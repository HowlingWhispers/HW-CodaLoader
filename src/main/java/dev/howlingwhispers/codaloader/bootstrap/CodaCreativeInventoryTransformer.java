package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Correct the inclusive sprite-array upper bound in Snapshot 3's tab renderer. */
public final class CodaCreativeInventoryTransformer implements ClassFileTransformer {
    static final String SCREEN = "net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen";

    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaCreativeInventoryTransformer(), false);
    }

    @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                      ProtectionDomain protection, byte[] bytes) {
        if (!SCREEN.equals(name)) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            int[] methods = {0}, patches = {0};
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String method,
                        String desc, String signature, String[] exceptions) {
                    MethodVisitor original = super.visitMethod(access, method, desc, signature, exceptions);
                    Type[] args = Type.getArgumentTypes(desc);
                    if (!method.equals("extractTabButton") || args.length != 2
                            || !args[1].getDescriptor().equals("Lnet/minecraft/world/item/CreativeModeTab;")
                            || Type.getReturnType(desc).getSort() != Type.VOID) return original;
                    methods[0]++;
                    return new MethodVisitor(Opcodes.ASM9, original) {
                        private boolean arrayLength;
                        @Override public void visitInsn(int opcode) {
                            arrayLength = opcode == Opcodes.ARRAYLENGTH;
                            super.visitInsn(opcode);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                               String descriptor, boolean itf) {
                            if (arrayLength && opcode == Opcodes.INVOKESTATIC
                                    && owner.equals("net/minecraft/util/Mth")
                                    && called.equals("clamp") && descriptor.equals("(III)I")) {
                                // Stack: index, 0, sprites.length. Mth.clamp's
                                // maximum is inclusive; the last legal index
                                // is length - 1. Tab position stays unchanged.
                                super.visitInsn(Opcodes.ICONST_1);
                                super.visitInsn(Opcodes.ISUB);
                                patches[0]++;
                            }
                            arrayLength = false;
                            super.visitMethodInsn(opcode, owner, called, descriptor, itf);
                        }
                        @Override public void visitVarInsn(int op, int var) { arrayLength = false; super.visitVarInsn(op, var); }
                        @Override public void visitIntInsn(int op, int value) { arrayLength = false; super.visitIntInsn(op, value); }
                        @Override public void visitTypeInsn(int op, String type) { arrayLength = false; super.visitTypeInsn(op, type); }
                        @Override public void visitFieldInsn(int op, String owner, String field, String desc) { arrayLength = false; super.visitFieldInsn(op, owner, field, desc); }
                        @Override public void visitJumpInsn(int op, Label label) { arrayLength = false; super.visitJumpInsn(op, label); }
                        @Override public void visitLdcInsn(Object value) { arrayLength = false; super.visitLdcInsn(value); }
                        @Override public void visitIincInsn(int var, int increment) { arrayLength = false; super.visitIincInsn(var, increment); }
                        @Override public void visitInvokeDynamicInsn(String name, String desc, Handle handle, Object... args) { arrayLength = false; super.visitInvokeDynamicInsn(name, desc, handle, args); }
                        @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { arrayLength = false; super.visitTableSwitchInsn(min, max, dflt, labels); }
                        @Override public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) { arrayLength = false; super.visitLookupSwitchInsn(dflt, keys, labels); }
                        @Override public void visitMultiANewArrayInsn(String desc, int dims) { arrayLength = false; super.visitMultiANewArrayInsn(desc, dims); }
                    };
                }
            }, 0);
            if (methods[0] != 1 || patches[0] != 1) {
                System.err.println("[H.O.W.L.] Creative tab sprite seam missing/changed; refusing patch.");
                return null;
            }
            System.out.println("[H.O.W.L.] Creative tab sprite bounds corrected.");
            return writer.toByteArray();
        } catch (RuntimeException error) {
            System.err.println("[H.O.W.L.] Creative tab sprite hook refused: " + error);
            return null;
        }
    }
}
