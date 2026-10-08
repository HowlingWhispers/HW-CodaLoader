package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds a tail callback to native screen initialization, before the first render.
 * Uses the JDK's bundled ASM reflectively to keep the distribution dependency-free.
 */
public final class CodaMenuTransformer implements ClassFileTransformer {
    private static final Set<String> SCREENS = Set.of(
            "net/minecraft/client/gui/screens/Screen",
            "net/minecraft/client/gui/screens/TitleScreen",
            "net/minecraft/client/gui/screens/PauseScreen");
    private static final Set<String> METHODS = Set.of("init", "rebuildWidgets", "resize", "repositionElements");
    private static final String ASM = "jdk.internal.org.objectweb.asm.";
    private final Class<?> visitor, reader, writer, node, insns, instruction, variable, invocation;

    private CodaMenuTransformer() throws ReflectiveOperationException {
        visitor = Class.forName(ASM + "ClassVisitor");
        reader = Class.forName(ASM + "ClassReader");
        writer = Class.forName(ASM + "ClassWriter");
        node = Class.forName(ASM + "tree.ClassNode");
        insns = Class.forName(ASM + "tree.InsnList");
        instruction = Class.forName(ASM + "tree.AbstractInsnNode");
        variable = Class.forName(ASM + "tree.VarInsnNode");
        invocation = Class.forName(ASM + "tree.MethodInsnNode");
    }

    public static void install(Instrumentation instrumentation) {
        try {
            Module ours = CodaMenuTransformer.class.getModule();
            instrumentation.redefineModule(Object.class.getModule(), Set.of(),
                    Map.of(ASM.substring(0, ASM.length() - 1), Set.of(ours),
                            ASM + "tree", Set.of(ours)), Map.of(), Set.of(), Map.of());
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
            Object tree = node.getConstructor().newInstance();
            reader.getMethod("accept", visitor, int.class).invoke(
                    reader.getConstructor(byte[].class).newInstance((Object) original), tree, 0);
            int hooks = 0;
            for (Object method : (List<?>) node.getField("methods").get(tree)) {
                Class<?> type = method.getClass();
                String methodName = (String) type.getField("name").get(method);
                String descriptor = (String) type.getField("desc").get(method);
                int access = type.getField("access").getInt(method);
                if (!METHODS.contains(methodName) || !descriptor.endsWith(")V") || (access & 8) != 0) continue;
                Object list = type.getField("instructions").get(method);
                for (Object current : (Object[]) insns.getMethod("toArray").invoke(list)) {
                    if ((int) instruction.getMethod("getOpcode").invoke(current) != 177) continue;
                    Object callback = insns.getConstructor().newInstance();
                    insns.getMethod("add", instruction).invoke(callback,
                            variable.getConstructor(int.class, int.class).newInstance(25, 0));
                    insns.getMethod("add", instruction).invoke(callback,
                            invocation.getConstructor(int.class, String.class, String.class, String.class, boolean.class)
                                    .newInstance(184, "dev/howlingwhispers/codaloader/bootstrap/CodaMenuLifecycle",
                                            "afterInitialize", "(Ljava/lang/Object;)V", false));
                    insns.getMethod("insertBefore", instruction, insns).invoke(list, current, callback);
                    hooks++;
                }
            }
            if (hooks == 0) {
                System.err.println("[CodaLoader] No menu lifecycle methods found in " + name);
                return null;
            }
            // No branches are added: existing stack-map frames remain valid.
            Object output = writer.getConstructor(int.class).newInstance(1);
            node.getMethod("accept", visitor).invoke(tree, output);
            System.out.println("[CodaLoader] Installed " + hooks + " menu lifecycle hooks in " + name);
            return (byte[]) writer.getMethod("toByteArray").invoke(output);
        } catch (Throwable failure) {
            System.err.println("[CodaLoader] Menu transformation warning for " + name + ": " + failure);
            return null;
        }
    }
}
