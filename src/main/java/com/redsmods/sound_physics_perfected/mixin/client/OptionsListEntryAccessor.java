package com.redsmods.sound_physics_perfected.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
*///?}

/**
 * Lets 1.20.1's option list show widgets that are not OptionInstances: it only makes rows from OptionInstances,
 * so a row is made from a stand-in and its widgets are swapped. 1.21 has OptionsList#addSmall(AbstractWidget,
 * AbstractWidget) and needs nothing here. (The target is named by string because Entry is a protected class.)
 */
@SuppressWarnings("public-target") // public in the class file, but protected to javac
@Mixin(targets = "net.minecraft.client.gui.components.OptionsList$Entry")
public interface OptionsListEntryAccessor {
    //? if <1.21 {
    /*@Mutable
    @Accessor("children")
    void spp$setChildren(List<AbstractWidget> children);
    *///?}
}
