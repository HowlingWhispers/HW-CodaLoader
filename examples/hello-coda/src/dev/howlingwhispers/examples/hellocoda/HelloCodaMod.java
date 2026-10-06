package dev.howlingwhispers.examples.hellocoda;

import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;

public final class HelloCodaMod implements CodaMod {
    @Override
    public void onInitialize(CodaContext context) {
        System.out.println("[HelloCoda] Pawprint confirmed. CodaLoader can load me. 🐾");
        System.out.println("[HelloCoda] Minecraft target: " + context.minecraftVersion());
    }
}
