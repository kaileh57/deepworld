package dev.kaileh57.deepworld.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;

public class DwOreBlock extends DropExperienceBlock implements PolymerTexturedBlock {
	private final BlockState client_state;

	public DwOreBlock(IntProvider xp, Properties properties, Identifier model) {
		super(xp, properties);
		this.client_state = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return client_state;
	}
}
