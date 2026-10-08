package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaNativeContents;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Real Mojang 26.4 Snapshot 3 classes, not fixtures.
 * Tests registry bootstrap headlessly without launching Minecraft's GL UI.
 */
public final class NativeMinecraftSmoke {
    public static void main(String[] args) throws Exception {
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:wood_item", 0.7f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcrafttransport:cobblestone_item", 1.4f);
        CodaNativeContents.registerBlock("buildcraft_cml", "buildcraftcore:engine_redstone", 1.5f);
        CodaNativeContents.registerItem("buildcraft_cml", "buildcraftcore:wrench");
        CodaNativeContents.registerTab("buildcraft_cml", "buildcraftcore:buildcraft",
                "BuildCraft", "buildcrafttransport:wood_item",
                List.of("buildcrafttransport:wood_item", "buildcrafttransport:cobblestone_item",
                        "buildcraftcore:engine_redstone", "buildcraftcore:wrench"));

        ClassLoader loader = NativeMinecraftSmoke.class.getClassLoader();
        Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap", true, loader);
        System.out.println("Bootstrapping EXACT Mojang Snapshot 3 registries...");
        bootstrap.getMethod("bootStrap").invoke(null);

        Class<?> registries = Class.forName(
                "net.minecraft.core.registries.BuiltInRegistries", true, loader);
        Class<?> idClass = Class.forName("net.minecraft.resources.Identifier", true, loader);
        Method parse = idClass.getMethod("parse", String.class);
        int checked = 0;
        for (String name : List.of("buildcrafttransport:wood_item",
                "buildcrafttransport:cobblestone_item", "buildcraftcore:engine_redstone")) {
            Object blocks = registries.getField("BLOCK").get(null);
            Object id = parse.invoke(null, name);
            Object value = blocks.getClass().getMethod("getValue", idClass).invoke(blocks, id);
            if (value == null || blocks.getClass().getMethod("getKey", Object.class).invoke(blocks, value)
                    .toString().equals(name) == false) {
                throw new AssertionError("Native Block missing: " + name);
            }
            checked++;
        }
        for (String name : List.of("buildcrafttransport:wood_item",
                "buildcrafttransport:cobblestone_item", "buildcraftcore:engine_redstone",
                "buildcraftcore:wrench")) {
            Object items = registries.getField("ITEM").get(null);
            Object id = parse.invoke(null, name);
            Object value = items.getClass().getMethod("getValue", idClass).invoke(items, id);
            if (value == null || !items.getClass().getMethod("getKey", Object.class)
                    .invoke(items, value).toString().equals(name))
                throw new AssertionError("Native item missing: " + name);
            checked++;
        }
        Object creative = registries.getField("CREATIVE_MODE_TAB").get(null);
        Object name = parse.invoke(null, "buildcraftcore:buildcraft");
        Object tab = creative.getClass().getMethod("getValue", idClass).invoke(creative, name);
        if (tab == null) throw new AssertionError("BuildCraft Creative tab not registered");
        checked++;
        System.out.println("PASS: " + checked
                + " actual Mojang Snapshot 3 BuildCraft native block, item and Creative registry entries");
    }
}
