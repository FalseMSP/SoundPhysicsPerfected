package redsmods.mixin.client;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Source.class)
public interface SourceAccessor {
    /**
     * Accessor for the private `sources` map in SourceManager,
     * which maps SoundInstance → Channel.
     */
    @Accessor("pointer")
    int getPointer();
}