package dev.dramaticdeaths;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Real particles in the world around the body. They are client-side only and
 * tick with the slowed-down game, so they drift in slow motion too.
 */
public final class WorldFx {
	private static final RandomSource RANDOM = RandomSource.create();

	private WorldFx() {
	}

	/** One big burst at the moment of death. */
	public static void burst(ClientLevel level, DeathType type, Vec3 body) {
		Vec3 c = body.add(0.0, 0.6, 0.0);
		switch (type) {
			case MOB -> {
				spray(level, ParticleTypes.DAMAGE_INDICATOR, c, 25, 0.35, 0.3);
				spray(level, ParticleTypes.CRIT, c, 30, 0.6, 0.2);
			}
			case PLAYER -> {
				spray(level, ParticleTypes.CRIT, c, 35, 0.6, 0.2);
				spray(level, ParticleTypes.ENCHANTED_HIT, c, 25, 0.5, 0.2);
				spray(level, ParticleTypes.ELECTRIC_SPARK, c, 15, 0.4, 0.2);
			}
			case EXPLOSION -> {
				level.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0.0, 0.0, 0.0);
				spray(level, ParticleTypes.LARGE_SMOKE, c, 40, 0.35, 0.1);
				spray(level, ParticleTypes.LAVA, c, 12, 0.0, 0.0);
			}
			case FALL -> {
				ring(level, ParticleTypes.POOF, body.add(0.0, 0.1, 0.0), 36, 0.25);
				ring(level, ParticleTypes.CLOUD, body.add(0.0, 0.1, 0.0), 20, 0.12);
				spray(level, ParticleTypes.DUST_PLUME, c, 12, 0.15, 0.1);
			}
			case DROWN -> spray(level, ParticleTypes.BUBBLE, c, 50, 0.15, 0.25);
			case FIRE -> {
				spray(level, ParticleTypes.FLAME, c, 40, 0.15, 0.15);
				spray(level, ParticleTypes.LAVA, c, 10, 0.0, 0.0);
				spray(level, ParticleTypes.LARGE_SMOKE, c, 15, 0.1, 0.1);
			}
			case FREEZE -> {
				spray(level, ParticleTypes.SNOWFLAKE, c, 50, 0.25, 0.05);
				spray(level, ParticleTypes.WHITE_ASH, c, 30, 0.1, 0.0);
			}
			case VOID -> {
				spray(level, ParticleTypes.REVERSE_PORTAL, c, 60, 0.3, 0.2);
				spray(level, ParticleTypes.PORTAL, c, 40, 1.0, 0.5);
			}
			case LIGHTNING -> {
				spray(level, ParticleTypes.ELECTRIC_SPARK, c, 60, 0.8, 0.5);
				spray(level, ParticleTypes.LARGE_SMOKE, c, 20, 0.1, 0.1);
			}
			case CRUSH -> {
				ring(level, ParticleTypes.POOF, body.add(0.0, 0.1, 0.0), 30, 0.2);
				spray(level, ParticleTypes.CLOUD, c, 15, 0.1, 0.05);
			}
			case MAGIC -> {
				spray(level, ParticleTypes.WITCH, c, 40, 0.3, 0.3);
				spray(level, ParticleTypes.ENCHANT, c, 40, 1.0, 0.5);
			}
			case STARVE -> spray(level, ParticleTypes.SOUL, c, 8, 0.05, 0.05);
			case GENERIC -> {
				spray(level, ParticleTypes.SOUL, c, 12, 0.05, 0.08);
				spray(level, ParticleTypes.POOF, c, 12, 0.1, 0.05);
			}
		}
	}

	/** Called every (slowed) client tick while the scene plays. */
	public static void ambient(ClientLevel level, DeathType type, Vec3 body, float t, float strength) {
		Vec3 c = body.add(0.0, 0.5, 0.0);
		switch (type) {
			case MOB, PLAYER -> chance(level, ParticleTypes.SOUL, around(c, 0.3), 0.0, 0.04, 0.0, 0.25 * strength);
			case EXPLOSION -> {
				chance(level, ParticleTypes.LARGE_SMOKE, around(c, 1.5), 0.0, 0.03, 0.0, 0.8 * strength);
				repeat(3, strength, () -> level.addParticle(ParticleTypes.ASH, around(c, 4.0).x, c.y + 3.0, around(c, 4.0).z, 0.0, -0.02, 0.0));
			}
			case FALL -> repeat(2, strength, () -> {
				Vec3 p = around(c, 2.5);
				level.addParticle(ParticleTypes.WHITE_ASH, p.x, p.y + 1.5, p.z, 0.0, -0.01, 0.0);
			});
			case DROWN -> repeat(3, strength, () -> {
				Vec3 p = around(c, 0.8);
				level.addParticle(ParticleTypes.BUBBLE, p.x, p.y, p.z, 0.0, 0.12, 0.0);
			});
			case FIRE -> {
				repeat(2, strength, () -> {
					Vec3 p = around(c, 0.6);
					level.addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, 0.0, 0.04, 0.0);
				});
				chance(level, ParticleTypes.LARGE_SMOKE, around(c, 0.5), 0.0, 0.06, 0.0, 0.6 * strength);
				chance(level, ParticleTypes.LAVA, c, 0.0, 0.0, 0.0, 0.12 * strength);
			}
			case FREEZE -> repeat(5, strength, () -> {
				Vec3 p = around(c, 3.5);
				level.addParticle(ParticleTypes.SNOWFLAKE, p.x, p.y + 2.5, p.z, 0.02, -0.05, 0.01);
			});
			case VOID -> {
				repeat(3, strength, () -> {
					Vec3 p = around(c, 1.5);
					level.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 0.0, 0.05, 0.0);
				});
				chance(level, ParticleTypes.SCULK_SOUL, around(c, 1.0), 0.0, 0.03, 0.0, 0.3 * strength);
			}
			case LIGHTNING -> chance(level, ParticleTypes.ELECTRIC_SPARK, around(c, 0.8), 0.0, 0.05, 0.0, 0.7 * strength);
			case CRUSH -> chance(level, ParticleTypes.POOF, around(c, 0.8), 0.0, 0.01, 0.0, 0.3 * strength);
			case MAGIC -> {
				// A slow spiral around the body.
				float angle = t * 3.0f;
				for (int i = 0; i < 3; i++) {
					float a = angle + i * Mth.TWO_PI / 3.0f;
					level.addParticle(ParticleTypes.WITCH, c.x + Mth.cos(a) * 1.2, c.y + (t % 2.0f) * 0.6, c.z + Mth.sin(a) * 1.2, 0.0, 0.02, 0.0);
				}
				chance(level, ParticleTypes.ENCHANT, around(c, 1.5), 0.0, 0.1, 0.0, 0.8 * strength);
			}
			case STARVE, GENERIC -> chance(level, ParticleTypes.SOUL, around(c, 0.3), 0.0, 0.04, 0.0, 0.3 * strength);
		}
	}

	private static void spray(ClientLevel level, ParticleOptions particle, Vec3 c, int count, double speed, double up) {
		for (int i = 0; i < count; i++) {
			double vx = (RANDOM.nextDouble() - 0.5) * 2.0 * speed;
			double vy = RANDOM.nextDouble() * up * 2.0;
			double vz = (RANDOM.nextDouble() - 0.5) * 2.0 * speed;
			level.addParticle(particle, c.x, c.y, c.z, vx, vy, vz);
		}
	}

	/** A flat shockwave ring along the ground. */
	private static void ring(ClientLevel level, ParticleOptions particle, Vec3 c, int count, double speed) {
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2.0 / count;
			level.addParticle(particle, c.x, c.y, c.z, Math.cos(a) * speed, 0.01, Math.sin(a) * speed);
		}
	}

	private static void chance(ClientLevel level, ParticleOptions particle, Vec3 p, double vx, double vy, double vz, double chance) {
		if (RANDOM.nextDouble() < chance) {
			level.addParticle(particle, p.x, p.y, p.z, vx, vy, vz);
		}
	}

	private static void repeat(int times, float strength, Runnable spawn) {
		int n = Math.round(times * strength);
		for (int i = 0; i < n; i++) {
			spawn.run();
		}
	}

	private static Vec3 around(Vec3 c, double radius) {
		return c.add((RANDOM.nextDouble() - 0.5) * 2.0 * radius, (RANDOM.nextDouble() - 0.5) * radius, (RANDOM.nextDouble() - 0.5) * 2.0 * radius);
	}
}
