package dev.dramaticdeaths;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Decides where the camera is at every moment of each death scene.
 * All positions are world coordinates; {@code t} is seconds since death.
 */
public final class CameraDirector {
	/** Vanilla's default field of view, used for the fall "vertigo" zoom. */
	private static final float BASE_FOV = 70.0f;

	/**
	 * @param position where the camera sits
	 * @param target   what it looks at
	 * @param anchor   a point that is surely visible; the camera never ends up behind a wall from here
	 * @param roll     camera tilt in degrees
	 * @param fov      field of view multiplier (below 1 zooms in)
	 */
	public record Shot(Vec3 position, Vec3 target, Vec3 anchor, float roll, float fov) {
	}

	private CameraDirector() {
	}

	/**
	 * @param body      the dead player's feet, interpolated
	 * @param deathEye  where the player's eyes were at the moment of death
	 * @param killerEye the killer's eyes, interpolated (null when there is no killer)
	 * @param killerSize the killer's bounding box width, to frame big mobs further away
	 */
	public static Shot shoot(DeathType type, float t, float startYaw, Vec3 body, Vec3 deathEye,
			@Nullable Vec3 killerEye, @Nullable Vec3 killerFeet, float killerSize) {
		Vec3 chest = body.add(0.0, 0.5, 0.0);
		Shot shot = switch (type) {
			case MOB, PLAYER -> killcam(t, startYaw, chest, deathEye, killerEye, killerFeet, killerSize);
			case EXPLOSION -> explosion(t, startYaw, chest, deathEye);
			case FALL -> fall(t, chest);
			case DROWN -> new Shot(orbit(chest, startYaw + 180.0f + t * 12.0f, 2.6f, Mth.lerp(smooth(t / 6.5f), 0.4f, 6.0f)),
					chest, chest, 4.0f * Mth.sin(t * 1.3f), 1.1f);
			case FIRE -> new Shot(orbit(chest, startYaw + 180.0f + t * 22.0f, Mth.lerp(smooth(t / 6.0f), 3.0f, 5.0f), Mth.lerp(smooth(t / 6.0f), 0.3f, 2.4f)),
					chest, chest, 2.5f * Mth.sin(t * 7.0f), 1.0f + 0.03f * Mth.sin(t * 5.0f));
			case FREEZE -> new Shot(orbit(chest, startYaw + 180.0f + 40.0f * smooth(t / 7.0f), 2.0f + t * 0.4f, 0.8f + t * 0.35f),
					chest, chest, 0.0f, 1.0f);
			case VOID -> {
				Vec3 above = deathEye.add(0.0, 1.2, 0.0).add(forward(startYaw).scale(-0.6));
				yield new Shot(above, chest, above, 0.0f, Mth.lerp(smooth(t / 6.0f), 1.0f, 0.55f));
			}
			case LIGHTNING -> new Shot(orbit(chest, startYaw + 180.0f + t * 12.0f, 3.0f, Mth.lerp(easeOut(t / 2.0f), 9.0f, 4.0f)),
					chest, chest, 0.0f, 1.0f);
			case CRUSH -> new Shot(orbit(chest, startYaw + 90.0f + t * 8.0f, 2.6f, Mth.lerp(smooth((t - 3.5f) / 3.5f), -0.3f, 4.0f)),
					chest, chest, 0.0f, 1.0f);
			case MAGIC -> new Shot(orbit(chest, startYaw + t * 40.0f, 3.0f + 0.5f * Mth.sin(t), 1.2f + t * 0.45f),
					chest, chest, 10.0f * Mth.sin(t * 1.5f), 1.0f + 0.08f * Mth.sin(t * 2.0f));
			case STARVE -> new Shot(body.add(forward(startYaw).scale(-1.5)).add(0.0, Mth.lerp(smooth(t / 7.0f), 1.0f, 9.0f), 0.0),
					chest, chest, 0.0f, 1.0f);
			case GENERIC -> generic(t, startYaw, chest);
		};
		// Impact shake, plus a slow "handheld camera" drift so it never looks robotic.
		Vec3 shake = shake(t, shakeAmount(type, t));
		Vec3 handheld = new Vec3(Mth.sin(t * 0.7f) * 0.05, Mth.sin(t * 0.9f + 1.0f) * 0.035, Mth.sin(t * 0.6f + 2.0f) * 0.05);
		float roll = shot.roll() + Mth.sin(t * 0.5f + 0.5f) * 0.8f;
		// A quick zoom punch on the hit for everything that isn't already a killcam zoom.
		float punch = type.isKillcam() ? 1.0f : 1.0f - 0.14f * (float) Math.exp(-t * 5.0f) * Mth.clamp(t * 25.0f, 0.0f, 1.0f);
		return new Shot(shot.position().add(shake).add(handheld), shot.target(), shot.anchor(), roll, shot.fov() * punch);
	}

