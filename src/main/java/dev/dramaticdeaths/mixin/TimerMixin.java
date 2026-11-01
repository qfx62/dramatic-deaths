package dev.dramaticdeaths.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dramaticdeaths.DeathCinematic;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Slow motion: makes each client tick last longer while the cinematic plays. */
@Mixin(DeltaTracker.Timer.class)
public abstract class TimerMixin {
	@ModifyExpressionValue(
			method = "advanceGameTime",
			at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F")
	)
	private float dramaticdeaths$slowDown(float msPerTick) {
		return msPerTick / DeathCinematic.timeScale();
	}
}
