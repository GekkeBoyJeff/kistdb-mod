package nl.libreria.nicknames;

import net.fabricmc.fabric.api.entity.FakePlayer;
import nl.libreria.core.SelfTests;

/** Zelftests voor nicknames. */
final class NicknamesTests {
	private NicknamesTests() {
	}

	static void register() {
		SelfTests.register("naam.nickname", level -> {
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(NicknamesMod.validate(p, "x") != null, "te kort");
			SelfTests.check(NicknamesMod.validate(p, "Dit is echt veel te lang") != null, "te lang");
			SelfTests.check(NicknamesMod.validate(p, "Kees§c") != null, "geen opmaakcodes");
			SelfTests.check(NicknamesMod.validate(p, "Jeff de Bouwer") == null, "gewone naam mag");
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			d.nick = "Jeff";
			d.color = "rainbow";
			SelfTests.check(NicknamesMod.styled(p) != null && NicknamesMod.styled(p).getString().equals("Jeff"), "nickname tekst");
			SelfTests.check(NicknamesMod.chatName(p) != null, "chatnaam");
			d.nick = "";
			SelfTests.check(NicknamesMod.styled(p) == null, "zonder nickname: gewone naam");
		});
	}
}
