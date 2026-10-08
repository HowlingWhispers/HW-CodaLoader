package dev.howlingwhispers.codaloader.bootstrap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Real production ASM transformer against synthetic classfiles. */
public final class NativeRegistryTransformerTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static byte[] fixture(String name, String method, String descriptor,
                                  int returnOpcode) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor m = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                method, descriptor, null, null);
        m.visitCode();
        if (returnOpcode == Opcodes.ARETURN) m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(returnOpcode);
        m.visitMaxs(1, 1);
        m.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void verify(String name, String method, String descriptor,
                               int opcode, String bridgeMethod) throws Exception {
        CodaNativeRegistryTransformer t = new CodaNativeRegistryTransformer();
        byte[] result = t.transform(null, name, null, null,
                fixture(name, method, descriptor, opcode));
        check(result != null, name + " must be hooked");
        int[] hits = {0};
        new ClassReader(result).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String methodName,
                    String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner,
                            String called, String callDesc, boolean itf) {
                        if (owner.equals("dev/howlingwhispers/codaloader/bootstrap/CodaNativeRegistryBridge")
                                && called.equals(bridgeMethod)) hits[0]++;
                    }
                };
            }
        }, 0);
        check(hits[0] == 1, "Exactly one native bootstrap hook: " + bridgeMethod);
        byte[] wrong = fixture(name, method, "(I)V", Opcodes.RETURN);
        check(t.transform(null, name, null, null, wrong) == null,
                "Unknown Minecraft mapping must fail closed");
    }
    public static void main(String[] args) throws Exception {
        verify("net/minecraft/world/level/block/Blocks", "<clinit>", "()V",
                Opcodes.RETURN, "registerBlocksAndItems");
        // Explicitly refuse the historically wrong Items.<clinit> hook:
        // Mojang starts Items during Blocks initialization, before Blocks
        // is complete, so registering there previously crashed the real game.
        CodaNativeRegistryTransformer transformer = new CodaNativeRegistryTransformer();
        check(transformer.transform(null, "net/minecraft/world/item/Items", null, null,
                fixture("net/minecraft/world/item/Items", "<clinit>", "()V", Opcodes.RETURN)) == null,
                "Items phase must not inject premature registrations");
        verify("net/minecraft/world/item/CreativeModeTabs", "bootstrap",
                "(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/item/CreativeModeTab;",
                Opcodes.ARETURN, "registerCreativeTabs");
        System.out.println("PASS: " + checks + " native Minecraft registry bytecode hook checks");
    }
}
