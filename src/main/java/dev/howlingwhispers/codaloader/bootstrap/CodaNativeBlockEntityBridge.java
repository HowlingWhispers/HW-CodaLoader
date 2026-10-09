package dev.howlingwhispers.codaloader.bootstrap;

import dev.howlingwhispers.codaloader.api.CodaNativeContents;
import dev.howlingwhispers.codaloader.api.CodaBlockEntityTick;
import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Snapshot 3's native EntityBlock/BlockEntityType compatibility boundary.
 *
 * BuildCraft's original BlockPipeHolder/TilePipeHolder own pipe behavior.
 * This bootstrap-only adapter provides a real vanilla BlockEntity identity
 * for native blocks until their original lifecycle, NBT and ticking ports
 * can be connected. No item inventory, MJ store or synthetic pipe flow is
 * created here. It refuses missing/changed Mojang signatures.
 */
public final class CodaNativeBlockEntityBridge implements Opcodes {
    private static final String PACKAGE = "dev/howlingwhispers/codaloader/bootstrap/";
    private static final String BLOCK = "net/minecraft/world/level/block/Block";
    private static final String PIPE_BLOCK = "net/minecraft/world/level/block/PipeBlock";
    private static final String PROPERTIES =
            "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
    private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String POS = "net/minecraft/core/BlockPos";
    private static final String ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
    private static final String TYPE = "net/minecraft/world/level/block/entity/BlockEntityType";
    private static final String ENTITY_BLOCK = "net/minecraft/world/level/block/EntityBlock";
    private static final String TICKER = "net/minecraft/world/level/block/entity/BlockEntityTicker";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String BLOCK_CLASS = PACKAGE + "HowlNativeEntityBlock";
    private static final String PIPE_BLOCK_CLASS = PACKAGE + "HowlNativePipeEntityBlock";
    private static final String ENTITY_CLASS = PACKAGE + "HowlNativeBlockEntity";

    private static final Map<Object, Object> BLOCK_TYPES = new IdentityHashMap<>();
    private static final Map<Object, String> TYPE_IDS = new IdentityHashMap<>();
    private static volatile Class<?> nativeBlock;
    private static volatile Class<?> nativePipeBlock;
    private static volatile Constructor<?> nativeEntityCtor;
    private static boolean registered;

    private CodaNativeBlockEntityBridge() {}

    private static Class<?> minecraft(String name) throws ClassNotFoundException {
        return Class.forName(name.replace('/', '.'), true,
                CodaNativeBlockEntityBridge.class.getClassLoader());
    }

