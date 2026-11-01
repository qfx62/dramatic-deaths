package dev.dramaticdeaths;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared state for one death: the cinematic, then the "aftermath" screen with
 * the respawn button. Everything runs on the client thread.
 */
public final class DeathCinematic {
	/** Length of the cinematic part in real seconds. */
	public static final float DURATION = 7.0f;
	public static final Identifier LENS = Identifier.fromNamespaceAndPath("dramaticdeaths", "lens");

	public enum Phase { CINEMATIC, AFTERMATH }

	private record Cue(float time, SoundEvent sound, float pitch, float volume) {
	}

	private static boolean active;
	private static Phase phase = Phase.CINEMATIC;
	private static long startNanos;
	private static float aftermathStart;
	private static @Nullable LocalPlayer player;
	private static @Nullable CinematicScreen screen;
	private static DeathInfo info = new DeathInfo(DeathType.GENERIC, null, null, null, "");
	private static Lines.Picked lines = new Lines.Picked("", "");
	private static Vec3 deathEye = Vec3.ZERO;
	private static float startYaw;
	private static List<Cue> cues = List.of();
	private static int nextCue;
	private static boolean respawning;
	/** Killer position from the last frame it existed, so the shot holds if it vanishes. */
	private static @Nullable Vec3 lastKillerEye;
	private static @Nullable Vec3 lastKillerFeet;
	/** Field of view multiplier chosen by the camera this frame. */
	private static float fov = 1.0f;

	private DeathCinematic() {
	}

	public static CinematicScreen start(DeathScreen deathScreen, DeathInfo deathInfo, LocalPlayer deadPlayer) {
		active = true;
		phase = Phase.CINEMATIC;
		startNanos = System.nanoTime();
		player = deadPlayer;
		info = deathInfo;
		lines = Lines.pick(deathInfo);
		deathEye = deadPlayer.getEyePosition();
		startYaw = deadPlayer.getYRot();
		lastKillerEye = null;
		lastKillerFeet = null;
		fov = 1.0f;
		respawning = false;
		cues = cuesFor(deathInfo);
		nextCue = 0;
		if (deadPlayer.level() instanceof net.minecraft.client.multiplayer.ClientLevel level) {
			WorldFx.burst(level, deathInfo.type(), deadPlayer.position());
		}
		screen = new CinematicScreen(deathScreen);
		return screen;
	}

