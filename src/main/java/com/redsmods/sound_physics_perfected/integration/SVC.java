package com.redsmods.sound_physics_perfected.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.redsmods.sound_physics_perfected.OpenALEffectsHandler;
import com.redsmods.sound_physics_perfected.config.Config;
import de.maxhenkel.voicechat.api.events.*;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.countBlocksBetween;

@ForgeVoicechatPlugin
public class SVC implements VoicechatPlugin {

    private final Map<UUID, VoiceChannelData> audioChannels;
    private final OpenALEffectsHandler fxHandler = new OpenALEffectsHandler();

    public SVC() {
        audioChannels = new HashMap<>();
    }

    @Override
    public String getPluginId() {
        return "soundphysicsperfected";
    }

    @Override
    public void initialize(VoicechatApi api) {
        System.out.println("Initializing Sound Physics Perfected Voice Chat integration");
        audioChannels.clear();
//        fxHandler = new OpenALEffectsHandler();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // This is the key event - it gives us direct access to the OpenAL source
        registration.registerEvent(OpenALSoundEvent.class, this::onOpenALSound);
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
        registration.registerEvent(CreateOpenALContextEvent.class, this::onCreateALContext);
    }

    private void onCreateALContext(CreateOpenALContextEvent event) {
        // Initialize your OpenAL effects when the context is created
        System.out.println("Initializing OpenAL effects for voice chat");
        // Your OpenAL initialization code here
        long context = event.getContext();
        long device = event.getDevice();
        fxHandler.initializeReverb(context,device);
        System.out.println("succ init openal yipee");
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        // Clean up unused channels
        audioChannels.values().removeIf(VoiceChannelData::canBeRemoved);
    }

    private void onOpenALSound(OpenALSoundEvent event) {
        // This event gives us direct access to the OpenAL source!
        int openALSource = event.getSource();
        Position position = event.getPosition();
        UUID channelId = event.getChannelId();

        if (channelId == null || position == null) {
            return;
        }

        // Get or create channel data
        VoiceChannelData channelData = audioChannels.computeIfAbsent(channelId,
                id -> new VoiceChannelData(id));

        // Convert position to Vec3
        Vec3 sourcePos = new Vec3(position.getX(), position.getY(), position.getZ());

        // Update channel data
        channelData.updateSound(openALSource, sourcePos, event.getCategory());

        // Apply sound physics effects
        applySoundPhysicsEffects(openALSource, sourcePos, channelData);
    }

    private void applySoundPhysicsEffects(int openALSource, Vec3 sourcePos, VoiceChannelData channelData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Vec3 listenerPos = mc.player.position();

        // Calculate occlusion
        float occlusion = Math.pow(Config.getInstance().permeationAbsorption,countBlocksBetween(mc.level, listenerPos, sourcePos,mc.player));

        // Apply your OpenAL effects using the source ID
        applyOpenALEffects(openALSource, occlusion);
    }

    private float getMaterialOcclusion(BlockState blockState) {
        String blockName = blockState.getBlock().getDescriptionId();

        // Material-based occlusion values
        if (blockName.contains("stone") || blockName.contains("concrete")) {
            return 0.8f;
        } else if (blockName.contains("wood")) {
            return 0.6f;
        } else if (blockName.contains("glass")) {
            return 0.3f;
        } else if (blockName.contains("wool") || blockName.contains("carpet")) {
            return 0.9f; // High absorption
        } else if (blockName.contains("leaves")) {
            return 0.4f;
        } else if (blockName.contains("water") || blockName.contains("lava")) {
            return 0.7f;
        }

        return 0.7f; // Default occlusion
    }

    private void applyOpenALEffects(int openALSource, float occlusion) {
        try {
            if(Config.getInstance().voicechatReverb)  fxHandler.applyReverbToSource(openALSource);
            if(Config.getInstance().voicechatMuffle)  fxHandler.applyMuffleToSource(openALSource, occlusion);

//            System.out.println("Applying effects to OpenAL source: " + openALSource +
//                    ", occlusion: " + String.format("%.2f", occlusion));

        } catch (Exception e) {
            System.err.println("Failed to apply OpenAL effects: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Data classes
    public static class VoiceChannelData {
        @Getter
        private final UUID channelId;
        @Getter
        private int lastSource = -1;
        @Getter
        private Vec3 lastPosition;
        @Getter
        private String lastCategory;
        private long lastUpdateTime;

        public VoiceChannelData(UUID channelId) {
            this.channelId = channelId;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public void updateSound(int source, Vec3 position, String category) {
            this.lastSource = source;
            this.lastPosition = position;
            this.lastCategory = category;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public boolean canBeRemoved() {
            // Remove if not updated for 5 seconds
            return System.currentTimeMillis() - lastUpdateTime > 5000;
        }
    }
}