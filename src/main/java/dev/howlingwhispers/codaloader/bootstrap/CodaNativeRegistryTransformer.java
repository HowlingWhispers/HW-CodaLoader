package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Three EXACT Snapshot 3 bootstrap seams. Once Mojang changes an expected
 * class/method descriptor, disable that seam rather than registering after
 * freeze or mutating vanilla global registries illegally.
 *
 * H.O.W.L. mod metadata is loaded before Minecraft's main class is invoked.
 */
public final class CodaNativeRegistryTransformer implements ClassFileTransformer {
    private static final String HOOK =
            "dev/howlingwhispers/codaloader/bootstrap/CodaNativeRegistryBridge";

    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaNativeRegistryTransformer(), false);
        System.out.println("[H.O.W.L.] Native BuildCraft registry hooks armed for Snapshot 3.");
    }

    @Override public byte[] transform(ClassLoader loader, String internalName, Class<?> redefining,
                                      ProtectionDomain protection, byte[] bytes) {
        String method, descriptor, handler, callDesc;
        switch (internalName == null ? "" : internalName) {
            case "net/minecraft/world/level/block/Blocks" -> {
                method = "<clinit>"; descriptor = "()V";
                handler = "registerBlocksAndItems"; callDesc = "()V";
            }
            case "net/minecraft/world/item/CreativeModeTabs" -> {
                method = "bootstrap";
                descriptor = "(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/item/CreativeModeTab;";
                handler = "registerCreativeTabs"; callDesc = "(Ljava/lang/Object;)V";
            }
            case "net/minecraft/core/registries/BuiltInRegistries" -> {
                method = "freeze"; descriptor = "()V";
                handler = "registerNativeFactories"; callDesc = "()V";
            }
            default -> { return null; }
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] seen = {0};
            int[] calls = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name,
                        String desc, String signature, String[] exceptions) {
                    MethodVisitor original = super.visitMethod(access, name, desc, signature, exceptions);
                    if (!method.equals(name) || !descriptor.equals(desc)
                            || (access & Opcodes.ACC_ABSTRACT) != 0) return original;
                    seen[0]++;
                    return new MethodVisitor(Opcodes.ASM9, original) {
                        @Override public void visitCode() {
                            super.visitCode();
                            if (method.equals("freeze")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, handler, callDesc, false);
                                calls[0]++;
                            }
                        }
                        @Override public void visitInsn(int opcode) {
                            if ((method.equals("<clinit>") && opcode == Opcodes.RETURN)
                                || (method.equals("bootstrap") && opcode == Opcodes.ARETURN)) {
                                if (method.equals("bootstrap"))
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, handler, callDesc, false);
                                calls[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (seen[0] != 1 || calls[0] == 0) {
                System.err.println("[H.O.W.L.] Native registry seam missing/changed: " + internalName);
                return null;
            }
            System.out.println("[H.O.W.L.] Native registry hook active: " + internalName);
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] Native registry hook refused: " + internalName + ": " + error);
            return null;
        }
    }
}
