package dev.howlingwhispers.codawolf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.wolf.Wolf;
/** Mimic the two precise missing mappings reported by the player's game log. */
public final class NativeLookupSnapshotTest {
    static int checks;
    static void assertThat(boolean b,String why) { checks++; if (!b) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        var fixture = Minecraft.getInstance().integrated;
        // The fixture's ServerLevel has no getDayTime() and EntityType has no WOLF.
        assertThat(java.util.Arrays.stream(fixture.level.getClass().getMethods())
                .noneMatch(m->m.getName().equals("getDayTime")), "missing direct dayTime reproduced");
        assertThat(java.util.Arrays.stream(net.minecraft.world.entity.EntityType.class.getFields())
                .noneMatch(f->f.getName().equals("WOLF")), "missing static wolf field reproduced");
        var bridge = new MinecraftWolfBridge();
        assertThat(bridge.player(fixture.player.getUUID()) == fixture.player, "native owner lookup");
        assertThat(bridge.dayTime(fixture.level) == 12345L, "authoritative level-data day clock");
        var wolf = (Wolf) bridge.spawn(fixture.player);
        assertThat(wolf.type.name.equals("minecraft:wolf"), "wolf resolved via vanilla registry id");
        assertThat(wolf.owner.equals(fixture.player.getUUID()), "wolf tamed to local owner");
        assertThat(wolf.tamed && !wolf.sitting && wolf.persistent, "defensive companion can follow");
        assertThat("Coda".equals(wolf.label.text()) && wolf.nameVisible, "Coda named visibly");
        assertThat(wolf.collar == net.minecraft.world.item.DyeColor.CYAN, "cyan collar");
        assertThat(bridge.wolf(fixture.level,wolf.getUUID()) == wolf, "wolf registered in server world");
        assertThat(wolf.health == 20f && !bridge.dead(wolf), "wolf spawns healthy and alive");
        bridge.discard(wolf);
        assertThat(bridge.wolf(fixture.level,wolf.getUUID()) == null, "fixture cleanup");
        System.out.println("PASS: " + checks + " observed Snapshot 3 wolf/dayTime mapping regression assertions");
    }
}
