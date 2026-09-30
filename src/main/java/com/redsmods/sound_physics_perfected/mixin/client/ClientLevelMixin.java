package com.redsmods.sound_physics_perfected.mixin.client;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the tracer when a block changes, so cached ray paths are only reused while the world around is unchanged. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void spp$chunkLoaded(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        RaycastingHelper.onChunkLoaded(pos.x, pos.z);
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void spp$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        RaycastingHelper.onBlockChanged((ClientLevel) (Object) this, pos, oldState, newState);
    }
}
