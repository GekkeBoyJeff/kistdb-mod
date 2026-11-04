package nl.libreria.core.dialog;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import nl.libreria.core.Ui;
import org.jetbrains.annotations.Nullable;

/**
 * A real window (a Minecraft dialog, 1.21.6+): text, checkboxes, option buttons, sliders, text fields and buttons, all
 * drawn by the plain client. Built in code, shown to one player; the buttons call back into Java with what the player
 * filled in:
 *
 * <pre>{@code
 * Window.of("Effects")
 *     .text("Choose what you see.")
 *     .toggle("first", "First person: your weapon", true)
 *     .button("Save", "Keep these settings", (p, v) -> save(p, v.bool("first", true)))
 *     .back(p -> SettingsWindow.open(p))
 *     .show(player);
 * }</pre>
 *
 * Layout is always text first, then the inputs, then the buttons (two per row), then the bottom button.
 */
public final class Window {
	static final String KEY_WINDOW = "libreria_window", KEY_BUTTON = "libreria_button";
	/** Button and input widths (the client caps a dialog at about 320 px wide). */
	public static final int WIDE = 310, BUTTON = 150, FIELD = 250;

	private static int nextId;

	final int id = ++nextId;
	private final Component title;
	private @Nullable Component externalTitle;
	private final List<DialogBody> body = new ArrayList<>();
	private final List<Input> inputs = new ArrayList<>();
	private final List<ActionButton> buttons = new ArrayList<>();
	private final List<BiConsumer<ServerPlayer, Values>> callbacks = new ArrayList<>();
	private @Nullable ActionButton bottom;
	private int columns = 2;

	private Window(Component title) {
		this.title = title;
	}

	public static Window of(String title) {
		return new Window(Ui.text(title, ChatFormatting.WHITE).withStyle(ChatFormatting.BOLD));
	}

	public static Window of(Component title) {
		return new Window(title);
	}

	/** The name on the button that opens this window from the pause menu (only for data-pack dialogs). */
	public Window externalTitle(Component t) {
		this.externalTitle = t;
		return this;
	}

	public Window columns(int columns) {
		this.columns = Math.max(1, columns);
		return this;
	}

	// ---------------------------------------------------------------- body

	public Window text(String text) {
		return text(Ui.text(text, ChatFormatting.GRAY));
	}

	public Window text(Component text) {
		return text(text, WIDE);
	}

	public Window text(Component text, int width) {
		this.body.add(new PlainMessage(text, width));
		return this;
	}

	/** An item picture with text next to it (the tooltip shows when you hover the item). */
	public Window item(ItemStack stack, Component description) {
		if (stack.isEmpty()) return text(description);
		this.body.add(new ItemBody(ItemStackTemplate.fromNonEmptyStack(stack), Optional.of(new PlainMessage(description, WIDE - 30)),
				true, true, 16, 16));
		return this;
	}

	// ---------------------------------------------------------------- inputs

	/** A checkbox. Read it with {@link Values#bool}. */
	public Window toggle(String key, Component label, boolean initial) {
		this.inputs.add(new Input(key, new BooleanInput(label, initial, "true", "false")));
		return this;
	}

	public Window toggle(String key, String label, boolean initial) {
		return toggle(key, Ui.text(label, ChatFormatting.WHITE), initial);
	}

