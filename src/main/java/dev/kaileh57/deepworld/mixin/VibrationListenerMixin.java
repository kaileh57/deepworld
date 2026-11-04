package dev.kaileh57.deepworld.mixin;

import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.hazard.Hazards;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VibrationSystem.Listener.class)
public class VibrationListenerMixin {
	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
	private void deepworld_hush(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 source, CallbackInfoReturnable<Boolean> cir) {
		if (!Hazards.config().sound || !(context.sourceEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (Meals.strength(player, "hushed") >= 2) {
			cir.setReturnValue(false);
			return;
		}
		if (event.is(GameEvent.BLOCK_DESTROY) && CookData.tag(player.getMainHandItem()).getStringOr("socket", "").equals("echo")) {
			cir.setReturnValue(false);
		}
	}
}
