package redsmods;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

// Inner class to store sound data
public class SoundData {
    public final SoundInstance sound;
    public final Vec3d position;
    public final String soundId;
    public final long timestamp;
    public int distance = 0; // IN TICKS (blocks/SPEED_OF_SOUND_TICKS)

    public SoundData(SoundInstance sound, Vec3d position, String soundId) {
        this.sound = sound;
        this.position = position;
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