package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.acoustics.VelocityTracker;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Doppler effect. OpenAL shifts a sound's pitch by how fast it and the listener move along the line between
 * them, but vanilla never tells it that anything moves. This measures the listener's and every playing sound's
 * real movement each tick and passes it on. A sound placed at a traced (apparent) position still moves with its
 * real source, so a path that changes does not bend the pitch. Head-relative sounds (music, UI, ambience) move
 * with the listener in OpenAL, so they are not shifted.
 *
 * Client thread only (OpenAL listener state is set there by vanilla too).
 */
public final class Doppler {
    private static final Map<SoundInstance, VelocityTracker> SOUNDS = new IdentityHashMap<>();
    private static final VelocityTracker LISTENER = new VelocityTracker();
    private static float appliedFactor = -1.0f, appliedSpeed = -1.0f;
    private static boolean listenerMoving;

    private Doppler() {
    }

    /**
     * Once per tick: measures movement and updates OpenAL's listener velocity, Doppler factor and speed of sound.
     *
     * @param strength 0 = off, 1 = physical
     */
    public static void tick(Vec3 eye, Iterable<SoundInstance> playing, float strength, float speedOfSound) {
        float factor = Math.max(strength, 0.0f);
        if (factor != appliedFactor) {
            AL10.alDopplerFactor(factor);
            appliedFactor = factor;
        }
        float speed = Math.max(speedOfSound, 1.0f);
        if (speed != appliedSpeed) {
            AL11.alSpeedOfSound(speed);
            appliedSpeed = speed;
        }
        if (factor <= 0.0f) {
            SOUNDS.clear();
            if (listenerMoving) {
                AL10.alListener3f(AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
                listenerMoving = false;
            }
            return;
        }

        LISTENER.update(eye.x, eye.y, eye.z);
        if (LISTENER.moving() || listenerMoving) {
            AL10.alListener3f(AL10.AL_VELOCITY, LISTENER.vx(), LISTENER.vy(), LISTENER.vz());
            listenerMoving = LISTENER.moving();
        }

        Map<SoundInstance, VelocityTracker> seen = new IdentityHashMap<>();
        for (SoundInstance sound : playing) {
            if (sound == null || sound.isRelative()) continue;
            double x, y, z;
            if (sound instanceof RedTickableInstance red) {
                Vec3 real = red.getOriginalPosition(); // not the traced, apparent position
                x = real.x;
                y = real.y;
                z = real.z;
            } else {
                x = sound.getX();
                y = sound.getY();
                z = sound.getZ();
            }
            VelocityTracker tracker = SOUNDS.get(sound);
            if (tracker == null) tracker = new VelocityTracker();
            tracker.update(x, y, z);
            seen.put(sound, tracker);
        }
        SOUNDS.clear();
        SOUNDS.putAll(seen);
    }

    /** The sound's velocity in blocks per second, or null when it has not been seen moving. */
    public static float[] velocityOf(SoundInstance sound) {
        VelocityTracker tracker = SOUNDS.get(sound);
        if (tracker == null) return null;
        return new float[]{tracker.vx(), tracker.vy(), tracker.vz()};
    }

    /** The sound engine restarted (new OpenAL context): apply everything again. */
    public static void reset() {
        appliedFactor = appliedSpeed = -1.0f;
        listenerMoving = true;
        SOUNDS.clear();
    }
}
