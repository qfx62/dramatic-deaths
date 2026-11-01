package dev.dramaticdeaths.mixin;

import dev.dramaticdeaths.CinematicScreen;
import dev.dramaticdeaths.DeathCinematic;
import dev.dramaticdeaths.DeathInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla death screen never shows: our cinematic screen takes its place. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void dramaticdeaths$replaceDeathScreen(Screen screen, CallbackInfo ci) {
		if (!(screen instanceof DeathScreen deathScreen)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Gui gui = (Gui) (Object) this;
		ci.cancel();
		// Same death, screen was closed by something else: bring ours back instead of replaying.
		CinematicScreen existing = DeathCinematic.validate() ? DeathCinematic.screen() : null;
		if (existing != null) {
			gui.setScreen(existing);
			return;
		}
		Component message = ((DeathScreenAccessor) deathScreen).dramaticdeaths$getCauseOfDeath();
		gui.setScreen(DeathCinematic.start(deathScreen, DeathInfo.from(message, mc.player, mc.level), mc.player));
	}
}
