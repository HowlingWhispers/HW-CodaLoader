package dev.howlingwhispers.codaloader.api;

/** Integer Minecraft block position; safe to exchange between mod and loader. */
public record CodaBlockPos(int x, int y, int z) {}
