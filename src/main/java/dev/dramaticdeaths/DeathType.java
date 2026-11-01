package dev.dramaticdeaths;

/**
 * What killed the player, worked out from the death message's translation key
 * (for example "death.attack.drown" or "death.fell.accident.ladder").
 */
public enum DeathType {
	/** Killed by a mob, in melee or with a projectile. */
	MOB,
	/** Killed by another player. */
	PLAYER,
	EXPLOSION,
	FALL,
	DROWN,
	FIRE,
	FREEZE,
	VOID,
	LIGHTNING,
	CRUSH,
	MAGIC,
	STARVE,
	GENERIC;

	public static DeathType fromKey(String key) {
		if (key.startsWith("death.fell.")) {
			return FALL;
		}
		if (!key.startsWith("death.attack.")) {
			return GENERIC;
		}
		return switch (causeOf(key)) {
			case "player" -> PLAYER;
			case "mob", "arrow", "trident", "thrown", "sting", "sonic", "spear", "mace", "thorns", "fireball", "witherSkull" -> MOB;
			case "explosion", "fireworks", "badRespawnPoint" -> EXPLOSION;
			case "fall", "flyIntoWall" -> FALL;
			case "drown", "dryout" -> DROWN;
			case "lava", "inFire", "onFire", "hotFloor", "sulfurCubeHot" -> FIRE;
			case "freeze" -> FREEZE;
			case "outOfWorld", "outsideBorder" -> VOID;
			case "lightningBolt" -> LIGHTNING;
			case "anvil", "fallingBlock", "fallingStalactite", "stalagmite", "inWall", "cramming" -> CRUSH;
			case "magic", "indirectMagic", "wither", "dragonBreath" -> MAGIC;
			case "starve" -> STARVE;
			default -> GENERIC;
		};
	}

	/** "death.attack.drown.player" -> "drown"; empty for keys that aren't attacks. */
	public static String causeOf(String key) {
		if (!key.startsWith("death.attack.")) {
			return "";
		}
		String cause = key.substring("death.attack.".length());
		int dot = cause.indexOf('.');
		// Drop ".player" / ".item" suffixes: "drown.player" is still drowning.
		return dot >= 0 ? cause.substring(0, dot) : cause;
	}

	/** Scenes built around looking at whoever killed you. */
	public boolean isKillcam() {
		return this == MOB || this == PLAYER;
	}
}
