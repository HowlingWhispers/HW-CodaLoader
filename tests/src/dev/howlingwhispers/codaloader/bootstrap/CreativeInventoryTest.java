package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/** Runs transformed bytecode, including the player's column-7 crash. */
public final class CreativeInventoryTest {
    private static int checks;
    private static final String TAB = "net/minecraft/world/item/CreativeModeTab";
    private static final String MTH = "net/minecraft/util/Mth";
    private static final String DESC = "(Ljava/lang/Object;L" + TAB + ";)V";
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static byte[] simpleClass(String name) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (name.equals(TAB)) {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            m.visitCode(); m.visitVarInsn(Opcodes.ALOAD, 0);
            m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        } else {
            MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "clamp", "(III)I", null, null);
            m.visitCode(); m.visitVarInsn(Opcodes.ILOAD, 1); m.visitVarInsn(Opcodes.ILOAD, 0);
            m.visitVarInsn(Opcodes.ILOAD, 2);
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I", false);
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(II)I", false);
            m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0,0); m.visitEnd();
        }
        w.visitEnd(); return w.toByteArray();
    }
    private static byte[] screen(String method, boolean corrected) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CodaCreativeInventoryTransformer.SCREEN,
                null, "java/lang/Object", null);
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "column", "I", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "sprites", "[Ljava/lang/String;", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "selectedSprite", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method, DESC, null, null);
        m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC, CodaCreativeInventoryTransformer.SCREEN, "sprites", "[Ljava/lang/String;");
        m.visitFieldInsn(Opcodes.GETSTATIC, CodaCreativeInventoryTransformer.SCREEN, "column", "I");
        m.visitInsn(Opcodes.ICONST_0);
        m.visitFieldInsn(Opcodes.GETSTATIC, CodaCreativeInventoryTransformer.SCREEN, "sprites", "[Ljava/lang/String;");
        m.visitInsn(Opcodes.ARRAYLENGTH);
        if (corrected) { m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.ISUB); }
        m.visitMethodInsn(Opcodes.INVOKESTATIC, MTH, "clamp", "(III)I", false);
        m.visitInsn(Opcodes.AALOAD);
        m.visitFieldInsn(Opcodes.PUTSTATIC, CodaCreativeInventoryTransformer.SCREEN, "selectedSprite", "Ljava/lang/String;");
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0,0); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }
    private static Class<?> loadScreen(byte[] bytes) {
        class FixtureLoader extends ClassLoader {
            Class<?> define(String name, byte[] data) { return defineClass(name.replace('/', '.'), data, 0, data.length); }
        }
        FixtureLoader loader = new FixtureLoader();
        loader.define(TAB, simpleClass(TAB)); loader.define(MTH, simpleClass(MTH));
        return loader.define(CodaCreativeInventoryTransformer.SCREEN, bytes);
    }
    private static void draw(Class<?> screen, int column) throws Exception {
        screen.getField("column").set(null, column);
        screen.getMethod("extractTabButton", Object.class,
                screen.getClassLoader().loadClass(TAB.replace('/', '.'))).invoke(null, null, null);
    }
    public static void main(String[] args) throws Exception {
        CodaCreativeInventoryTransformer transformer = new CodaCreativeInventoryTransformer();
        byte[] original = screen("extractTabButton", false);
        Class<?> broken = loadScreen(original);
        broken.getField("sprites").set(null, new String[]{"0","1","2","3","4","5","6"});
        try { draw(broken, 7); throw new AssertionError("Expected reported column-7 crash"); }
        catch (InvocationTargetException ex) { check(ex.getCause() instanceof ArrayIndexOutOfBoundsException, "reproduces reported rendering crash"); }
        byte[] fixed = transformer.transform(null, CodaCreativeInventoryTransformer.SCREEN, null, null, original);
        check(fixed != null, "production transformer recognizes sprite boundary");
        Class<?> safe = loadScreen(fixed);
        for (String state : new String[]{"top-selected", "top-unselected", "bottom-selected", "bottom-unselected"}) {
            String[] sprites = new String[7];
            for (int i=0; i<7; i++) sprites[i] = state + i;
            safe.getField("sprites").set(null, sprites);
            for (int column=0; column<10; column++) {
                draw(safe, column);
                check(safe.getField("selectedSprite").get(null).equals(sprites[Math.min(column,6)]), "correct sprite for " + state + " column " + column);
                check(safe.getField("column").getInt(null) == column, "tab position preserved");
            }
            draw(safe, -1);
            check(safe.getField("selectedSprite").get(null).equals(sprites[0]), "negative index remains clamped");
        }
        check(transformer.transform(null, "other/Screen", null, null, original) == null, "unrelated screen untouched");
        check(transformer.transform(null, CodaCreativeInventoryTransformer.SCREEN, null, null, screen("changedMethod",false)) == null, "changed method refused");
        check(transformer.transform(null, CodaCreativeInventoryTransformer.SCREEN, null, null, fixed) == null, "already fixed bytecode untouched");
        if (args.length > 0) {
            try (JarFile client = new JarFile(Path.of(args[0]).toFile())) {
                byte[] actual = client.getInputStream(client.getJarEntry(CodaCreativeInventoryTransformer.SCREEN + ".class")).readAllBytes();
                StringBuilder mapping = new StringBuilder();
                new ClassReader(actual).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        if (!name.equals("extractTabButton")) return null;
                        mapping.append(name).append(desc).append("; ");
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitInsn(int opcode) {
                                if (opcode == Opcodes.ARRAYLENGTH) mapping.append("ARRAYLENGTH; ");
                                if (opcode == Opcodes.AALOAD) mapping.append("AALOAD; ");
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String name,
                                    String desc, boolean itf) {
                                if (name.equals("clamp") || name.equals("column"))
                                    mapping.append(owner).append('.').append(name).append(desc).append("; ");
                            }
                        };
                    }
                }, 0);
                check(transformer.transform(null, CodaCreativeInventoryTransformer.SCREEN, null, null, actual) != null,
                        "EXACT Mojang Snapshot 3 renderer not recognized: " + mapping);
            }
        }
        System.out.println("PASS: " + checks + " creative inventory rendering checks");
    }
}
