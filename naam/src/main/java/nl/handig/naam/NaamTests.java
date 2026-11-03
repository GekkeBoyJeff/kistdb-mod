package nl.handig.naam;

import net.fabricmc.fabric.api.entity.FakePlayer;
import nl.handig.kern.SelfTests;

/** Zelftests voor nicknames. */
final class NaamTests {
	private NaamTests() {
	}

	static void register() {
		SelfTests.register("naam.nickname", level -> {
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(Naam.validate(p, "x") != null, "te kort");
			SelfTests.check(Naam.validate(p, "Dit is echt veel te lang") != null, "te lang");
			SelfTests.check(Naam.validate(p, "Kees§c") != null, "geen opmaakcodes");
			SelfTests.check(Naam.validate(p, "Jeff de Bouwer") == null, "gewone naam mag");
			Naam.Data d = Naam.STORE.get(p);
			d.nick = "Jeff";
			d.color = "rainbow";
			SelfTests.check(Naam.styled(p) != null && Naam.styled(p).getString().equals("Jeff"), "nickname tekst");
			SelfTests.check(Naam.chatName(p) != null, "chatnaam");
			d.nick = "";
			SelfTests.check(Naam.styled(p) == null, "zonder nickname: gewone naam");
		});
	}
}
