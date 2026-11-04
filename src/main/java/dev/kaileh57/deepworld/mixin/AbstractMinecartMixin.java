package dev.kaileh57.deepworld.mixin;

import dev.kaileh57.deepworld.transit.RailBeds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {
	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
	private void deepworld_bed_speed(ServerLevel level, CallbackInfoReturnable<Double> cir) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (cir.getReturnValue() < RailBeds.vanilla_speed || AbstractMinecart.useExperimentalMovement(level)) {
			return;
		}
		cir.setReturnValue(RailBeds.speed_for(level, self.getCurrentBlockPosOrRailBelow()));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void deepworld_bed_boost(CallbackInfo ci) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		if (!(self.level() instanceof ServerLevel level) || !self.isOnRails()) {
			return;
		}
		double max = RailBeds.speed_for(level, self.getCurrentBlockPosOrRailBelow());
		if (max <= RailBeds.vanilla_speed) {
			return;
		}
		Vec3 motion = self.getDeltaMovement();
		double speed = motion.horizontalDistance();
		if (speed > 0.05 && speed < max) {
			double target = Math.min(max, speed * 1.06 + 0.01);
			self.setDeltaMovement(motion.x * target / speed, motion.y, motion.z * target / speed);
		}
	}
}
