package dev.howlingwhispers.codaloader.api;

/** Immutable block hit from the invoking player's authoritative view ray. */
public record CodaBlockTarget(String dimension, CodaBlockPos position) {}
