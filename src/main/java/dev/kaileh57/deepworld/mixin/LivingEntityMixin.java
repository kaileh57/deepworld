package dev.kaileh57.deepworld.mixin;

import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.cooking.Spoilage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	protected ItemStack useItem;

	@Unique
	private ItemStack deepworld_eaten = ItemStack.EMPTY;

	@Unique
	private int deepworld_food_before;

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void deepworld_before_eat(CallbackInfo ci) {
		deepworld_eaten = useItem.copy();
		if ((Object) this instanceof ServerPlayer player) {
			deepworld_food_before = player.getFoodData().getFoodLevel();
		}
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void deepworld_after_eat(CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || deepworld_eaten.isEmpty()) {
			return;
		}
		if (!CookData.tag(deepworld_eaten).getStringOr("dish", "").isEmpty()) {
			Meals.eat(player, deepworld_eaten);
		}
		if (player.getBlockY() < Spoilage.heat_line && Spoilage.max_minutes(deepworld_eaten) > 0) {
			int gained = player.getFoodData().getFoodLevel() - deepworld_food_before;
			if (gained > 0) {
				player.getFoodData().setFoodLevel(deepworld_food_before + (gained + 3) / 4);
			}
		}
		deepworld_eaten = ItemStack.EMPTY;
	}
}
