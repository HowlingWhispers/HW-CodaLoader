package net.minecraft.network.chat;
public record Component(String text) {
    public static Component literal(String label) { return new Component(label); }
}
