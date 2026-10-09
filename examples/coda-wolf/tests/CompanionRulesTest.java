import dev.howlingwhispers.codawolf.CompanionRules;
import dev.howlingwhispers.codawolf.CompanionSave;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class CompanionRulesTest {
    static int assertions;
    static void check(boolean ok, String reason) { assertions++; if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        check(CompanionRules.shouldDefend(true,true,20,20,20),"recent attacker defended");
        check(!CompanionRules.shouldDefend(false,true,20,20,20),"no unprovoked attack");
        check(!CompanionRules.shouldDefend(true,true,5,20,20),"low health retreat");
        check(!CompanionRules.shouldDefend(true,true,20,20,257),"stay near owner");
        check(!CompanionRules.shouldDefend(true,false,20,20,20),"dead wolf does not attack");
        var gate = new CompanionRules.SleepGate();
        check(!gate.tick(false,12500),"initial baseline");
        check(!gate.tick(false,24000),"time skip without sleep ignored");
        check(!gate.tick(true,25000),"sleep alone not enough");
        check(gate.tick(false,48000),"successful bed night skip");
        check(!gate.tick(false,48001),"only once");
        var asleepSkip = new CompanionRules.SleepGate();
        check(!asleepSkip.tick(false,13000),"new second gate");
        check(!asleepSkip.tick(true,13001),"entered sleep");
        check(!asleepSkip.tick(true,24000),"skip during sleep waits for waking");
        check(asleepSkip.tick(false,24001),"wake after night skip permits return");
        Path temp = Files.createTempDirectory("coda-save-test-");
        UUID owner = UUID.randomUUID(), wolf = UUID.randomUUID();
        var saved = CompanionSave.load(temp,owner);
        check(!saved.created && saved.wolfId==null && !saved.pendingRespawn,"new world uninitialized");
        saved.created = true; saved.wolfId=wolf;
        saved.awarenessEnabled = false; saved.persist();
        var restored = CompanionSave.load(temp,owner);
        check(restored.created && wolf.equals(restored.wolfId),"wolf identity persists");
        check(!restored.awarenessEnabled,"owner can disable local scanning across reloads");
        restored.wolfId=null; restored.pendingRespawn=true; restored.persist();
        var awaiting = CompanionSave.load(temp,owner);
        check(awaiting.pendingRespawn && awaiting.wolfId==null,"death state survives reload");
        var otherOwner=CompanionSave.load(temp, UUID.randomUUID());
        check(!otherOwner.created,"owner isolation");
        check(otherOwner.awarenessEnabled,"other players retain their default awareness");
        System.out.println("PASS: " + assertions + " survival and persistence assertions");
    }
}
