package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.config.Config;
//? if fabric {
import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ModInitializer;
//?}
//? if neoforge {
/*import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
*///?}


//? if neoforge {
/*@Mod(value = "@MODID@", dist = Dist.CLIENT)
*///?} else {
@Entrypoint
//?}
public class SoundPhysicsPerfected /*? if fabric {*/ implements ModInitializer /*?}*/ {
    //? if fabric {
    @Override
    public void onInitialize() {
        Config.CONFIG.load();
    }
    //?}

    //? if neoforge {
    /*public SoundPhysicsPerfected() {
        Config.CONFIG.load();
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (client, parent) -> Config.configScreen(parent));
    }
	*///?}
}
