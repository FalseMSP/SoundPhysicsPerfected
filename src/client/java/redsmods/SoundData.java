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
    public int distance = 0; // IN TICKS (blocks/SPEED_OF_SOUND_TICKS)

    public SoundData(RedSoundInstance sound, Vec3d position, Box boundingBox, String soundId) {
        this.sound = sound;
        this.position = position;
        this.boundingBox = boundingBox;
        this.soundId = soundId;
        this.timestamp = System.currentTimeMillis();
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }
    public int getDistance() {
        return distance;
    }
}