package com.redsmods.sound_physics_perfected.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.redsmods.sound_physics_perfected.OpenALEffectsHandler;
import com.redsmods.sound_physics_perfected.config.Config;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.*;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.countBlocksBetween;

@ForgeVoicechatPlugin
public class SVC implements VoicechatPlugin {

    private final Map<UUID, VoiceChannelData> audioChannels;
    private final OpenALEffectsHandler fxHandler = new OpenALEffectsHandler();

    public static String OWN_VOICE_CATEGORY = "own_voice";
    private static final UUID OWN_VOICE_ID = UUID.randomUUID();
    private ClientLocationalAudioChannel locationalAudioChannel;
    private VoicechatClientApi clientApi;

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
        registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        var ownVoice = event.getVoicechat().volumeCategoryBuilder()
                .setId(OWN_VOICE_CATEGORY)
                .setName("Own voice")
                .setNameTranslationKey("category.sound_physics_perfected.own_voice")
                .setDescription("The volume of your own voice")
                .setDescriptionTranslationKey("category.sound_physics_remastered.own_voice.description")
                .build();
        event.getVoicechat().registerVolumeCategory(ownVoice);
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
        audioChannels.values().removeIf(VoiceChannelData::canBeRemoved);

        if (event.isConnected()) {  // ← check if this method exists; if not, null-check getVoicechat()
            clientApi = event.getVoicechat();
            locationalAudioChannel = clientApi.createLocationalAudioChannel(
                    OWN_VOICE_ID,
                    clientApi.createPosition(0D, 0D, 0D)
            );
            // Set a very short distance so falloff is minimal near the player
            locationalAudioChannel.setDistance(1f);
        } else {
            clientApi = null;
            locationalAudioChannel = null;
        }
    }

    private void onOpenALSound(OpenALSoundEvent event) {
        int openALSource = event.getSource();
        Position position = event.getPosition();
        UUID channelId = event.getChannelId();

        if (channelId == null) {
            return;
        }

        boolean isOwnVoice = OWN_VOICE_ID.equals(channelId);

        if (isOwnVoice) {
            // Apply reverb to own voice but skip occlusion/positional effects
            org.lwjgl.openal.AL10.alSourcei(openALSource,
                    EXTEfx.AL_DIRECT_FILTER, fxHandler.getDirectBlockFilter());
            if (Config.getInstance().voicechatReverbSelf) {
                fxHandler.applyReverbToSource(openALSource);
                org.lwjgl.openal.AL10.alSourcef(openALSource, org.lwjgl.openal.AL10.AL_GAIN, 0.0f);
            }
            return;
        }

        if (position == null) {
            return;
        }

        VoiceChannelData channelData = audioChannels.computeIfAbsent(channelId, VoiceChannelData::new);
        Vec3 sourcePos = new Vec3(position.getX(), position.getY(), position.getZ());
        channelData.updateSound(openALSource, sourcePos, event.getCategory());
        applySoundPhysicsEffects(openALSource, sourcePos, channelData);
    }

    private void applySoundPhysicsEffects(int openALSource, Vec3 sourcePos, VoiceChannelData channelData) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Vec3 listenerPos = mc.player.getEyePosition();

        // Calculate occlusion
        float occlusion = (float) Math.pow(Config.getInstance().permeationAbsorption,countBlocksBetween(mc.level, listenerPos, sourcePos,mc.player));

        // Apply your OpenAL effects using the source ID
        applyOpenALEffects(openALSource, 1-occlusion);
    }

    private void applyOpenALEffects(int openALSource, float occlusion) {
        try {
            if(Config.getInstance().voicechatReverb)  fxHandler.applyReverbToSource(openALSource);
            if(Config.getInstance().voicechatMuffle)  fxHandler.applyMuffleToSource(openALSource, occlusion);

//            System.out.println("Applying effects to OpenAL source: " + openALSource +
//                    ", occlusion: " + String.format("%.2f", occlusion));
            if(Config.getInstance().voicechatMuffleVolume) {
                float volumeMultiplier = 1-occlusion;
                volumeMultiplier = Math.max(0.0f, Math.min(1.0f, volumeMultiplier));
                org.lwjgl.openal.AL10.alSourcef(openALSource, org.lwjgl.openal.AL10.AL_GAIN, volumeMultiplier);
            }

        } catch (Exception e) {
            System.err.println("Failed to apply OpenAL effects: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void onClientSound(ClientSoundEvent event) {
        if (!Config.getInstance().voicechatReverbSelf) return;
        if (locationalAudioChannel == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 fwd = mc.player.getLookAngle().scale(0.5);
        Vec3 pos = eye.add(fwd);

        locationalAudioChannel.setLocation(event.getVoicechat().createPosition(pos.x, pos.y, pos.z));
        locationalAudioChannel.setCategory(OWN_VOICE_CATEGORY);
        locationalAudioChannel.play(event.getRawAudio());
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
