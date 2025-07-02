package redsmods.mixin.client;

import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import redsmods.Config;

@Mixin(SoundEvent.class)
public class SoundEventMixin {

    private static final float SOUND_DISTANCE_MULTI = 8;

    @ModifyConstant(method = "getDistanceToTravel", constant = @Constant(floatValue = 16F), expect = 2)
    private float allowance1(float value) {
        return value * SOUND_DISTANCE_MULTI;
    }

}