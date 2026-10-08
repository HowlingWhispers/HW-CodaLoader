package net.minecraft.world.entity.animal.wolf;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
public final class Wolf {
    public final EntityType type;
    public final ServerLevel level;
    public UUID owner;
    public Component label;
    public DyeColor collar;
    public boolean tamed, sitting, persistent, nameVisible;
    public float health;
    private final UUID uuid = UUID.randomUUID();
    public Wolf(EntityType type,ServerLevel level) { this.type=type; this.level=level; }
    public void setPos(double x,double y,double z) {}
    public void setOwnerUUID(UUID id) { owner=id; }
    public void setTame(boolean tame,boolean broadcast) { tamed=tame; }
    public void setOrderedToSit(boolean sit) { sitting=sit; }
    public void setCustomName(Component name) { label=name; }
    public void setCustomNameVisible(boolean value) { nameVisible=value; }
    public void setPersistenceRequired() { persistent=true; }
    public void setCollarColor(DyeColor color) { collar=color; }
    public void setHealth(float value) { health=value; }
    public float getMaxHealth() { return 20f; }
    public float getHealth() { return health; }
    public UUID getUUID() { return uuid; }
    public boolean isAlive() { return health>0; }
    public void discard() { level.entities.remove(uuid); }
}
