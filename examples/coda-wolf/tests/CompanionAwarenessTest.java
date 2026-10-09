import dev.howlingwhispers.codawolf.CompanionAwareness;
import dev.howlingwhispers.codawolf.CompanionAwareness.Observation;
import dev.howlingwhispers.codawolf.CompanionRules;
import java.util.List;

public final class CompanionAwarenessTest {
    private static int checks;
    private static void ok(boolean value,String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static Observation block(String id,long tick,int x) {
        return new Observation("minecraft:overworld",id,List.of(),x,64,0,x*x,tick);
    }
    public static void main(String[] args) {
        var c = new CompanionAwareness();
        ok(c.recent().isEmpty(),"New wolf has no fabricated observations");
        ok(c.observe(block("minecraft:brown_mushroom",1,1)).orElseThrow()
                .text().contains("brown mushroom"),
                "First mushroom gets grounded advice without AI");
        ok(c.observe(block("minecraft:brown_mushroom",2,1)).isEmpty(),
                "No repeated mushroom announcements per scan");
        ok(c.observe(block("minecraft:red_mushroom",3,2)).isEmpty(),
                "Global speaking cooldown prevents chatter flood");
        ok(c.recent().get(0).blockId().equals("minecraft:red_mushroom"),
                "Silent sightings still enter active AI context");
        ok(c.observe(block("minecraft:lava",900,3)).isEmpty(),
                "Cooldown at exact boundary requires distinct event age");
        ok(c.observe(block("minecraft:lava",901,4)).orElseThrow().category().equals("hazard"),
                "Lava alert recognized as nearby, not claimed visible");
        ok(c.observe(block("minecraft:stone",2000,5)).isEmpty(),
                "Unknown blocks do not invent suggestions");
        ok(c.observe(block("minecraft:wheat",2001,6)).orElseThrow()
                .text().contains("crops"),"Crops generate practical ideas");
        ok(CompanionAwareness.respondTo(new Observation("minecraft:overworld",
                "minecraft:dandelion",List.of("minecraft:flowers"),0,64,0,0,2002))
                .orElseThrow().category().equals("flower"),
                "Datapack block tags can drive suggestions");
        ok(CompanionAwareness.respondTo(block("buildcraftcore:engine_redstone",2500,0))
                .orElseThrow().text().contains("aren't connected"),
                "Machine observation does not invent heat telemetry");
        ok(CompanionAwareness.respondTo(block("minecraft:diamond_ore",2500,0))
                .orElseThrow().text().contains("mining"),
                "Ore ID triggers mining suggestion");
        ok(CompanionAwareness.respondTo(block("minecraft:bee_nest",2500,0))
                .orElseThrow().text().contains("campfire"),
                "Bee nest produces practical advice");
        ok(c.observationsSeen()==6,"All unique or repeated observations recorded");
        for (int i=0;i<300;i++)
            c.observe(block("minecraft:stone",3000+i,i+50));
        ok(c.recent().size()<=12,"AI context bounded to twelve recent observations");
        int total=147;
        var seen=new boolean[total];
        for(int i=0;i<total;i++)
            seen[c.nextScanIndex(total)]=true;
        for(boolean v:seen)ok(v,"Incremental scan eventually visits every position");
        ok(c.nextScanIndex(total)==0,"Scan cursor cycles without new chunk requests");
        ok(!CompanionRules.releaseAssignedTarget(false,false),
                "No gratuitous vanilla target clearing");
        ok(CompanionRules.releaseAssignedTarget(false,true),
                "Only our manually assigned target may be released");
        ok(!CompanionRules.releaseAssignedTarget(true,true),
                "Current valid protective target retained");
        try{
            new Observation("overworld","minecraft:stone",List.of(),0,0,0,-1,0);
            throw new AssertionError("Negative distance accepted");
        }catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS: "+checks+" Coda offline environment, cooldown and vanilla-target tests");
    }
}
