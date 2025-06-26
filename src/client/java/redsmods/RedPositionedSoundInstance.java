package redsmods;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public class RedPositionedSoundInstance extends PositionedSoundInstance {
    public RedPositionedSoundInstance(Identifier soundId, SoundCategory category, float volume, float pitch, Random random, boolean repeatable, int repeatDelay, AttenuationType attenuationType, float x, float y, float z, boolean relative) {
        super(soundId,category,volume,pitch,random,repeatable,repeatDelay,attenuationType,x,y,z,relative);
    }
}
