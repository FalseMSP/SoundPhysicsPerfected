package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

//? if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
*///?}


//? if neoforge {
/*@Mod(value = "@MODID@", dist = Dist.CLIENT)
*///?} else if forge {
/*@Mod(value = "@MODID@")
*///?} else {
@Entrypoint
//?}
public class SoundPhysicsPerfected /*? if fabric {*/ implements ModInitializer /*?}*/ {

    public static final String MOD_ID = "sound_physics_perfected";
    public static final Logger DEBUG_LOGGER = LoggerFactory.getLogger(MOD_ID);

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
    //? if forge {
    /*public SoundPhysicsPerfected() {
        Config.CONFIG.load();
    }
    *///?}
}
