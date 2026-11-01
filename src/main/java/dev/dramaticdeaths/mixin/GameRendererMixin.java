package dev.dramaticdeaths.mixin;

import dev.dramaticdeaths.DeathCinematic;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the colour grading and lens filters while the death scene is on. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private List<Identifier> requestedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void dramaticdeaths$requestFilters(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (DeathCinematic.isActive()) {
			requestedPostEffects.add(DeathCinematic.grade());
			requestedPostEffects.add(DeathCinematic.LENS);
		}
	}
}
