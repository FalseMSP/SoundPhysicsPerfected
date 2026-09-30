package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Extra reverb effects for sounds whose surroundings differ from the listener's (after Vercidium's "grouped
 * EAX"): a zombie deep in a cave should ring like the cave even while you stand outside it, heard from the
 * cave's direction and only as loud as the part of that reverb you can actually hear.
 *
 * Every tick, sounds that are in a different kind of space are offered with their own reverb; similar ones
 * share a zone, and each zone becomes one OpenAL effect slot. Pure Java (testable); client thread only.
 */
public final class ReverbZones {
    public static final int MAX_ZONES = 2;
    /** How different (see {@link #distance}) a sound's space must be from the listener's to get its own zone. */
    static final double SEPARATE_SPACE = 0.45;
    /** How similar two sounds' spaces must be to share a zone. */
    static final double SAME_ZONE = 0.35;

    /** One sound's own reverb: its space's parameters, how much of it reaches the listener, and from where. */
    public static final class SourceReverb {
        public final ReverbParams params;
        /** 0..1: share of this sound's reverb the listener can hear (0 = sealed away, 1 = fully audible). */
        public final float gain;
        /** World-space horizontal pan (focused, like the listener's pans). */
        public final float panX, panZ;

        public SourceReverb(ReverbParams params, float gain, float panX, float panZ) {
            this.params = params;
            this.gain = gain;
            this.panX = panX;
            this.panZ = panZ;
        }
    }

    public static final class Zone {
        ReverbParams target;
        ReverbParams current;
        float gain, targetGain;
        float panX, panZ;
        double weight;
        boolean active;
        double idleSeconds;
        private boolean memberThisTick;
        private double panWeight, sumPanX, sumPanZ;

        public ReverbParams params() {
            return current;
        }

        /** Effect slot output gain (how much of this zone's reverb reaches the listener). */
        public float gain() {
            return gain;
        }

        /** The zone's reverb params with the listener-relative direction to pan towards (world space). */
        public ReverbParams withPan() {
            ReverbParams p = current;
            return new ReverbParams(p.density, p.diffusion, p.gain, p.gainHF, p.gainLF, p.decayTime, p.decayHFRatio,
                    p.decayLFRatio, p.reflectionsGain, p.reflectionsDelay, p.lateReverbGain, p.lateReverbDelay,
                    p.echoTime, p.echoDepth, p.modulationTime, p.modulationDepth, p.airAbsorptionGainHF,
                    p.hfReference, p.lfReference, p.roomRolloffFactor, panX, 0, panZ, panX, 0, panZ,
                    p.enclosure, p.meanFreePath, p.absorption);
        }

        public boolean active() {
            return active;
        }
    }

    private final Zone[] zones = new Zone[MAX_ZONES];

    public ReverbZones() {
        for (int i = 0; i < MAX_ZONES; i++) zones[i] = new Zone();
    }

    public Zone zone(int index) {
        return zones[index];
    }

    /** Start of a tick: forget last tick's members. */
    public void beginTick() {
        for (Zone z : zones) {
            z.memberThisTick = false;
            z.targetGain = 0.0f;
            z.weight = 0.0;
            z.panWeight = z.sumPanX = z.sumPanZ = 0.0;
        }
    }

    /**
     * Offers one playing sound. Returns the zone it should send to, or -1 when its surroundings are close enough
     * to the listener's that the listener's own reverb already covers it. Call strongest sounds first.
     *
     * @param loudness how much this sound matters (its volume); the loudest member of a zone defines its space
     */
    public int offer(SourceReverb reverb, float loudness, ReverbParams listener) {
        if (reverb == null || reverb.gain <= 0.02f) return -1;
        if (listener != null && distance(reverb.params, listener) < SEPARATE_SPACE) return -1;

        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < MAX_ZONES; i++) {
            Zone z = zones[i];
            if (!z.active) continue;
            double d = distance(reverb.params, z.memberThisTick ? z.target : z.current);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        if (best < 0 || bestDistance > SAME_ZONE) {
            int free = freeZone();
            if (free >= 0) best = free;
        }
        if (best < 0) return -1;

        Zone z = zones[best];
        double weight = loudness * reverb.gain;
        if (!z.active) {
            z.active = true;
            z.current = reverb.params;
            z.gain = 0.0f;
            z.panX = reverb.panX;
            z.panZ = reverb.panZ;
        }
        if (!z.memberThisTick || weight > z.weight) {
            z.target = reverb.params; // the zone sounds like its most important member's space
            z.weight = weight;
        }
        z.memberThisTick = true;
        z.targetGain = Math.max(z.targetGain, reverb.gain);
        z.sumPanX += reverb.panX * weight;
        z.sumPanZ += reverb.panZ * weight;
        z.panWeight += weight;
        z.idleSeconds = 0.0;
        return best;
    }

    private int freeZone() {
        for (int i = 0; i < MAX_ZONES; i++) {
            if (!zones[i].active) return i;
        }
        return -1;
    }

    /** End of a tick: ease zones towards their members; keep idle zones ringing for their decay, then free them. */
    public void endTick(double dtSeconds, double smoothingSeconds) {
        float alpha = smoothingSeconds <= 0.0 ? 1.0f : (float) (1.0 - Math.exp(-dtSeconds / smoothingSeconds));
        for (Zone z : zones) {
            if (!z.active) continue;
            if (z.memberThisTick) {
                z.current = z.current.approach(z.target, alpha);
                z.gain += (z.targetGain - z.gain) * alpha;
                if (z.panWeight > 0.0) {
                    z.panX += (float) ((z.sumPanX / z.panWeight - z.panX) * alpha);
                    z.panZ += (float) ((z.sumPanZ / z.panWeight - z.panZ) * alpha);
                }
            } else {
                // Let the reverb tail of sounds that just ended ring out before fading the zone away.
                z.idleSeconds += dtSeconds;
                if (z.idleSeconds > z.current.decayTime) {
                    z.gain *= (float) Math.exp(-dtSeconds / 0.5);
                    if (z.gain < 0.001f) {
                        z.active = false;
                        z.gain = 0.0f;
                    }
                }
            }
        }
    }

    /** How different two spaces sound: decay ratio (log), openness and reverb level. */
    static double distance(ReverbParams a, ReverbParams b) {
        return Math.abs(Math.log(a.decayTime / b.decayTime))
                + 1.5 * Math.abs(a.enclosure - b.enclosure)
                + 0.5 * Math.abs(a.lateReverbGain - b.lateReverbGain);
    }
}
