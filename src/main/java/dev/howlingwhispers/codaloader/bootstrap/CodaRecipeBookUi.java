package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaRecipeBookCategories;
import java.lang.reflect.*;
import java.util.*;

/**
 * Optional Snapshot 3 recipe-book shortcut rail. Uses existing vanilla search
 * rather than mutating Mojang's recipe categories. Missing client mappings
 * disable the extension without affecting crafting or the recipe book.
 */
public final class CodaRecipeBookUi {
    private static final Map<Object, State> SCREENS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Class<?>> WARNED = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_SUBTABS = 5;
    private record State(Object book, Object parent, List<Object> children, boolean expanded, int page) {}
    private CodaRecipeBookUi() {}

    public static void refresh(Object screen) {
        if (CodaRecipeBookCategories.snapshot().isEmpty()) return;
        if (!screen.getClass().getName().contains("inventory.")) return;
        try {
            Object book = recipeBook(screen);
            if (book == null || !Boolean.TRUE.equals(invoke(book, "isVisible"))) {
                remove(screen); return;
            }
            State existing = SCREENS.get(screen);
            if (existing != null && existing.book == book && widgetsPresent(screen, existing)) return;
            remove(screen);
            Object search = fieldValue(book, "searchBox");
            if (search == null) return;
            int searchX = ((Number)invoke(search, "getX")).intValue();
            int searchY = ((Number)invoke(search, "getY")).intValue();
            int screenHeight = ((Number)fieldValue(screen, "height")).intValue();
            // Under the vanilla category rail. Clamp to the visible screen,
            // with children extending upwards if near the screen bottom.
            int x = Math.max(2, searchX - 66), y = Math.min(screenHeight - 22, searchY + 152);
            Object parent = button(screen, "HW", x, y, 25, () -> toggle(screen));
            State state = new State(book, parent, new ArrayList<>(), false, 0);
            SCREENS.put(screen, state);
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (WARNED.add(screen.getClass()))
                System.err.println("[H.O.W.L.] Recipe-book shortcuts unavailable on " +
                        screen.getClass().getName() + ": " + error);
        }
    }