    /**
     * Bytecode is generated solely because H.O.W.L.'s SDK compiles without
     * Minecraft in its classpath. The generated class is a real vanilla
     * Block implementing Mojang's EntityBlock interface.
     */
    public static synchronized Class<?> entityBlockClass() {
        if (nativeBlock != null) return nativeBlock;
        try {
            minecraft(BLOCK);
            minecraft(ENTITY_BLOCK);
            minecraft(ENTITY);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V17, ACC_PUBLIC | ACC_FINAL, BLOCK_CLASS, null,
                    BLOCK, new String[]{ENTITY_BLOCK});
            MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>",
                    "(L" + PROPERTIES + ";)V", null, null);
            ctor.visitCode();
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 1);
            ctor.visitMethodInsn(INVOKESPECIAL, BLOCK, "<init>",
                    "(L" + PROPERTIES + ";)V", false);
            ctor.visitInsn(RETURN);
            ctor.visitMaxs(0, 0);
            ctor.visitEnd();

            MethodVisitor factory = writer.visitMethod(ACC_PUBLIC, "newBlockEntity",
                    "(L" + POS + ";L" + STATE + ";)L" + ENTITY + ";", null, null);
            factory.visitCode();
            factory.visitVarInsn(ALOAD, 1);
            factory.visitVarInsn(ALOAD, 2);
            factory.visitMethodInsn(INVOKESTATIC, PACKAGE + "CodaNativeBlockEntityBridge",
                    "create", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            factory.visitTypeInsn(CHECKCAST, ENTITY);
            factory.visitInsn(ARETURN);
            factory.visitMaxs(0, 0);
            factory.visitEnd();

            // Mojang's EntityBlock.getTicker drives pipe updates only while
            // Minecraft owns a loaded server-side block entity. Do not run a
            // global polling loop or synthesize travelling items.
            MethodVisitor ticker = writer.visitMethod(ACC_PUBLIC, "getTicker",
                    "(L" + LEVEL + ";L" + STATE + ";L" + TYPE + ";)L" + TICKER + ";",
                    null, null);
            ticker.visitCode();
            ticker.visitVarInsn(ALOAD, 1);
            ticker.visitVarInsn(ALOAD, 2);
            ticker.visitVarInsn(ALOAD, 3);
            ticker.visitMethodInsn(INVOKESTATIC, PACKAGE + "CodaNativeBlockEntityBridge",
                    "createTicker",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    false);
            ticker.visitTypeInsn(CHECKCAST, TICKER);
            ticker.visitInsn(ARETURN);
            ticker.visitMaxs(0, 0);
            ticker.visitEnd();

            writer.visitEnd();
            nativeBlock = MethodHandles.lookup().defineClass(writer.toByteArray());
            return nativeBlock;
        } catch (Throwable error) {
            throw new IllegalStateException("Snapshot 3 native EntityBlock class unavailable", error);
        }
    }

    public static synchronized Class<?> pipeEntityBlockClass() {
        if (nativePipeBlock != null) return nativePipeBlock;
        try {
            minecraft(PIPE_BLOCK);
            minecraft(ENTITY_BLOCK);
            minecraft(ENTITY);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V17, ACC_PUBLIC | ACC_FINAL, PIPE_BLOCK_CLASS, null,
                    PIPE_BLOCK, new String[]{ENTITY_BLOCK});
            MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>",
                    "(L" + PROPERTIES + ";)V", null, null);
            ctor.visitCode();
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitLdcInsn(0.1875f);
            ctor.visitVarInsn(ALOAD, 1);
            ctor.visitMethodInsn(INVOKESPECIAL, PIPE_BLOCK, "<init>",
                    "(FL" + PROPERTIES + ";)V", false);
            ctor.visitInsn(RETURN);
            ctor.visitMaxs(0, 0);
            ctor.visitEnd();

            MethodVisitor factory = writer.visitMethod(ACC_PUBLIC, "newBlockEntity",
                    "(L" + POS + ";L" + STATE + ";)L" + ENTITY + ";", null, null);
            factory.visitCode();
            factory.visitVarInsn(ALOAD, 1);
            factory.visitVarInsn(ALOAD, 2);
            factory.visitMethodInsn(INVOKESTATIC, PACKAGE + "CodaNativeBlockEntityBridge",
                    "create", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            factory.visitTypeInsn(CHECKCAST, ENTITY);
            factory.visitInsn(ARETURN);
            factory.visitMaxs(0, 0);
            factory.visitEnd();

            // Mojang's EntityBlock.getTicker drives pipe updates only while
            // Minecraft owns a loaded server-side block entity. Do not run a
            // global polling loop or synthesize travelling items.
            MethodVisitor ticker = writer.visitMethod(ACC_PUBLIC, "getTicker",
                    "(L" + LEVEL + ";L" + STATE + ";L" + TYPE + ";)L" + TICKER + ";",
                    null, null);
            ticker.visitCode();
            ticker.visitVarInsn(ALOAD, 1);
            ticker.visitVarInsn(ALOAD, 2);
            ticker.visitVarInsn(ALOAD, 3);
            ticker.visitMethodInsn(INVOKESTATIC, PACKAGE + "CodaNativeBlockEntityBridge",
                    "createTicker",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    false);
            ticker.visitTypeInsn(CHECKCAST, TICKER);
            ticker.visitInsn(ARETURN);
            ticker.visitMaxs(0, 0);
            ticker.visitEnd();

            writer.visitEnd();
            nativePipeBlock = MethodHandles.lookup().defineClass(writer.toByteArray());
            return nativePipeBlock;
        } catch (Throwable error) {
            throw new IllegalStateException("Snapshot 3 native EntityBlock class unavailable", error);
        }
    }

    private static synchronized Constructor<?> entityConstructor() {
        if (nativeEntityCtor != null) return nativeEntityCtor;
        try {
            minecraft(ENTITY);
            minecraft(TYPE);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V17, ACC_PUBLIC | ACC_FINAL, ENTITY_CLASS, null, ENTITY, null);
            MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>",
                    "(L" + POS + ";L" + STATE + ";)V", null, null);
            ctor.visitCode();
            ctor.visitVarInsn(ALOAD, 0);
            ctor.visitVarInsn(ALOAD, 2);
            ctor.visitMethodInsn(INVOKESTATIC, PACKAGE + "CodaNativeBlockEntityBridge",
                    "typeForState", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            ctor.visitTypeInsn(CHECKCAST, TYPE);
            ctor.visitVarInsn(ALOAD, 1);
            ctor.visitVarInsn(ALOAD, 2);
            ctor.visitMethodInsn(INVOKESPECIAL, ENTITY, "<init>",
                    "(L" + TYPE + ";L" + POS + ";L" + STATE + ";)V", false);
            ctor.visitInsn(RETURN);
            ctor.visitMaxs(0, 0);
            ctor.visitEnd();
            writer.visitEnd();
            Class<?> clazz = MethodHandles.lookup().defineClass(writer.toByteArray());
            nativeEntityCtor = clazz.getConstructor(minecraft(POS), minecraft(STATE));
            return nativeEntityCtor;
        } catch (Throwable error) {
            throw new IllegalStateException("Snapshot 3 native BlockEntity class unavailable", error);
        }
    }

    /** Called only after a block registry entry and its native type exist. */
    public static synchronized Object typeForState(Object blockState) {
        try {
            Object block = blockState.getClass().getMethod("getBlock").invoke(blockState);
            Object type = BLOCK_TYPES.get(block);
            if (type == null)
                throw new IllegalStateException("Unregistered H.O.W.L. BlockEntity block");
            return type;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Minecraft block-state mapping changed", error);
        }
    }

    /**
     * Give Minecraft a real BlockEntityTicker for a registered pipe-holder type.
     * Only native ticker invocations dispatch an event. No unplaced or
     * unloaded nodes are fabricated, and client calls do nothing.
     */
    public static synchronized Object createTicker(Object level, Object state, Object nativeType) {
        if (state == null || nativeType == null) return null;
        try {
            Object requiredType = typeForState(state);
            if (requiredType != nativeType) return null;
            String typeId = TYPE_IDS.get(nativeType);
            if (typeId == null || !CodaNativeContents.hasBlockEntityTick(typeId))
                return null;
            Class<?> ticker = minecraft(TICKER);
            return Proxy.newProxyInstance(ticker.getClassLoader(),
                    new Class<?>[]{ticker}, (proxy, method, args) -> {
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("toString")) return "H.O.W.L. native ticker " + typeId;
                        if (!method.getName().equals("tick") || args == null || args.length != 4)
                            throw new IllegalStateException("Unknown native BlockEntityTicker invocation");
                        Object world = args[0], pos = args[1], stateAtPos = args[2], entity = args[3];
                        if (world == null || pos == null || stateAtPos == null || entity == null)
                            throw new IllegalStateException("Minecraft native ticker missing world context");
                        if ((Boolean)world.getClass().getMethod("isClientSide").invoke(world))
                            return null;
                        if (entity.getClass().getMethod("getType").invoke(entity) != nativeType)
                            throw new IllegalStateException("Native BuildCraft pipe entity type mismatch");
                        if ((Boolean)entity.getClass().getMethod("isRemoved").invoke(entity))
                            return null;
                        if (typeForState(stateAtPos) != nativeType)
                            throw new IllegalStateException("Native BuildCraft block changed during tick");
                        Object dimension = world.getClass().getMethod("dimension").invoke(world);
                        String dimensionId = dimension.getClass().getMethod("identifier")
                                .invoke(dimension).toString();
                        CodaBlockPos position = new CodaBlockPos(
                                ((Number)pos.getClass().getMethod("getX").invoke(pos)).intValue(),
                                ((Number)pos.getClass().getMethod("getY").invoke(pos)).intValue(),
                                ((Number)pos.getClass().getMethod("getZ").invoke(pos)).intValue());
                        if (nativePipeBlock != null && nativePipeBlock.isInstance(
                                stateAtPos.getClass().getMethod("getBlock").invoke(stateAtPos))) {
                            refreshPipeConnections(world, pos, stateAtPos);
                        }
                        CodaNativeContents.dispatchBlockEntityTick(
                                new CodaBlockEntityTick(typeId, dimensionId, position));
                        return null;
                    });
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Native Minecraft pipe ticker mapping unavailable", ex);
        }
    }


    /**
     * Reflect real connected faces into PipeBlock's six vanilla BooleanProperty
     * blockstates. Only loaded adjacent blocks are inspected. Client multipart
     * rendering follows ordinary server-synced vanilla block states.
     */
    private static void refreshPipeConnections(Object world, Object pos, Object state)
            throws ReflectiveOperationException {
        Class<?> posType = minecraft(POS), stateType = minecraft(STATE);
        Class<?> propType = minecraft("net/minecraft/world/level/block/state/properties/Property");
        Class<?> properties = minecraft("net/minecraft/world/level/block/state/properties/BlockStateProperties");
        Object registry = CodaNativeRegistryBridge.registry("BLOCK");
        String[] faces = {"EAST","WEST","UP","DOWN","SOUTH","NORTH"};
        int[][] offsets = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        int x=((Number)posType.getMethod("getX").invoke(pos)).intValue();
        int y=((Number)posType.getMethod("getY").invoke(pos)).intValue();
        int z=((Number)posType.getMethod("getZ").invoke(pos)).intValue();
        Object updated = state;
        boolean dirty = false;
        for (int i=0;i<faces.length;i++) {
            Object neighbor = posType.getConstructor(int.class,int.class,int.class)
                    .newInstance(x+offsets[i][0],y+offsets[i][1],z+offsets[i][2]);
            boolean connected = false;
            // Never call getBlockState on an unloaded chunk.
            if ((Boolean)world.getClass().getMethod("hasChunkAt",posType).invoke(world,neighbor)) {
                Object neighborState = world.getClass().getMethod("getBlockState",posType)
                        .invoke(world,neighbor);
                Object block = stateType.getMethod("getBlock").invoke(neighborState);
                String id=registry.getClass().getMethod("getKey",Object.class)
                        .invoke(registry,block).toString();
                connected = id.equals("hw_buildcraft_lite:wooden_transport_pipe")
                        || id.equals("hw_buildcraft_lite:stone_transport_pipe")
                        || id.equals("hw_buildcraft_lite:redstone_engine")
                        || id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest")
                        || id.equals("minecraft:barrel") || id.equals("minecraft:hopper");
            }
            Object prop = properties.getField(faces[i]).get(null);
            boolean previous=(Boolean)stateType.getMethod("getValue",propType).invoke(updated,prop);
            if (previous!=connected) {
                updated=stateType.getMethod("setValue",propType,Comparable.class)
                        .invoke(updated,prop,Boolean.valueOf(connected));
                dirty=true;
            }
        }
        if (dirty) {
            // Flag 2 synchronizes the same block's updated visual state to clients.
            world.getClass().getMethod("setBlock",posType,stateType,int.class)
                    .invoke(world,pos,updated,2);
        }
    }

    /** Native EntityBlock.newBlockEntity delegate, not a virtual pipe model. */
    public static Object create(Object pos, Object state) {
        try {
            typeForState(state); // Do not create a tile for an unknown block.
            return entityConstructor().newInstance(pos, state);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native H.O.W.L. block entity creation failed", error);
        }
    }

    /**
     * Register exact Mojang BlockEntityType objects before registry freeze.
     * The BCCE type ID is shared by the original wooden and cobblestone
     * BlockPipeHolder objects, matching the upstream pipe-holder model.
     */
    public static synchronized void registerTypes(Map<String, Object> nativeBlocks) {
        if (registered) return;
        if (CodaNativeContents.blockEntityTypes().isEmpty()) {
            registered = true;
            return;
        }
        try {
            Object registry = CodaNativeRegistryBridge.registry("BLOCK_ENTITY_TYPE");
            Class<?> supplier = minecraft(TYPE + "$BlockEntitySupplier");
            Class<?> type = minecraft(TYPE);
            for (CodaNativeContents.BlockEntityDefinition definition
                    : CodaNativeContents.blockEntityTypes()) {
                CodaNativeRegistryBridge.requireFree(registry, definition.id());
                Set<Object> blocks = new LinkedHashSet<>();
                for (String id : definition.blocks()) {
                    Object block = nativeBlocks.get(id);
                    if (block == null || !minecraft(ENTITY_BLOCK).isInstance(block))
                        throw new IllegalStateException("Missing original Minecraft EntityBlock for " + id);
                    if (BLOCK_TYPES.containsKey(block))
                        throw new IllegalStateException("Duplicate block entity ownership: " + id);
                    blocks.add(block);
                }
                java.util.function.BiFunction<Object,Object,Object> originalFactory =
                        CodaNativeContents.nativeBlockEntityFactory(definition.id());
                Object factory = Proxy.newProxyInstance(supplier.getClassLoader(),
                        new Class<?>[]{supplier}, (proxy, method, args) -> {
                            if (method.getName().equals("create") && args != null && args.length == 2) {
                                Object entity = originalFactory == null
                                        ? entityConstructor().newInstance(args)
                                        : originalFactory.apply(args[0], args[1]);
                                if (entity == null || !minecraft(ENTITY).isInstance(entity))
                                    throw new IllegalStateException("Native entity factory returned a "
                                            + "non-Minecraft BlockEntity: " + definition.id());
                                return entity;
                            }
                            if (method.getName().equals("toString")) return definition.id();
                            if (method.getName().equals("hashCode"))
                                return System.identityHashCode(proxy);
                            if (method.getName().equals("equals"))
                                return proxy == args[0];
                            throw new UnsupportedOperationException("Unexpected BlockEntitySupplier method");
                        });
                Object nativeType = type.getConstructor(supplier, Set.class)
                        .newInstance(factory, Set.copyOf(blocks));
                CodaNativeRegistryBridge.register(registry, definition.id(), nativeType);
                TYPE_IDS.put(nativeType, definition.id());
                for (Object block : blocks) BLOCK_TYPES.put(block, nativeType);
                System.out.println("[H.O.W.L.] Native BlockEntityType registered: " + definition.id());
            }
            registered = true;
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "Native BlockEntityType registration failed before Minecraft registry freeze", error);
        }
    }
}
