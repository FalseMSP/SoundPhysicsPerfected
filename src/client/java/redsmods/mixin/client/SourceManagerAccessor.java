package redsmods.mixin.client;

import net.minecraft.client.sound.Source;
import net.minecraft.client.sound.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Channel.SourceManager.class)
public interface SourceManagerAccessor {
    /**
     * Accessor for the private `sources` map in SourceManager,
     * which maps SoundInstance → Channel.
     */
    @Accessor("source")
    Source getSource();
}