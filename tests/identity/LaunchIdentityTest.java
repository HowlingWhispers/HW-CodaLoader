import dev.howlingwhispers.codaloader.bootstrap.LaunchIdentity;
import java.util.HashMap;
import java.util.Map;
public final class LaunchIdentityTest {
    public static void main(String[] args) {
        var env = new HashMap<>(Map.of("CODA_LAUNCHED_BY", "CodaLauncher", "CODA_PLAYER_NAME", "CodaOwner",
            "CODA_PLAYER_UUID", "1234567890abcdef1234567890abcdef", "CODA_PLAY_MODE", "offline", "CODA_ACCESS_TOKEN", "secret"));
        var offline = LaunchIdentity.fromEnvironment(env);
        if (!offline.offline() || !offline.token().equals("0") || offline.toString().contains("secret")) throw new AssertionError("Offline token leakage");
        env.put("CODA_PLAY_MODE", "online");
        if (LaunchIdentity.fromEnvironment(env).offline()) throw new AssertionError("Online mode lost");
        env.put("CODA_ACCESS_TOKEN", "0"); reject(env);
        env.put("CODA_ACCESS_TOKEN", "secret"); env.put("CODA_PLAY_MODE", "guest"); reject(env);
        env.put("CODA_PLAY_MODE", "offline"); env.put("CODA_PLAYER_UUID", "forged"); reject(env);
        reject(Map.of());
        System.out.println("PASS: identity required, UUID preserved, explicit mode, token redaction");
    }
    private static void reject(Map<String,String> env) {
        try { LaunchIdentity.fromEnvironment(env); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Invalid launch identity accepted");
    }
}