	/**
	 * Still the same death? Ends everything once the player respawned, left
	 * the world or the player object changed.
	 */
	public static boolean validate() {
		if (!active) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player != player || !player.isDeadOrDying()) {
			reset();
		}
		return active;
	}

	public static void reset() {
		active = false;
		screen = null;
		player = null;
		fov = 1.0f;
		respawning = false;
	}

	/** Skip or end of the cinematic: switch to the screen with the buttons. */
	public static void enterAftermath() {
		if (phase == Phase.AFTERMATH) {
			return;
		}
		phase = Phase.AFTERMATH;
		aftermathStart = seconds();
		nextCue = cues.size();
	}

	public static void respawn() {
		if (player != null && !respawning) {
			respawning = true;
			player.respawn();
		}
	}

	/** Called every frame: plays the sounds whose time has come. */
	public static void playDueSounds() {
		float t = seconds();
		while (nextCue < cues.size() && cues.get(nextCue).time() <= t) {
			Cue cue = cues.get(nextCue++);
			playSound(cue.sound(), cue.pitch(), cue.volume());
		}
	}

	public static boolean isActive() {
		return active;
	}

	public static Phase phase() {
		return phase;
	}

	public static @Nullable CinematicScreen screen() {
		return screen;
	}

	public static boolean isRespawning() {
		return respawning;
	}

	public static DeathInfo info() {
		return info;
	}

	public static Lines.Picked lines() {
		return lines;
	}

	public static Vec3 deathEye() {
		return deathEye;
	}

	public static float startYaw() {
		return startYaw;
	}

	/** Real seconds since death. */
	public static float seconds() {
		return (System.nanoTime() - startNanos) / 1.0e9f;
	}

	/** Real seconds since the buttons screen appeared. */
	public static float aftermathSeconds() {
		return phase == Phase.AFTERMATH ? seconds() - aftermathStart : 0.0f;
	}

	/** Seconds since death, mapped to 0..1 over the given window. */
	public static float progress(float from, float to) {
		return Mth.clamp((seconds() - from) / (to - from), 0.0f, 1.0f);
	}

	public static void rememberKiller(Vec3 eye, Vec3 feet) {
		lastKillerEye = eye;
		lastKillerFeet = feet;
	}

	public static @Nullable Vec3 lastKillerEye() {
		return lastKillerEye;
	}

	public static @Nullable Vec3 lastKillerFeet() {
		return lastKillerFeet;
	}

	public static void setFov(float value) {
		fov = value;
	}

	public static float fov() {
		return active ? fov : 1.0f;
	}

	/** The colour grading filter for this kind of death. */
	public static Identifier grade() {
		String name = switch (info.type()) {
			case MOB, GENERIC -> "red";
			case PLAYER -> "gold";
			case EXPLOSION, FIRE -> "warm";
			case FALL, CRUSH, LIGHTNING -> "bleach";
			case DROWN -> "blue";
			case FREEZE -> "ice";
			case VOID, MAGIC -> "purple";
			case STARVE -> "sepia";
		};
		return Identifier.fromNamespaceAndPath("dramaticdeaths", "grade_" + name);
	}

	/** Game speed multiplier: each death type has its own slow-motion curve. */
	public static float timeScale() {
		if (!active || phase == Phase.AFTERMATH) {
			return 1.0f;
		}
		float t = seconds();
		float slow = switch (info.type()) {
			// Near freeze-frame on the hit, then heavy slow motion.
			case MOB, PLAYER -> t < 0.5f ? 0.08f : 0.18f;
			case EXPLOSION -> Mth.lerp(CameraDirector.smooth((t - 0.6f) / 0.6f), 0.04f, 0.3f);
			case LIGHTNING -> t < 0.4f ? 0.06f : 0.3f;
			// Time itself freezes over.
			case FREEZE -> Mth.lerp(CameraDirector.smooth(t / 5.0f), 0.3f, 0.02f);
			case FALL, CRUSH -> 0.2f;
			case VOID, STARVE -> 0.6f;
			default -> 0.3f;
		};
		float in = CameraDirector.smooth(t / 0.25f);
		float out = CameraDirector.smooth((t - (DURATION - 1.0f)) / 1.0f);
		return Mth.lerp(out, Mth.lerp(in, 1.0f, slow), 1.0f);
	}

	private static List<Cue> cuesFor(DeathInfo info) {
		SoundEvent bell = SoundEvents.BELL_BLOCK;
		SoundEvent heartbeat = SoundEvents.WARDEN_HEARTBEAT;
		SoundEvent bass = SoundEvents.NOTE_BLOCK_BASS.value();
		return switch (info.type()) {
			case MOB -> List.of(
					new Cue(0.0f, info.killerType() == EntityTypes.WARDEN ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.PLAYER_ATTACK_CRIT, 0.5f, 1.0f),
					new Cue(0.05f, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.7f, 0.8f),
					new Cue(0.6f, bass, 0.5f, 1.0f),
					new Cue(2.6f, heartbeat, 0.6f, 1.0f),
					new Cue(5.0f, bell, 0.5f, 1.0f));
			case PLAYER -> List.of(
					new Cue(0.0f, SoundEvents.PLAYER_ATTACK_CRIT, 0.5f, 1.0f),
					new Cue(0.05f, SoundEvents.TRIDENT_THUNDER.value(), 1.5f, 0.6f),
					new Cue(0.6f, SoundEvents.RAID_HORN.value(), 1.0f, 0.6f),
					new Cue(5.0f, bell, 0.5f, 1.0f));
			case EXPLOSION -> List.of(
					new Cue(0.0f, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.0f),
					new Cue(0.3f, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.7f),
					new Cue(0.7f, bass, 0.5f, 1.0f),
					new Cue(0.9f, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.4f),
					new Cue(1.5f, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.2f),
					new Cue(5.0f, bell, 0.5f, 1.0f));
			case FALL -> List.of(
					new Cue(0.0f, SoundEvents.PLAYER_BIG_FALL, 0.6f, 1.0f),
					new Cue(0.05f, SoundEvents.ANVIL_LAND, 0.5f, 0.6f),
					new Cue(1.5f, bell, 0.5f, 1.0f),
					new Cue(3.0f, heartbeat, 0.5f, 1.0f));
			case DROWN -> List.of(
					new Cue(0.0f, SoundEvents.PLAYER_HURT_DROWN, 0.7f, 1.0f),
					new Cue(0.2f, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.8f, 1.0f),
					new Cue(1.0f, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8f, 1.0f),
					new Cue(1.5f, bell, 0.4f, 0.6f));
			case FIRE -> List.of(
					new Cue(0.0f, SoundEvents.PLAYER_HURT_ON_FIRE, 0.7f, 1.0f),
					new Cue(0.2f, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.0f),
					new Cue(0.8f, SoundEvents.LAVA_POP, 0.8f, 1.0f),
					new Cue(1.5f, SoundEvents.BLAZE_SHOOT, 0.5f, 0.8f),
					new Cue(1.5f, bell, 0.5f, 0.8f));
			case FREEZE -> List.of(
					new Cue(0.0f, SoundEvents.PLAYER_HURT_FREEZE, 0.6f, 1.0f),
					new Cue(1.5f, SoundEvents.GLASS_BREAK, 0.6f, 1.0f),
					new Cue(1.5f, bell, 0.7f, 0.5f));
			case VOID -> List.of(
					new Cue(0.0f, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.0f),
					new Cue(0.5f, SoundEvents.PORTAL_TRAVEL, 0.6f, 0.35f),
					new Cue(1.5f, bell, 0.4f, 0.8f));
			case LIGHTNING -> List.of(
					new Cue(0.0f, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f),
					new Cue(0.05f, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f),
					new Cue(1.5f, bell, 0.5f, 1.0f));
			case CRUSH -> List.of(
					new Cue(0.0f, SoundEvents.ANVIL_LAND, 0.5f, 1.0f),
					new Cue(0.1f, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.6f, 1.0f),
					new Cue(1.5f, bell, 0.5f, 1.0f));
			case MAGIC -> List.of(
					new Cue(0.0f, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.6f),
					new Cue(1.5f, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.0f),
					new Cue(1.5f, bell, 0.5f, 0.8f));
			case STARVE -> List.of(
					new Cue(0.0f, heartbeat, 0.5f, 1.0f),
					new Cue(1.2f, heartbeat, 0.45f, 1.0f),
					new Cue(1.5f, bell, 0.5f, 0.8f),
					new Cue(2.4f, heartbeat, 0.4f, 1.0f));
			case GENERIC -> List.of(
					new Cue(0.0f, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.0f),
					new Cue(0.5f, heartbeat, 0.7f, 1.0f),
					new Cue(1.5f, bell, 0.5f, 1.0f));
		};
	}

	public static void playSound(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}
}