	/** Over-the-shoulder whip pan to the killer, a low "villain" orbit, then a crane shot over both. */
	private static Shot killcam(float t, float startYaw, Vec3 chest, Vec3 deathEye,
			@Nullable Vec3 killerEye, @Nullable Vec3 killerFeet, float killerSize) {
		if (killerEye == null || killerFeet == null) {
			return generic(t, startYaw, chest);
		}
		// 1) Behind your own shoulder, snapping round to face the killer and zooming in.
		Vec3 toKiller = killerEye.subtract(deathEye).normalize();
		Vec3 right = new Vec3(-toKiller.z, 0.0, toKiller.x).normalize();
		Vec3 shoulder = deathEye.subtract(toKiller.scale(0.9)).add(0.0, 0.25, 0.0).add(right.scale(0.5));
		Vec3 lookedAt = deathEye.add(forward(startYaw).scale(5.0));
		Vec3 whipTarget = lerp(easeOut(t / 0.35f), lookedAt, killerEye);
		float zoom = Mth.lerp(easeOut((t - 0.15f) / 0.6f), 1.0f, 0.45f);
		Shot shoulderShot = new Shot(shoulder, whipTarget, deathEye, 0.0f, zoom);
		if (t < 2.6f) {
			return shoulderShot;
		}

		// 2) Low angle circling the killer, looking up at it.
		float distance = Math.max(2.4f, killerSize * 2.6f);
		float height = (float) (killerEye.y - killerFeet.y) * 0.25f;
		float baseAngle = yawTowards(killerFeet, deathEye);
		Vec3 villain = orbit(killerFeet, baseAngle + (t - 2.6f) * 35.0f, distance, height);
		float blend = smooth((t - 2.6f) / 0.5f);
		Shot villainShot = new Shot(lerp(blend, shoulder, villain), lerp(blend, whipTarget, killerEye), killerEye,
				0.0f, Mth.lerp(blend, zoom, 0.85f));
		if (t < 5.0f) {
			return villainShot;
		}

		// 3) Crane up high to see both of you.
		Vec3 middle = lerp(0.5f, chest, killerFeet);
		Vec3 crane = middle.add(forward(baseAngle).scale(-3.0)).add(0.0, 7.0 + killerSize, 0.0);
		float rise = smooth((t - 5.0f) / 2.0f);
		return new Shot(lerp(rise, villainShot.position(), crane), lerp(rise, villainShot.target(), middle), middle,
				0.0f, Mth.lerp(rise, villainShot.fov(), 1.0f));
	}

