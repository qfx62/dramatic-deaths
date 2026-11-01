package dev.dramaticdeaths;

import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Everything the client can find out about a death from the death message.
 * The killer's name in the message carries a hover tooltip with its UUID and
 * type, which lets us find the actual entity in the world.
 */
public record DeathInfo(
		DeathType type,
		@Nullable Entity killer,
		@Nullable Component killerName,
		@Nullable EntityType<?> killerType,
		/** The death message key without "death.attack.", e.g. "arrow" or "anvil". */
		String cause
) {
	private static final double SEARCH_RADIUS = 48.0;

	public static DeathInfo from(@Nullable Component message, Player player, @Nullable ClientLevel level) {
		if (message == null || !(message.getContents() instanceof TranslatableContents contents)) {
			return new DeathInfo(DeathType.GENERIC, null, null, null, "");
		}
		DeathType type = DeathType.fromKey(contents.getKey());
		String cause = DeathType.causeOf(contents.getKey());
		Object[] args = contents.getArgs();
		if (args.length < 2 || !(args[1] instanceof Component name)) {
			return new DeathInfo(type, null, null, null, cause);
		}

		HoverEvent.EntityTooltipInfo tooltip = findEntityTooltip(name);
		EntityType<?> killerType = tooltip != null ? tooltip.type : null;
		Entity killer = level == null ? null : findKiller(level, player, name, tooltip);
		if (killer != null) {
			killerType = killer.getType();
		}
		// Killcam without anything to look at falls back to the generic scene.
		if (type.isKillcam() && killer == null) {
			type = DeathType.GENERIC;
		}
		return new DeathInfo(type, killer, name, killerType, cause);
	}

	private static HoverEvent.@Nullable EntityTooltipInfo findEntityTooltip(Component component) {
		if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowEntity showEntity) {
			return showEntity.entity();
		}
		for (Component sibling : component.getSiblings()) {
			HoverEvent.EntityTooltipInfo found = findEntityTooltip(sibling);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static @Nullable Entity findKiller(ClientLevel level, Player player, Component name, HoverEvent.@Nullable EntityTooltipInfo tooltip) {
		UUID uuid = tooltip != null ? tooltip.uuid : null;
		String plainName = name.getString();
		Entity best = null;
		double bestDistance = SEARCH_RADIUS * SEARCH_RADIUS;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == player) {
				continue;
			}
			if (uuid != null && uuid.equals(entity.getUUID())) {
				return entity;
			}
			// No exact match yet: remember the closest living thing that looks right.
			boolean sameType = tooltip != null && entity.getType() == tooltip.type;
			boolean sameName = entity instanceof LivingEntity && entity.getName().getString().equals(plainName);
			double distance = entity.distanceToSqr(player);
			if ((sameType || sameName) && distance < bestDistance) {
				best = entity;
				bestDistance = distance;
			}
		}
		return best;
	}
}