	/** One choice from a few options, shown as a button that cycles "Label: option". Read it with {@link Values#text}. */
	public Window choice(String key, Component label, List<String> ids, List<Component> names, String initial) {
		List<SingleOptionInput.Entry> entries = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			entries.add(new SingleOptionInput.Entry(ids.get(i), Optional.of(names.get(i)), ids.get(i).equals(initial)));
		}
		this.inputs.add(new Input(key, new SingleOptionInput(FIELD, entries, label, true)));
		return this;
	}

	/** A slider from {@code min} to {@code max} in steps. Shows "Label: value". Read it with {@link Values#number}. */
	public Window slider(String key, Component label, float min, float max, float step, float initial) {
		float init = Math.max(min, Math.min(max, initial));
		this.inputs.add(new Input(key, new NumberRangeInput(FIELD, label, "options.generic_value",
				new NumberRangeInput.RangeInfo(min, max, Optional.of(init), Optional.of(step)))));
		return this;
	}

	/** A text field. Read it with {@link Values#text}. */
	public Window field(String key, Component label, String initial, int maxLength) {
		this.inputs.add(new Input(key, new TextInput(FIELD, label, true, initial == null ? "" : initial, Math.max(1, maxLength),
				Optional.empty())));
		return this;
	}

	// ---------------------------------------------------------------- buttons

	/** A button that calls back with everything filled in. The window closes when it's pressed. */
	public Window button(Component label, @Nullable Component tooltip, BiConsumer<ServerPlayer, Values> onClick) {
		this.buttons.add(new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), BUTTON), Optional.of(callback(onClick))));
		return this;
	}

	public Window button(String label, @Nullable String tooltip, Consumer<ServerPlayer> onClick) {
		return button(Ui.text(label, ChatFormatting.WHITE), tooltip == null ? null : Ui.text(tooltip, ChatFormatting.GRAY),
				(p, v) -> onClick.accept(p));
	}

	/** A button that opens a web page (the client asks first, as with every link). */
	public Window link(Component label, @Nullable Component tooltip, String url) {
		Optional<Action> action;
		try {
			action = Optional.of(new StaticAction(new ClickEvent.OpenUrl(URI.create(url))));
		} catch (IllegalArgumentException e) {
			action = Optional.empty();
		}
		this.buttons.add(new ActionButton(new CommonButtonData(label, Optional.ofNullable(tooltip), BUTTON), action));
		return this;
	}

	/** The wide button at the bottom ("Back", "Done"); Escape does the same. */
	public Window bottom(Component label, BiConsumer<ServerPlayer, Values> onClick) {
		this.bottom = new ActionButton(new CommonButtonData(label, Optional.empty(), 200), Optional.of(callback(onClick)));
		return this;
	}

	/** "◀ Back" at the bottom, to the window you came from. */
	public Window back(Consumer<ServerPlayer> to) {
		return bottom(Ui.text("◀ Back", ChatFormatting.WHITE), (p, v) -> to.accept(p));
	}

	/** "Done" at the bottom: just closes. */
	public Window done() {
		this.bottom = new ActionButton(new CommonButtonData(Ui.text("Done", ChatFormatting.WHITE), Optional.empty(), 200), Optional.empty());
		return this;
	}

	private Action callback(BiConsumer<ServerPlayer, Values> onClick) {
		CompoundTag tag = new CompoundTag();
		tag.putInt(KEY_WINDOW, this.id);
		tag.putInt(KEY_BUTTON, this.callbacks.size());
		this.callbacks.add(onClick);
		return new CustomAll(Dialogs.WINDOW, Optional.of(tag));
	}

	void click(ServerPlayer player, int button, Values values) {
		if (button < 0 || button >= this.callbacks.size()) return;
		this.callbacks.get(button).accept(player, values);
	}

	// ---------------------------------------------------------------- showing

	public Dialog build() {
		CommonDialogData common = new CommonDialogData(this.title, Optional.ofNullable(this.externalTitle), true, false,
				DialogAction.CLOSE, List.copyOf(this.body), List.copyOf(this.inputs));
		if (this.buttons.isEmpty()) {
			ActionButton ok = this.bottom != null ? this.bottom
					: new ActionButton(new CommonButtonData(Ui.text("Done", ChatFormatting.WHITE), Optional.empty(), 200), Optional.empty());
			return new NoticeDialog(common, ok);
		}
		return new MultiActionDialog(common, List.copyOf(this.buttons), Optional.ofNullable(this.bottom), this.columns);
	}

	public void show(ServerPlayer player) {
		Dialogs.opened(player, this);
		player.connection.send(new ClientboundShowDialogPacket(Holder.direct(build())));
	}
}
