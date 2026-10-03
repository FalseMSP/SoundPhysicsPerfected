package com.redsmods.sound_physics_perfected.acoustics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The prebuilt acoustic table of every vanilla block ({@code block_acoustics.json}): its surface material and the gaps
 * sound gets through. Built offline from Minecraft's own data: every block state's collision shape compared with its
 * model (elements and texture transparency, as drawn), so a block that looks open (leaves, grates, bars, ladders,
 * door windows) lets sound through. Materials were assigned per block and reviewed per family. Blocks a version
 * does not have are simply never looked up; blocks the table does not have (modded ones) fall back to
 * {@link AcousticMaterials}' rules.
 */
public final class BlockAcoustics {
    /**
     * Leaves: real foliage barely stops sound. ISO 9613-2 puts dense foliage at 0.04 / 0.06 / 0.09-0.12 dB per metre
     * (bass / mid / treble); Minecraft's look denser, so about three times that. Per block: bass, mid, treble.
     */
    public static final float[] FOLIAGE = {0.99f, 0.97f, 0.94f};

    /** One block's row. */
    public record Row(AcousticMaterial material, float[] gaps, Map<String, float[]> gapsByState) {
        /** Share of sound through this state's gaps per band, or null when it has none. */
        public float[] pass(BlockState state) {
            if (gapsByState == null) return gaps;
            for (Map.Entry<String, float[]> e : gapsByState.entrySet()) {
                String[] kv = e.getKey().split("=", 2);
                if (kv[1].equals(AcousticMaterials.propertyValue(state, kv[0]))) return e.getValue();
            }
            return null;
        }
    }

    private static volatile Map<String, Row> rows;

    private BlockAcoustics() {
    }

    /** The row for a block id ("minecraft:oak_leaves"), or null when the table does not have it. */
    public static Row get(String id) {
        Map<String, Row> r = rows;
        if (r == null) r = load();
        return r.get(id);
    }

    private static synchronized Map<String, Row> load() {
        if (rows != null) return rows;
        Map<String, Row> out = new HashMap<>();
        try (InputStream in = BlockAcoustics.class.getResourceAsStream("/assets/sound_physics_perfected/block_acoustics.json")) {
            if (in != null) {
                JsonObject blocks = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("blocks");
                for (Map.Entry<String, JsonElement> e : blocks.entrySet()) out.put(e.getKey(), parse(e.getValue()));
            }
        } catch (Exception e) {
            // A broken table only costs the vanilla specifics: everything falls back to the rules.
            out.clear();
        }
        if (out.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("sound_physics_perfected").warn("Block acoustics table missing; using rules only");
        } else {
            org.slf4j.LoggerFactory.getLogger("sound_physics_perfected").info("Loaded acoustics of {} blocks", out.size());
        }
        rows = out;
        return out;
    }

    private static Row parse(JsonElement value) {
        if (value.isJsonPrimitive()) return new Row(AcousticMaterial.byName(value.getAsString()), null, null);
        JsonObject o = value.getAsJsonObject();
        AcousticMaterial material = o.has("material") ? AcousticMaterial.byName(o.get("material").getAsString()) : null;
        JsonElement gaps = o.get("gaps");
        if (gaps == null || gaps.isJsonNull()) return new Row(material, null, null);
        if (gaps.isJsonPrimitive()) return new Row(material, "foliage".equals(gaps.getAsString()) ? FOLIAGE : null, null);
        if (gaps.isJsonArray()) return new Row(material, bands(gaps), null);
        Map<String, float[]> byState = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : gaps.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonNull()) byState.put(e.getKey(), bands(e.getValue()));
        }
        return new Row(material, null, byState);
    }

    private static float[] bands(JsonElement array) {
        var a = array.getAsJsonArray();
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
