package dev.dramaticdeaths;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

/**
 * Picks the random title and tagline for a death. Each pool lives in the
 * language files as "dramaticdeaths.&lt;pool&gt;.&lt;index&gt;"; the same line never
 * comes twice in a row.
 */
public final class Lines {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<String, Integer> LAST_PICK = new HashMap<>();

	/** Title in capitals, and a smaller tagline below it. */
	public record Picked(String title, String tagline) {
	}

	private Lines() {
	}

	public static Picked pick(DeathInfo info) {
		String name = info.killerName() != null ? info.killerName().getString() : "";
		String typeName = info.type().name().toLowerCase(Locale.ROOT);

		String titlePool = switch (info.type()) {
			case EXPLOSION -> info.killerName() != null ? "title.explosion.by" : "title.explosion";
			default -> "title." + typeName;
		};
		String title = Component.translatable(key(titlePool), name).getString().toUpperCase(Locale.ROOT);

		// Mob-specific lines when we know the mob ("You looked at it." for endermen),
		// mixed with the general lines for that kind of death.
		String taglinePool = "line." + typeName;
		String mobPool = mobPool(info.killerType());
		if (mobPool != null && (info.type().isKillcam() || RANDOM.nextFloat() < 0.6f)) {
			taglinePool = mobPool;
		}
		String tagline = Component.translatable(key(taglinePool), name).getString();
		return new Picked(title, tagline);
	}

	private static String mobPool(EntityType<?> type) {
		if (type == null) {
			return null;
		}
		String pool = "line.mob." + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
		return LinePools.SIZES.containsKey(pool) ? pool : null;
	}

	private static String key(String pool) {
		int size = LinePools.SIZES.getOrDefault(pool, 1);
		int index = RANDOM.nextInt(size);
		Integer last = LAST_PICK.get(pool);
		if (size > 1 && last != null && index == last) {
			index = (index + 1 + RANDOM.nextInt(size - 1)) % size;
		}
		LAST_PICK.put(pool, index);
		return "dramaticdeaths." + pool + "." + index;
	}
}
