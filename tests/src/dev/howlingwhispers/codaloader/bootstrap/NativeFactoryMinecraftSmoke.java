package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaRegistryFactories;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Exercises real native subclasses, keys, registry IDs and item bindings on Mojang's client. */
public final class NativeFactoryMinecraftSmoke {
    private static final AtomicInteger constructions = new AtomicInteger();
    private static CodaRegistryFactories.Entry<Object> machine, item, sound;

    public static void declare() {
        machine = CodaRegistryFactories.register("api_test", "minecraft:block", "howl_api_test:machine", context -> {
            constructions.incrementAndGet();
            Class<?> properties = type("net.minecraft.world.level.block.state.BlockBehaviour$Properties");
            Object p = properties.getMethod("of").invoke(null);
            p = properties.getMethod("setId", type("net.minecraft.resources.ResourceKey")).invoke(p, context.key());
            return nativeSubclass().getConstructor(properties).newInstance(p);
        });
        item = CodaRegistryFactories.register("api_test", "minecraft:item", "howl_api_test:machine", context -> {
            Class<?> properties = type("net.minecraft.world.item.Item$Properties");
            Object p = properties.getConstructor().newInstance();
            p = properties.getMethod("setId", type("net.minecraft.resources.ResourceKey")).invoke(p, context.key());
            return type("net.minecraft.world.item.BlockItem").getConstructor(
                    type("net.minecraft.world.level.block.Block"), properties).newInstance(machine.get(), p);
        });
        sound = CodaRegistryFactories.register("api_test", "minecraft:sound_event", "howl_api_test:machine", context -> {
            Class<?> idType = type("net.minecraft.resources.Identifier");
            Object id = idType.getMethod("parse", String.class).invoke(null, context.id());
            return type("net.minecraft.sounds.SoundEvent").getMethod("createVariableRangeEvent", idType).invoke(null, id);
        });
        if (constructions.get() != 0 || machine.isBound())
            throw new AssertionError("Native machine constructed before Minecraft bootstrap");
    }

    public static int verify() throws Exception {
        if (!machine.isBound() || !item.isBound() || !sound.isBound() || constructions.get() != 1)
            throw new AssertionError("Native registry factory hook did not execute exactly once");
        Object block = machine.get();
        if (!block.getClass().getMethod("nativeBehavior").invoke(block).equals("preserved"))
            throw new AssertionError("Loader substituted a generic Block for the original subclass");
        Object registries = type("net.minecraft.core.registries.BuiltInRegistries").getField("BLOCK").get(null);
        if (!registries.getClass().getMethod("getKey", Object.class).invoke(registries, block)
                .toString().equals("howl_api_test:machine"))
            throw new AssertionError("Native block registered under the wrong key");
        Class<?> blockType = type("net.minecraft.world.level.block.Block");
        Object state = blockType.getMethod("defaultBlockState").invoke(block);
        Object ids = blockType.getField("BLOCK_STATE_REGISTRY").get(null);
        int stateId = (Integer)ids.getClass().getMethod("getId", Object.class).invoke(ids, state);
        if (stateId < 0 || ids.getClass().getMethod("byId", int.class).invoke(ids, stateId) != state)
            throw new AssertionError("Native subclass state cannot be encoded in block packets");
        Class<?> direction = type("net.minecraft.core.Direction");
        for (Object face : direction.getEnumConstants())
            if (state.getClass().getMethod("getFaceOcclusionShape", direction).invoke(state, face) == null)
                throw new AssertionError("Native subclass shape cache is uninitialized");
        Object byBlock = type("net.minecraft.world.item.Item").getMethod("byBlock", blockType).invoke(null, block);
        if (byBlock != item.get()) throw new AssertionError("Block-item association does not preserve native identity");
        Object soundRegistry = type("net.minecraft.core.registries.BuiltInRegistries").getField("SOUND_EVENT").get(null);
        if (!soundRegistry.getClass().getMethod("getKey", Object.class).invoke(soundRegistry, sound.get())
                .toString().equals("howl_api_test:machine"))
            throw new AssertionError("Additional built-in registry unsupported");
        try {
            CodaRegistryFactories.register("api_test", "minecraft:block", "howl_api_test:late", c -> block);
            throw new AssertionError("Registration allowed after registry freeze");
        } catch (IllegalStateException expected) { }
        return 13;
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, true, NativeFactoryMinecraftSmoke.class.getClassLoader());
    }

    private static Class<?> nativeSubclass() {
        String name = "howl/nativeapitest/MachineBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/world/level/block/Block", null);
        String descriptor = "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V";
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", descriptor, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/level/block/Block", "<init>", descriptor, false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0); constructor.visitEnd();
        MethodVisitor behavior = writer.visitMethod(Opcodes.ACC_PUBLIC, "nativeBehavior", "()Ljava/lang/String;", null, null);
        behavior.visitCode(); behavior.visitLdcInsn("preserved"); behavior.visitInsn(Opcodes.ARETURN);
        behavior.visitMaxs(0, 0); behavior.visitEnd(); writer.visitEnd();
        class NativeClassLoader extends ClassLoader {
            NativeClassLoader() { super(NativeFactoryMinecraftSmoke.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass("howl.nativeapitest.MachineBlock", bytes, 0, bytes.length); }
        }
        return new NativeClassLoader().define(writer.toByteArray());
    }
}
