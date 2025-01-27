package io.github.fabricators_of_create.porting_lib.extensions;

import io.github.fabricators_of_create.porting_lib.util.ToolAction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface ItemStackExtensions {
	default boolean canPerformAction(ToolAction toolAction) {
		throw new RuntimeException("this should be overridden via mixin. what?");
	}

	/**
	 * Called before a block is broken. Return true to prevent default block
	 * harvesting.
	 *
	 * Note: In SMP, this is called on both client and server sides!
	 *
	 * @param pos       Block's position in world
	 * @param player    The Player that is wielding the item
	 * @return True to prevent harvesting, false to continue as normal
	 */
	default boolean onBlockStartBreak(BlockPos pos, Player player) {
		return !((ItemStack)(Object)this).isEmpty() && ((ItemExtensions)((ItemStack)(Object)this).getItem()).onBlockStartBreak(((ItemStack)(Object)this), pos, player);
	}
}
