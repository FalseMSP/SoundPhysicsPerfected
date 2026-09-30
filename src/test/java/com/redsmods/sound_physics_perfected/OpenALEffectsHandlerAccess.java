package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.acoustics.ReverbParams;

/** Test access to the package-private pan conversion. */
public final class OpenALEffectsHandlerAccess {
    private OpenALEffectsHandlerAccess() {
    }

    public static float[] pans(ReverbParams p, float[] basis) {
        return OpenALEffectsHandler.listenerSpacePans(p, basis);
    }
}
