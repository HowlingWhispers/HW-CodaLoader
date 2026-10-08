package net.minecraft.resources;
public record Identifier(String value) {
    public static Identifier parse(String value) { return new Identifier(value); }
}
