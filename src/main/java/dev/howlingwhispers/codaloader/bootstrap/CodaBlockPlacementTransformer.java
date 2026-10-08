package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Snapshot 3 native BlockItem placement events for BuildCraft engines. */
public final class CodaBlockPlacementTransformer implements ClassFileTransformer {
    private static final String TARGET = "net/minecraft/world/item/BlockItem";
    private static final String METHOD = "place";
    private static final String DESC =
            "(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;";

    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaBlockPlacementTransformer(), false);
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] original) {
        if (!TARGET.equals(name)) return null;
        try {
            int[] count = {0};
            ClassReader reader = new ClassReader(original);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method,
                        String desc, String signature, String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, method, desc, signature, exceptions);
                    if (!METHOD.equals(method) || !DESC.equals(desc)) return mv;
                    count[0]++;
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ARETURN) {
                                // stack: [result] -> [result, duplicate-result,item,context]
                                super.visitInsn(Opcodes.DUP);
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/howlingwhispers/codaloader/bootstrap/CodaBlockPlacementLifecycle",
                                        "afterPlacement",
                                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                                        false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            }, 0);
            if (count[0] != 1) {
                System.err.println("[H.O.W.L.] Native placement mapping changed; placement listeners disabled");
                return null;
            }
            System.out.println("[H.O.W.L.] Native BlockItem placement callbacks installed.");
            return writer.toByteArray();
        } catch (Throwable ex) {
            System.err.println("[H.O.W.L.] BlockItem placement transformer unavailable: " + ex);
            return null;
        }
    }
}
