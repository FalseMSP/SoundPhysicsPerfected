package redsmods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

enum RedsAttenuationType {
    INVERSE_SQUARE,
    LINEAR
}

public class Config {
    private static Config INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("SoundPhysicsPerfected.json");

    // RaysCast setting
    public int raysCast = 256;
    public int raysBounced = 3;
    public boolean reverbEnabled = true;
    public boolean permeationEnabled = true;
    public int maxRayLength = 8;
    public float SoundMult = 2;
    public int tickRate = 2; // once every 2 ticks
    public RedsAttenuationType attenuationType = RedsAttenuationType.INVERSE_SQUARE;

    public static Config getInstance() {
        if (INSTANCE == null) {
            INSTANCE = loadConfig();
        }
        return INSTANCE;
    }

    private static Config loadConfig() {
        Config config;

        if (Files.exists(CONFIG_FILE)) {
            try {
                String json = Files.readString(CONFIG_FILE);
                config = GSON.fromJson(json, Config.class);
            } catch (Exception e) {
                System.err.println("Failed to load config, using defaults: " + e.getMessage());
                config = new Config();
            }
        } else {
            config = new Config();
            config.save(); // Create default config file
        }

        return config;
    }

    public void save() {
        try {
            // Ensure config directory exists
            Files.createDirectories(CONFIG_FILE.getParent());

            String json = GSON.toJson(this);
            Files.writeString(CONFIG_FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public void reload() {
        INSTANCE = loadConfig();
    }
}