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
    // Deliberately omit setOwnerUUID: absent on the user's 26.4 Snapshot 3.
    public void tame(net.minecraft.client.server.IntegratedServer.FakePlayer player) {
        owner=player.getUUID();
        tamed=true;
    }
    public boolean isTame() { return tamed; }
    public boolean isOwnedBy(net.minecraft.client.server.IntegratedServer.FakePlayer player) {
        return owner!=null && owner.equals(player.getUUID());
    }
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
