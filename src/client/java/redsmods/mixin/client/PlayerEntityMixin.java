package redsmods.mixin.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.RaycastingHelper;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    private static int TICKS_SINCE_WORLD = 0;
    // Map to store ray hit counts for each entity
    private java.util.Map<Entity, Integer> entityRayHitCounts = new java.util.HashMap<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerTick(CallbackInfo ci) {

        PlayerEntity player = (PlayerEntity) (Object) this;
        World world = player.getWorld();

        // Only run on client side to avoid server lag
        if (!world.isClient) {
            return;
        }

        // Cast rays from player and detect entities
        RaycastingHelper.castBouncingRaysAndDetectSFX(world, player);
        RaycastingHelper.playQueuedObjects(++TICKS_SINCE_WORLD);
    }
}