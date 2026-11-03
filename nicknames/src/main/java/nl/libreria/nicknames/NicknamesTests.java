package nl.libreria.nicknames;

import net.fabricmc.fabric.api.entity.FakePlayer;
import nl.libreria.core.SelfTests;

/** Zelftests voor nicknames. */
final class NicknamesTests {
	private NicknamesTests() {
	}

	static void register() {
		SelfTests.register("nicknames.nickname", level -> {
			FakePlayer p = FakePlayer.get(level);
			SelfTests.check(NicknamesMod.validate(p, "x") != null, "too short");
			SelfTests.check(NicknamesMod.validate(p, "This is way too long") != null, "too long");
			SelfTests.check(NicknamesMod.validate(p, "Steve§c") != null, "no formatting codes");
			SelfTests.check(NicknamesMod.validate(p, "Jeff the Miner") == null, "normal name allowed");
			SelfTests.check("At least 2 characters.".equals(NicknamesMod.validate(p, "x")), "English reason: " + NicknamesMod.validate(p, "x"));
			NicknamesMod.Data d = NicknamesMod.STORE.get(p);
			d.nick = "Jeff";
			d.color = "rainbow";
			SelfTests.check(NicknamesMod.styled(p) != null && NicknamesMod.styled(p).getString().equals("Jeff"), "nickname text");
			SelfTests.check(NicknamesMod.chatName(p) != null, "chat name");
			d.nick = "";
			SelfTests.check(NicknamesMod.styled(p) == null, "without nickname: regular name");
		});
	}
}
