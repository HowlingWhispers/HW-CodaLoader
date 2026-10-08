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
 * Strict, version-pinned Minecraft 26.4 Snapshot 3 server-thread tick hook.
 * Unknown mappings fail closed; never guess another method or run client-side.
 */
public final class CodaServerTickTransformer implements ClassFileTransformer {
    private static final String TARGET = "net/minecraft/server/MinecraftServer";
    private static final String METHOD = "tickServer";
    private static final String DESCRIPTOR = "(Ljava/util/function/BooleanSupplier;)V";

    public static void install(Instrumentation instrumentation) {
        try {
            instrumentation.addTransformer(new CodaServerTickTransformer(), false);
            System.out.println("[H.O.W.L.] Server tick hook registered (Snapshot 3 named mapping).");
        } catch (Throwable failure) {
            System.err.println("[H.O.W.L.] Could not register server tick hook: " + failure);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] original) {
        if (!TARGET.equals(name)) return null;
        try {
            ClassReader reader = new ClassReader(original);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] matches = {0};
            int[] callbacks = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    if (!METHOD.equals(methodName) || !DESCRIPTOR.equals(descriptor)
                            || (access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
                        return visitor;
                    matches[0]++;
                    return new MethodVisitor(Opcodes.ASM9, visitor) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/howlingwhispers/codaloader/bootstrap/CodaServerTickLifecycle",
                                        "afterTick", "(Ljava/lang/Object;)V", false);
                                callbacks[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (matches[0] != 1 || callbacks[0] == 0) {
                System.err.println("[H.O.W.L.] No supported Snapshot 3 tickServer method found; "
                        + "server tick callbacks disabled rather than guessing.");
                return null;
            }
            System.out.println("[H.O.W.L.] Installed server-thread callbacks at "
                    + callbacks[0] + " native tick returns.");
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[H.O.W.L.] Server tick transformer refused unsafe mapping: " + error);
            return null;
        }
    }
}
