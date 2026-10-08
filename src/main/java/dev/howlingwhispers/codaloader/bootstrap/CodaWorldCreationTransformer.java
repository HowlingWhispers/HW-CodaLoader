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
 * Minecraft 26.4 Snapshot 3 experimental NEW-WORLD screen hook.
 * Never touches existing world loading or world generation itself.
 */
public final class CodaWorldCreationTransformer implements ClassFileTransformer {
    private static final String SCREEN =
            "net/minecraft/client/gui/screens/worldselection/CreateWorldScreen";
    private static final String HOOK =
            "dev/howlingwhispers/codaloader/bootstrap/CodaWorldCreationLifecycle";

    public static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaWorldCreationTransformer(), false);
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] source) {
        if (!SCREEN.equals(name)) return null;
        try {
            ClassReader reader = new ClassReader(source);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] matches = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method,
                        String descriptor, String signature, String[] exceptions) {
                    MethodVisitor visitor = super.visitMethod(access, method, descriptor,
                            signature, exceptions);
                    if (!method.equals("init") || !descriptor.equals("()V")
                            || (access & Opcodes.ACC_STATIC) != 0) return visitor;
                    matches[0]++;
                    return new MethodVisitor(Opcodes.ASM9, visitor) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "afterInit",
                                        "(Ljava/lang/Object;)V", false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (matches[0] != 1) {
                System.err.println("[H.O.W.L.] Quiet Underground: CreateWorldScreen.init"
                        + " changed, no worldgen hook installed.");
                return null;
            }
            System.out.println("[H.O.W.L.] Quiet Underground: new-world screen hook ready.");
            return writer.toByteArray();
        } catch (Throwable ex) {
            System.err.println("[H.O.W.L.] Quiet Underground: screen hook unavailable: " + ex);
            return null;
        }
    }
}
