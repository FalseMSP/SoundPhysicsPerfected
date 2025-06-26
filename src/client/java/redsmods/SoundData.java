package redsmods;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

// Inner class to store sound data
public class SoundData {
    public final RedSoundInstance sound;
    public final Vec3d position;
    public final Box boundingBox;
    public final String soundId;
    public final long timestamp;

    public SoundData(RedSoundInstance sound, Vec3d position, Box boundingBox, String soundId) {
        this.sound = sound;
        this.position = position;
        this.boundingBox = boundingBox;
        this.soundId = soundId;
        this.timestamp = System.currentTimeMillis();
    }
}