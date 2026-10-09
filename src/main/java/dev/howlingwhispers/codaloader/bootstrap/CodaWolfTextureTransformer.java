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
 * Exact Minecraft 26.4 Snapshot 3 wolf texture seam.
 *
 * Vanilla WolfRenderer.extractRenderState reads Wolf.getTexture() into
 * WolfRenderState.texture. This hook changes that ONE field after vanilla
 * extraction only for wolves carrying Coda's persistent entity tag. It never
 * changes vanilla registries, wolf AI, or textures for unrelated wolves.
 */
public final class CodaWolfTextureTransformer implements ClassFileTransformer {
    private static final String RENDERER =
            "net/minecraft/client/renderer/entity/WolfRenderer";
    private static final String METHOD = "extractRenderState";
    private static final String DESC =
            "(Lnet/minecraft/world/entity/animal/wolf/Wolf;"
          + "Lnet/minecraft/client/renderer/entity/state/WolfRenderState;F)V";

    private CodaWolfTextureTransformer() {}

    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaWolfTextureTransformer(), false);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytes) {
        if (!RENDERER.equals(className)) return null;
        try {
            int[] matching = {0}, hooks = {0};
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name,
                        String desc, String signature, String[] exceptions) {
                    MethodVisitor original = super.visitMethod(access,name,desc,signature,exceptions);
                    if (!METHOD.equals(name) || !DESC.equals(desc)) return original;
                    matching[0]++;
                    return new MethodVisitor(Opcodes.ASM9,original) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD,1);
                                super.visitVarInsn(Opcodes.ALOAD,2);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "dev/howlingwhispers/codaloader/bootstrap/CodaWolfClientTextures",
                                    "select", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
                                hooks[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
            },0);
            if (matching[0] != 1 || hooks[0] != 1) {
                System.err.println("[H.O.W.L.] Refusing mismatched Snapshot 3 wolf renderer seam");
                return null;
            }
            System.out.println("[H.O.W.L.] Coda-only wolf skin renderer hook installed.");
            return writer.toByteArray();
        } catch (RuntimeException ex) {
            System.err.println("[H.O.W.L.] Wolf renderer seam unavailable: "+ex);
            return null;
        }
    }
}
