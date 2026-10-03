import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.PrintWriter;
import java.util.Locale;

/** Writes one JSON line per block state: shapes, occlusion, render layer, sound, resistance, fluid. */
public class Dump {
    static String boxes(VoxelShape s) {
        StringBuilder b = new StringBuilder("[");
        for (AABB a : s.toAabbs()) {
            if (b.length() > 1) b.append(',');
            b.append(String.format(Locale.ROOT, "[%.4f,%.4f,%.4f,%.4f,%.4f,%.4f]", a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
        }
        return b.append(']').toString();
    }

    static String q(Object o) {
        return "\"" + String.valueOf(o).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        java.lang.reflect.Method layer = null;
        try {
            Class<?> c = Class.forName("net.minecraft.client.renderer.ItemBlockRenderTypes");
            layer = c.getMethod("getChunkRenderType", BlockState.class);
        } catch (Throwable t) {
            System.err.println("no render layers: " + t);
        }
        int n = 0;
        try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
            for (Block block : BuiltInRegistries.BLOCK) {
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                StringBuilder supers = new StringBuilder("[");
                for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    if (supers.length() > 1) supers.append(',');
                    supers.append(q(c.getSimpleName()));
                }
                supers.append(']');
                String sound;
                try {
                    sound = block.defaultBlockState().getSoundType().getBreakSound().location().getPath();
                } catch (Throwable t) {
                    sound = "?";
                }
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    String col, outl;
                    try { col = boxes(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)); } catch (Throwable t) { col = "\"ERR " + t.getClass().getSimpleName() + "\""; }
                    try { outl = boxes(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)); } catch (Throwable t) { outl = "\"ERR\""; }
                    String lay = "?";
                    if (layer != null) {
                        try { lay = String.valueOf(layer.invoke(null, state)); } catch (Throwable t) { lay = "ERR"; }
                    }
                    String s = state.toString();
                    int br = s.indexOf('[');
                    String props = br < 0 ? "" : s.substring(br + 1, s.length() - 1);
                    out.println("{\"id\":" + q(id) + ",\"cls\":" + supers + ",\"props\":" + q(props)
                            + ",\"def\":" + (state == block.defaultBlockState())
                            + ",\"col\":" + col + ",\"out\":" + outl
                            + ",\"occ\":" + state.canOcclude()
                            + ",\"layer\":" + q(lay)
                            + ",\"fluid\":" + q(BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()))
                            + ",\"fh\":" + state.getFluidState().getOwnHeight()
                            + ",\"snd\":" + q(sound)
                            + ",\"res\":" + block.getExplosionResistance()
                            + ",\"dyn\":" + block.hasDynamicShape()
                            + ",\"air\":" + state.isAir() + "}");
                    n++;
                }
            }
        }
        System.err.println("states " + n);
    }
}
