package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.reflect.*;
import java.util.*;

/** Native inventory pages: vanilla page first, twelve mod tabs per page, search/inventory pinned. */
public final class CodaCreativeTabPages {
    private static final IdentityHashMap<Object,Integer> MODS=new IdentityHashMap<>();
    private static final Map<Object,Controls> CONTROLS=new WeakHashMap<>();
    private static int page;
    private record Controls(Object previous,Object next,Object label) {}
    private CodaCreativeTabPages() {}

    static void register(Object tab,int index) { MODS.put(tab,index); }
    static int pageCount() { return 1+(MODS.size()+11)/12; }
    static int pageFor(int index) { return 1+index/12; }
    static int columnFor(int index) { return index%6; }
    static boolean topFor(int index) { return index%12<6; }

    public static List<?> visibleTabs() {
        try {
            Class<?> tabs=Class.forName("net.minecraft.world.item.CreativeModeTabs");
            return visible((List<?>)tabs.getMethod("tabs").invoke(null),page);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Snapshot creative tab list changed",e); }
    }
    static List<?> visible(List<?> all,int requested) throws ReflectiveOperationException {
        var result=new ArrayList<Object>();
        for(Object tab:all) {
            Integer index=MODS.get(tab);
            if(index!=null ? pageFor(index)==requested : requested==0||pinned(tab)) result.add(tab);
        }
        return List.copyOf(result);
    }
    private static boolean pinned(Object tab) throws ReflectiveOperationException {
        String type=tab.getClass().getMethod("getType").invoke(tab).toString();
        return type.equals("SEARCH")||type.equals("INVENTORY");
    }
    public static int visualColumn(Object tab) {
        Integer index=MODS.get(tab);
        if(index!=null)return columnFor(index);
        try { return ((Number)tab.getClass().getMethod("column").invoke(tab)).intValue(); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static Object visualRow(Object tab) {
        try {
            Object row=tab.getClass().getMethod("row").invoke(tab);
            Integer index=MODS.get(tab);
            return index==null?row:row.getClass().getField(topFor(index)?"TOP":"BOTTOM").get(null);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static void selected(Object tab) {
        Integer index=MODS.get(tab);
        try {
            if(index!=null)page=pageFor(index);
            else if(!pinned(tab))page=0;
            for(var entry:List.copyOf(CONTROLS.entrySet())) update(entry.getKey(),entry.getValue());
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Snapshot creative selection changed",e);}
    }
    /** Added only after native CreativeModeInventoryScreen.init returns on its GUI thread. */
    public static void initialize(Object screen) {
        if(MODS.isEmpty())return;
        try {
            Object selected=field(screen.getClass(),"selectedTab").get(null);
            selected(selected);
            int left=field(screen.getClass(),"leftPos").getInt(screen);
            int top=field(screen.getClass(),"topPos").getInt(screen);
            int width=field(screen.getClass(),"imageWidth").getInt(screen);
            int y=Math.max(0,top-49);
            Object previous=button(screen,"<",left+6,y,16,()->shift(screen,-1));
            Object next=button(screen,">",left+width-22,y,16,()->shift(screen,1));
            Object label=button(screen,"",left+width/2-18,y,36,()->{});
            Controls controls=new Controls(previous,next,label);CONTROLS.put(screen,controls);update(screen,controls);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Snapshot creative page controls changed",e);}
    }
    private static Field field(Class<?> cls,String name) throws NoSuchFieldException {
        for(Class<?> c=cls;c!=null;c=c.getSuperclass()) {
            try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        }
        throw new NoSuchFieldException(name);
    }
    public static void removed(Object screen){CONTROLS.remove(screen);}
    private static void shift(Object screen,int delta) {
        int next=Math.max(0,Math.min(pageCount()-1,page+delta));if(next==page)return;
        try {
            page=next;
            Class<?> tabs=Class.forName("net.minecraft.world.item.CreativeModeTabs");
            Object choice=next==0?tabs.getMethod("getDefaultTab").invoke(null)
                :visibleTabs().stream().filter(MODS::containsKey).findFirst().orElseThrow();
            Method select=screen.getClass().getDeclaredMethod("selectTab",Class.forName("net.minecraft.world.item.CreativeModeTab"));
            select.setAccessible(true);select.invoke(screen,choice);
            update(screen,CONTROLS.get(screen));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot change creative page",e);}
    }
    private static Object button(Object screen,String text,int x,int y,int width,Runnable action) throws ReflectiveOperationException {
        Class<?> component=Class.forName("net.minecraft.network.chat.Component");
        Class<?> press=Class.forName("net.minecraft.client.gui.components.Button$OnPress");
        Class<?> button=Class.forName("net.minecraft.client.gui.components.Button");
        Object callback=Proxy.newProxyInstance(press.getClassLoader(),new Class<?>[]{press},(proxy,m,args)->{
            if(m.getName().equals("onPress")){action.run();return null;}
            if(m.getName().equals("hashCode"))return System.identityHashCode(proxy);
            if(m.getName().equals("equals"))return proxy==args[0];
            if(m.getName().equals("toString"))return "H.O.W.L. creative pages";
            throw new UnsupportedOperationException(m.getName());
        });
        Object builder=button.getMethod("builder",component,press).invoke(null,component.getMethod("literal",String.class).invoke(null,text),callback);
        builder=builder.getClass().getMethod("bounds",int.class,int.class,int.class,int.class).invoke(builder,x,y,width,14);
        Object widget=builder.getClass().getMethod("build").invoke(builder);
        Class<?> event=Class.forName("net.minecraft.client.gui.components.events.GuiEventListener");
        Method add=Class.forName("net.minecraft.client.gui.screens.Screen").getDeclaredMethod("addRenderableWidget",event);
        add.setAccessible(true);add.invoke(screen,widget);return widget;
    }
    private static void update(Object screen,Controls c) throws ReflectiveOperationException {
        if(c==null)return;
        field(c.previous.getClass(),"active").setBoolean(c.previous,page>0);
        field(c.next.getClass(),"active").setBoolean(c.next,page<pageCount()-1);
        field(c.label.getClass(),"active").setBoolean(c.label,false);
        Class<?> component=Class.forName("net.minecraft.network.chat.Component");
        Object label=component.getMethod("literal",String.class).invoke(null,(page+1)+"/"+pageCount());
        c.label.getClass().getMethod("setMessage",component).invoke(c.label,label);
    }
}
