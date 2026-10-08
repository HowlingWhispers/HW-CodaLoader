package dev.howlingwhispers.codawolf;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Fail-closed named-mapping reflection for a separate mod, not loader internals. */
final class NativeCalls {
    private NativeCalls() {}
    static Class<?> type(String name, ClassLoader loader) throws Exception { return Class.forName(name, true, loader); }
    static Object field(Class<?> owner, String name) throws Exception { return owner.getField(name).get(null); }
    static boolean fits(Class<?> param, Object argument) {
        if (argument == null) return !param.isPrimitive();
        if (param.isPrimitive()) {
            return (param == int.class && argument instanceof Integer)
                    || (param == boolean.class && argument instanceof Boolean)
                    || (param == float.class && argument instanceof Float)
                    || (param == double.class && argument instanceof Double)
                    || (param == long.class && argument instanceof Long);
        }
        return param.isInstance(argument);
    }
    static Object call(Object receiver, String name, Object... args) throws Exception {
        Class<?> klass = receiver instanceof Class<?> c ? c : receiver.getClass();
        Method found = null;
        for (Method candidate : klass.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) continue;
            if (receiver instanceof Class<?> && !Modifier.isStatic(candidate.getModifiers())) continue;
            boolean matches = true;
            for (int i = 0; i < args.length; i++) if (!fits(candidate.getParameterTypes()[i], args[i])) { matches = false; break; }
            if (matches) { found = candidate; break; }
        }
        if (found == null) throw new NoSuchMethodException(klass.getName() + "." + name + "/" + args.length);
        try {
            return found.invoke(receiver instanceof Class<?> ? null : receiver, args);
        } catch (InvocationTargetException failure) {
            Throwable original = failure.getCause();
            if (original instanceof Exception exception) throw exception;
            if (original instanceof Error error) throw error;
            throw failure;
        }
    }
    static Object construct(Class<?> klass, Object... args) throws Exception {
        for (Constructor<?> c : klass.getConstructors()) {
            if (c.getParameterCount() != args.length) continue;
            boolean fits = true;
            for (int i=0;i<args.length;i++) if (!fits(c.getParameterTypes()[i],args[i])) { fits=false; break; }
            if (fits) return c.newInstance(args);
        }
        throw new NoSuchMethodException("Public constructor unavailable for " + klass.getName());
    }
}