    private static Object recipeBook(Object screen) throws IllegalAccessException {
        for (Class<?> c=screen.getClass(); c!=null; c=c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.getType().getName().contains("recipebook.")) continue;
                if (!f.getType().getSimpleName().contains("RecipeBookComponent")) continue;
                f.setAccessible(true);
                return f.get(screen);
            }
        }
        return null;
    }

    private static void toggle(Object screen) {
        State state = SCREENS.get(screen);
        if (state == null) return;
        if (state.expanded) { removeChildren(screen, state.children); SCREENS.put(screen,new State(state.book,state.parent,new ArrayList<>(),false,state.page)); return; }
        try {
            int x = ((Number)invoke(state.parent, "getX")).intValue();
            int y = ((Number)invoke(state.parent, "getY")).intValue();
            List<Object> children = new ArrayList<>();
            List<CodaRecipeBookCategories.Category> categories = CodaRecipeBookCategories.snapshot();
            int offset = state.page * MAX_SUBTABS;
            if (offset >= categories.size()) offset = 0;
            int count = Math.min(MAX_SUBTABS, categories.size() - offset);
            // Subtabs form an adjacent flyout, avoiding collisions with vanilla tabs.
            int screenWidth = ((Number)fieldValue(screen,"width")).intValue();
            int flyoutX = x + 27;
            if (flyoutX + 96 > screenWidth) flyoutX = Math.max(2, x - 98);
            for (int i=0; i<count; i++) {
                var category = categories.get(offset+i);
                int yy = Math.max(2, Math.min(y + i*19, ((Number)fieldValue(screen,"height")).intValue()-18));
                children.add(button(screen, abbreviated(category.title()), flyoutX, yy, 94,
                        () -> search(state.book, category)));
            }
            if (categories.size() > count)
                children.add(button(screen, "More >", flyoutX,
                        Math.max(2, y-19), 94, () -> page(screen)));
            SCREENS.put(screen,new State(state.book,state.parent,children,true,state.page));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            remove(screen);
            System.err.println("[H.O.W.L.] Recipe shortcut flyout closed: " + ex);
        }
    }

    private static void page(Object screen) {
        State state = SCREENS.get(screen);
        if (state == null) return;
        removeChildren(screen,state.children);
        int pages=(CodaRecipeBookCategories.snapshot().size()+MAX_SUBTABS-1)/MAX_SUBTABS;
        SCREENS.put(screen,new State(state.book,state.parent,new ArrayList<>(),false,
                (state.page+1)%Math.max(1,pages)));
        toggle(screen);
    }

    private static String abbreviated(String text) {
        return text.length() <= 14 ? text : text.substring(0,13) + "…";
    }

    private static void search(Object book,CodaRecipeBookCategories.Category category) {
        try {
            Object input = fieldValue(book, "searchBox");
            if (input == null) return;
            // Vanilla search index understands display names; this is a
            // search shortcut, not a new native recipe category.
            String query = category.itemIds().stream()
                    .map(id -> id.substring(id.indexOf(':') + 1))
                    .map(id -> id.replace('_',' '))
                    .filter(id -> id.contains("pipe"))
                    .map(id -> "pipe")
                    .findFirst()
                    .orElseGet(() -> category.title().replace("Sorting ", "").replace("H.O.W.L. ", ""));
            invoke(input,"setValue",String.class,query);
        } catch (ReflectiveOperationException e) {
            System.err.println("[H.O.W.L.] Recipe search unavailable: " + e);
        }
    }

    private static Object button(Object screen,String label,int x,int y,int width,Runnable callback)
            throws ReflectiveOperationException {
        Class<?> component=Class.forName("net.minecraft.network.chat.Component");
        Class<?> listener=Class.forName("net.minecraft.client.gui.components.Button$OnPress");
        Class<?> button=Class.forName("net.minecraft.client.gui.components.Button");
        Object handler=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(p,m,args)->{
            if(m.getName().equals("onPress")) { callback.run(); return null; }
            return switch(m.getName()) {
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p==args[0];
                case "toString" -> "HW recipe book";
                default -> throw new UnsupportedOperationException(m.getName());
            };
        });
        Object text=component.getMethod("literal",String.class).invoke(null,label);
        Object builder=button.getMethod("builder",component,listener).invoke(null,text,handler);
        builder=builder.getClass().getMethod("bounds",int.class,int.class,int.class,int.class)
                .invoke(builder,x,y,width,18);
        Object widget=builder.getClass().getMethod("build").invoke(builder);
        Class<?> event=Class.forName("net.minecraft.client.gui.components.events.GuiEventListener");
        Method add=Class.forName("net.minecraft.client.gui.screens.Screen")
                .getDeclaredMethod("addRenderableWidget",event);
        add.setAccessible(true); add.invoke(screen,widget);
        return widget;
    }

    private static Object fieldValue(Object object,String name) throws IllegalAccessException,NoSuchFieldException {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object); }
            catch(NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object object,String method) throws ReflectiveOperationException {
        return object.getClass().getMethod(method).invoke(object);
    }
    private static Object invoke(Object object,String method,Class<?> type,Object arg) throws ReflectiveOperationException {
        return object.getClass().getMethod(method,type).invoke(object,arg);
    }
    private static boolean widgetsPresent(Object screen,State state) {
        try {
            Object children=invoke(screen,"children");
            if (!(children instanceof Collection<?> list)) return false;
            return list.contains(state.parent) && list.containsAll(state.children);
        } catch(ReflectiveOperationException e) {return false;}
    }
    private static void removeChildren(Object screen,List<Object> widgets) {
        for(Object widget:widgets) {
            try {
                Object children=invoke(screen,"children");
                if(children instanceof Collection<?> list) list.remove(widget);
                Object renderables=fieldValue(screen,"renderables");
                if(renderables instanceof Collection<?> list) list.remove(widget);
                Object narratables=fieldValue(screen,"narratables");
                if(narratables instanceof Collection<?> list) list.remove(widget);
            } catch(ReflectiveOperationException | UnsupportedOperationException ignored) {}
        }
    }
    private static void remove(Object screen) {
        State state=SCREENS.remove(screen);
        if(state!=null) {
            removeChildren(screen,state.children);
            removeChildren(screen,List.of(state.parent));
        }
    }
}
