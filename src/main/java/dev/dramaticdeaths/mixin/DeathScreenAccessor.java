package dev.dramaticdeaths.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
	@Accessor("causeOfDeath")
	Component dramaticdeaths$getCauseOfDeath();

	@Accessor("hardcore")
	boolean dramaticdeaths$isHardcore();

	@Accessor("deathScore")
	Component dramaticdeaths$getDeathScore();
}
