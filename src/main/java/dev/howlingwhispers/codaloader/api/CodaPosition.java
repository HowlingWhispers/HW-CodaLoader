package dev.howlingwhispers.codaloader.api;

public record CodaPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
    public CodaPosition {
        if (dimension == null || dimension.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Invalid home position");
        }
    }
}
