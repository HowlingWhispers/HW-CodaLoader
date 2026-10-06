package dev.howlingwhispers.codaloader.api;

/** Base entrypoint implemented by every CodaLoader mod. */
public interface CodaMod {
    void onInitialize(CodaContext context) throws Exception;
}
