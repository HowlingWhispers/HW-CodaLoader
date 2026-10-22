package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Named Snapshot 3 adapter: absent or changed methods fail explicitly, never mutate off-thread. */
final class CommandReflection {
    private CommandReflection() {}
    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null ? parameters[i].isPrimitive() : !boxed(parameters[i]).isInstance(args[i])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            try {
                Object receiver = target instanceof Class<?> ? null : target;
                // Native public API implementations can be anonymous classes (e.g. Mob's equipment Container).
                // Keep exact public method selection; merely make that implementation callable.
                if (!method.canAccess(receiver) && !method.trySetAccessible())
                    throw new IllegalAccessException("Native public API method is inaccessible: " + method);
                return method.invoke(receiver, args);
            }
            catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof Exception cause) throw cause;
                throw ex;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " (" + args.length + " arguments)");
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == long.class) return Long.class;
        return type;
    }
}
