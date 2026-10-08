package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adds a GUI-thread callback at the end of native initialization, before rendering. */
public final class CodaMenuTransformer implements ClassFileTransformer {
    private static final Set<String> SCREENS = Set.of(
            "net/minecraft/client/gui/screens/Screen",
            "net/minecraft/client/gui/screens/TitleScreen",
            "net/minecraft/client/gui/screens/PauseScreen");
    private static final Set<String> METHODS = Set.of("init", "rebuildWidgets", "resize", "repositionElements");

    public static void install(Instrumentation instrumentation) {
        try {
            instrumentation.addTransformer(new CodaMenuTransformer(), false);
            System.out.println("[CodaLoader] Menu initialization hooks registered.");
        } catch (Throwable failure) {
            System.err.println("[CodaLoader] Cannot install menu initialization hooks: " + failure);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] original) {
        if (name == null || !SCREENS.contains(name)) return null;
        try {
            ClassReader reader = new ClassReader(original);
            // No branches are added: preserve stack-map frames and recalculate only maxima.
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] hooks = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                                          String signature, String[] exceptions) {
                    MethodVisitor nativeMethod = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                    if (!METHODS.contains(methodName) || !descriptor.endsWith(")V")
                            || (access & Opcodes.ACC_STATIC) != 0) return nativeMethod;
                    return new MethodVisitor(Opcodes.ASM9, nativeMethod) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/howlingwhispers/codaloader/bootstrap/CodaMenuLifecycle",
                                        "afterInitialize", "(Ljava/lang/Object;)V", false);
                                hooks[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (hooks[0] == 0) {
                System.err.println("[CodaLoader] No menu lifecycle methods found in " + name);
                return null;
            }
            System.out.println("[CodaLoader] Installed " + hooks[0] + " menu lifecycle hooks in " + name);
            return writer.toByteArray();
        } catch (Throwable failure) {
            System.err.println("[CodaLoader] Menu transformation warning for " + name + ": " + failure);
            return null;
        }
    }
}
