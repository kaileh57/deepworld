package dev.kaileh57.deepworld.mixin;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractVillager.class)
public class AbstractVillagerMixin {
	@Redirect(method = "addOffersFromTradeSet", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/loot/LootContext$Builder;create(Ljava/util/Optional;)Lnet/minecraft/world/level/storage/loot/LootContext;"))
	private LootContext deepworld_seeded_trades(LootContext.Builder builder, Optional<Identifier> sequence, ServerLevel level, MerchantOffers offers, ResourceKey<TradeSet> key) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		long seed = self.getUUID().getMostSignificantBits() ^ self.getUUID().getLeastSignificantBits() ^ key.identifier().hashCode() * 31L;
		return builder.withOptionalRandomSource(RandomSource.create(seed)).create(Optional.empty());
	}
}
