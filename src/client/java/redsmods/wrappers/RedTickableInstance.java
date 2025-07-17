package redsmods.wrappers;

import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.Nullable;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import redsmods.RaycastingHelper;

import static redsmods.RaycastingHelper.TICK_RATE;

public class RedTickableInstance implements TickableSoundInstance {
    private final Identifier soundID;
    private final Sound sound;
    private final SoundCategory category;
    private final Vec3d originalPos;
    private final float originalVolume;
    private SoundInstance wrapped;
    private double x;
    private double y;
    private double z;
    private boolean done;
    private float volume;
    private float pitch;
    private int tickCount;

    public RedTickableInstance(Identifier soundID, Sound sound, SoundCategory category, Vec3d position, float volume, float pitch, SoundInstance wrapped, Vec3d originalPos, float originalVolume) {
        this.soundID = soundID;
        this.sound = sound;
        this.category = category;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.done = false;
        this.volume = volume;
        this.pitch = pitch;
        this.wrapped = wrapped;
        this.originalPos = originalPos;
        this.originalVolume = originalVolume;
        tickCount = 0;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void tick() {
        tickCount++;
        if (done || TICK_RATE == 0) return; // DONE or ticking sounds is off
        if (tickCount % TICK_RATE == 0) // only update once every .1 second
            RaycastingHelper.tickQueue.add(this);
        if (wrapped instanceof TickableSoundInstance)
            ((TickableSoundInstance) wrapped).tick();
    }

    @Override
    public Identifier getId() {
        return soundID;
    }

    @Override
    public @Nullable WeightedSoundSet getSoundSet(SoundManager soundManager) {
        return wrapped.getSoundSet(soundManager);
    }

    @Override
    public Sound getSound() {
        return sound;
    }

    @Override
    public SoundCategory getCategory() {
        return category;
    }

    @Override
    public boolean isRepeatable() {
        return wrapped.isRepeatable();
    }

    public Vec3d getOriginalPosition() {
        return originalPos;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public boolean shouldAlwaysPlay() {
        return wrapped.shouldAlwaysPlay();
    }

    @Override
    public boolean isRelative() {
        return wrapped.isRelative();
    }

    @Override
    public int getRepeatDelay() {
        return wrapped.getRepeatDelay();
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public AttenuationType getAttenuationType() {
        return AttenuationType.LINEAR;
    }

    public void stop() {
        this.done = true;
    }

    public void setPos(Vec3d targetPosition) {
        x = targetPosition.getX();
        y = targetPosition.getY();
        z = targetPosition.getZ();
    }

    public void setVolume(float max) {
        volume = max;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public float getOriginalVolume() {
        return originalVolume;
    }
}