	/** Blasted backwards and upwards, tumbling, then slowly settling into an orbit. */
	private static Shot explosion(float t, float startYaw, Vec3 chest, Vec3 deathEye) {
		Vec3 back = forward(startYaw).scale(-6.0).add(0.0, 4.0, 0.0);
		Vec3 blasted = deathEye.add(back.scale(easeOutExpo(t / 1.4f)));
		Vec3 drift = orbit(chest, startYaw + 180.0f + (t - 1.4f) * 10.0f, 6.0f, Mth.lerp(smooth((t - 1.4f) / 5.6f), 4.0f, 7.0f));
		Vec3 position = lerp(smooth((t - 1.4f) / 1.0f), blasted, drift);
		float roll = 35.0f * Mth.sin(t * 4.0f) * (float) Math.exp(-t * 1.2f);
		return new Shot(position, chest, chest, roll, Mth.lerp(smooth(t / 2.0f), 1.15f, 1.0f));
	}

	/** Straight down at the body while rising, with a "vertigo" zoom that keeps the body the same size. */
	private static Shot fall(float t, Vec3 chest) {
		float startHeight = 1.6f;
		float height = Mth.lerp(smooth(t / 5.5f), startHeight, 14.0f);
		float halfAngle = BASE_FOV * 0.5f * Mth.DEG_TO_RAD;
		float fovDegrees = 2.0f * (float) Math.atan(startHeight * Math.tan(halfAngle) / height) * Mth.RAD_TO_DEG;
		Vec3 above = chest.add(0.02, height, 0.0);
		return new Shot(above, chest, chest, t * 8.0f, Math.max(0.25f, fovDegrees / BASE_FOV));
	}

	/** The original scene: start at the face, rise and drift back while circling. */
	private static Shot generic(float t, float startYaw, Vec3 chest) {
		float move = smooth(t / 4.0f);
		float late = smooth((t - 4.0f) / 3.0f);
		float distance = Mth.lerp(move, 1.5f, 5.5f) + late * 1.5f;
		float height = Mth.lerp(move, 0.6f, 3.2f) + late * 2.5f;
		return new Shot(orbit(chest, startYaw + 180.0f + t * 18.0f, distance, height), chest, chest, 0.0f, 1.0f);
	}

	private static float shakeAmount(DeathType type, float t) {
		return switch (type) {
			case MOB, PLAYER -> 0.10f * decay(t, 3.0f);
			case EXPLOSION -> 0.60f * decay(t, 2.5f);
			case FALL -> 0.35f * decay(t, 4.0f);
			case LIGHTNING -> 0.50f * decay(t, 3.0f);
			case CRUSH -> 0.45f * decay(t, 3.5f);
			default -> 0.0f;
		};
	}

	private static Vec3 shake(float t, float amount) {
		if (amount <= 0.001f) {
			return Vec3.ZERO;
		}
		return new Vec3(
				(Mth.sin(t * 41.3f) * 0.6f + Mth.sin(t * 17.9f) * 0.4f) * amount,
				(Mth.sin(t * 37.7f) * 0.6f + Mth.sin(t * 23.1f) * 0.4f) * amount,
				(Mth.sin(t * 29.3f) * 0.6f + Mth.sin(t * 13.7f) * 0.4f) * amount);
	}

	public static Vec3 orbit(Vec3 center, float angleDegrees, float distance, float height) {
		float angle = angleDegrees * Mth.DEG_TO_RAD;
		return center.add(-Mth.sin(angle) * distance, height, Mth.cos(angle) * distance);
	}

	/** Unit vector a player with this yaw looks along (ignoring pitch). */
	private static Vec3 forward(float yawDegrees) {
		float yaw = yawDegrees * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** The orbit angle that puts the camera on the {@code from} side of {@code center}. */
	private static float yawTowards(Vec3 center, Vec3 from) {
		Vec3 d = from.subtract(center);
		return (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
	}

	private static Vec3 lerp(float t, Vec3 a, Vec3 b) {
		return a.add(b.subtract(a).scale(t));
	}

	private static float decay(float t, float speed) {
		return (float) Math.exp(-t * speed);
	}

	public static float smooth(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	public static float easeOut(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		float inv = 1.0f - t;
		return 1.0f - inv * inv * inv;
	}

	private static float easeOutExpo(float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
	}
}
