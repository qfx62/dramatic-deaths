package dev.dramaticdeaths.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dramaticdeaths.CameraDirector;
import dev.dramaticdeaths.DeathCinematic;
import dev.dramaticdeaths.DeathInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the camera over to {@link CameraDirector} while the cinematic plays. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private Level level;
	@Shadow private Entity entity;
	@Shadow private boolean detached;
	@Shadow @Final private Quaternionf rotation;

	@Shadow protected abstract void setPosition(Vec3 position);

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

	@Inject(
			method = "update",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
	)
	private void dramaticdeaths$directCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!DeathCinematic.validate() || entity == null || level == null) {
			return;
		}
		float partial = getCameraEntityPartialTicks(deltaTracker);
		DeathInfo info = DeathCinematic.info();
		Entity killer = info.killer();
		if (killer != null && !killer.isRemoved()) {
			DeathCinematic.rememberKiller(killer.getEyePosition(partial), killer.getPosition(partial));
		}
		float killerSize = killer != null ? killer.getBbWidth() : 0.6f;

		CameraDirector.Shot shot = CameraDirector.shoot(info.type(), DeathCinematic.seconds(), DeathCinematic.startYaw(),
				entity.getPosition(partial), DeathCinematic.deathEye(),
				DeathCinematic.lastKillerEye(), DeathCinematic.lastKillerFeet(), killerSize);

		// Don't fly through walls: stop just in front of the first block in the way.
		Vec3 position = shot.position();
		HitResult hit = level.clip(new ClipContext(shot.anchor(), position, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 pullBack = shot.anchor().subtract(hit.getLocation()).normalize().scale(0.3);
			position = hit.getLocation().add(pullBack);
		}

		Vec3 look = shot.target().subtract(position);
		float yRot = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
		float xRot = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
		detached = true;
		setPosition(position);
		setRotation(yRot, xRot);
		if (shot.roll() != 0.0f) {
			rotation.rotateZ(shot.roll() * Mth.DEG_TO_RAD);
		}
		DeathCinematic.setFov(shot.fov());
	}

	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float dramaticdeaths$zoom(float fov) {
		return fov * DeathCinematic.fov();
	}
}
