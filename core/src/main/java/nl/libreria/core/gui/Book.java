package nl.libreria.core.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import nl.libreria.core.ItemView;
import nl.libreria.core.dialog.Dialogs;

/**
 * A book to read, in the game's own book screen: pages of at most {@link #LINES} lines of at most {@link #WIDTH} pixels,
 * laid out here (so nothing falls off a page). Opened without giving the player a book: their client holds it for a
 * moment, just long enough to open it. Links go to another page of the same book ({@link #toPage}) or to the server
 * ({@link #action}, a {@code libreria:<name>} click handled through {@link Dialogs#on}).
 */
public final class Book {
	/** What fits on a page of the book screen. */
	public static final int WIDTH = 113, LINES = 14, MAX_PAGES = 100;
	/** Colours that read well on the paper. */
	public static final int INK = 0x1E1A16, TITLE = 0x4A2E0C, ACCENT = 0x8A5A00, LINK = 0x5E1F78, BUTTON = 0x24508F, MUTED = 0x6A6560;

	private final List<List<Component>> pages = new ArrayList<>();
	private List<Component> page = new ArrayList<>();

	public static Book create() {
		return new Book();
	}

	/** The page that is being written now (1 = the first). */
	public int pageNumber() {
		return this.pages.size() + 1;
	}

	/** Lines left on this page. */
	public int room() {
		return LINES - this.page.size();
	}

	public Book line(Component line) {
		if (this.page.size() >= LINES) newPage();
		this.page.add(line);
		return this;
	}

	/** An empty line, but not at the top of a page. */
	public Book gap() {
		if (!this.page.isEmpty() && this.page.size() < LINES) this.page.add(Component.empty());
		return this;
	}

	public Book newPage() {
		if (!this.page.isEmpty()) {
			this.pages.add(this.page);
			this.page = new ArrayList<>();
		}
		return this;
	}

	/** Starts on a fresh page unless this one has room for {@code lines} more. */
	public Book keep(int lines) {
		if (room() < lines) newPage();
		return this;
	}

	/** Text broken into lines that fit, each in this style. */
	public Book text(String text, Style style) {
		for (String l : wrap(text, style)) line(Component.literal(l).withStyle(style));
		return this;
	}

	/** Text in the middle of the page (lines that fit, each centred). */
	public Book centred(String text, Style style) {
		for (String l : wrap(text, style)) line(centre(Component.literal(l).withStyle(style)));
		return this;
	}

	/** Moves a line to the middle with spaces (a space is 4 pixels). */
	public static MutableComponent centre(Component line) {
		int pad = Math.max(0, (WIDTH - Glyphs.width(line)) / 2 / 4);
		return Component.empty().append(" ".repeat(pad)).append(line);
	}

	/** Words into lines no wider than the page. */
	public static List<String> wrap(String text, Style style) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			String tryLine = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Glyphs.width(Component.literal(tryLine).withStyle(style)) > WIDTH) {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(tryLine);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	public static Style style(int rgb) {
		return Style.EMPTY.withColor(rgb);
	}

	/** A link to a page of this book (1 = the first). */
	public static Style toPage(Style style, int page) {
		return style.withClickEvent(new ClickEvent.ChangePage(page));
	}

	/** A link that asks the server for something: {@code libreria:<name>}, with these values. */
	public static Style action(Style style, String name, CompoundTag values) {
		return style.withClickEvent(new ClickEvent.Custom(Identifier.fromNamespaceAndPath("libreria", name), Optional.<net.minecraft.nbt.Tag>of(values)));
	}

	/** Every page as its lines (for the self-tests). */
	public List<List<Component>> layout() {
		List<List<Component>> all = new ArrayList<>(this.pages);
		if (!this.page.isEmpty()) all.add(this.page);
		return all;
	}

	public List<Component> pages() {
		List<List<Component>> all = new ArrayList<>(this.pages);
		if (!this.page.isEmpty() || all.isEmpty()) all.add(this.page);
		List<Component> out = new ArrayList<>();
		for (List<Component> lines : all.subList(0, Math.min(all.size(), MAX_PAGES))) {
			MutableComponent p = Component.empty();
			for (int i = 0; i < lines.size(); i++) {
				if (i > 0) p.append("\n");
				p.append(lines.get(i));
			}
			out.add(p);
		}
		return out;
	}

	/** Opens it for this player (whatever window they had open closes). */
	public void open(ServerPlayer p, String title) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> content = new ArrayList<>();
		for (Component c : pages()) content.add(Filterable.passThrough(c));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title.length() > 32 ? title.substring(0, 32) : title),
				"Libreria", 0, content, true));
		Dialogs.forget(p.getUUID());
		if (p.containerMenu != p.inventoryMenu) p.closeContainer();
		var menu = p.inventoryMenu;
		int slot = 36 + p.getInventory().getSelectedSlot();
		// The client holds the book for a moment (it opens what's in its hand), then its own item again
		p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot, book));
		p.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
		ItemStack real = menu.getSlot(slot).getItem();
		p.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), slot,
				ItemView.forViewer(p, menu, slot, real).copy()));
	}
}
