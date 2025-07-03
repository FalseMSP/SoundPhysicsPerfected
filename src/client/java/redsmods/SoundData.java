package redsmods;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

// Inner class to store sound data
public class SoundData {
    public final SoundInstance sound;
    public final Vec3d position;
    public final String soundId;

    public SoundData(SoundInstance sound, Vec3d position, String soundId) {
        this.sound = sound;
        this.position = position;
        this.soundId = soundId;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SoundData)) return false;
        SoundData that = (SoundData) o;
        return Objects.equals(sound, that.sound) &&
                Objects.equals(position, that.position) &&
                Objects.equals(soundId, that.soundId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sound, position, soundId);
    }
}