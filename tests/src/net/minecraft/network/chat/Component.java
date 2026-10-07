package net.minecraft.network.chat;
/** Fixture only. */
public record Component(String text) { public static Component literal(String text) { return new Component(text); } }
