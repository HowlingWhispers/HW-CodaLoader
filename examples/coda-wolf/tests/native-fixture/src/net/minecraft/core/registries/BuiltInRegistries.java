package net.minecraft.core.registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
public final class BuiltInRegistries {
    public static final TestRegistry ENTITY_TYPE = new TestRegistry();
    public static final class TestRegistry {
        public Object getValue(Identifier key) {
            return "minecraft:wolf".equals(key.value()) ? new EntityType(key.value()) : null;
        }
    }
}
