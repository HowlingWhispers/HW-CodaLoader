# CodaLoader

CodaLoader is a from-scratch Minecraft Java mod loader and bootstrap project targeting **Minecraft Java 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## Windows download

Releases are distributed as one versioned ZIP:

```text
CodaLoader-v0.0.3-hook-win64.zip
│
├── CodaLoader.jar
├── Launch-CodaLoader.bat
├── README-FIRST.txt
└── run/
    └── mods/
        └── hello-coda.jar
```

Extract the whole ZIP into its own folder and double-click `Launch-CodaLoader.bat`.

The ZIP name is versioned, but the installed filenames remain stable. Updating CodaLoader does not leave a trail of old version-numbered JARs behind.

## Automatic updates

Because this repository and its Releases are public, CodaLoader can check GitHub directly without an API token.

At startup CodaLoader:

1. reads the newest non-draft GitHub Release,
2. fetches `update-manifest.json`,
3. compares the release version and SHA-256 hashes of the managed files,
4. downloads the versioned ZIP when an update or repair is needed,
5. verifies the complete ZIP and every managed file,
6. stages the update under `run/update/`,
7. exits,
8. lets a temporary Windows updater replace the managed files,
9. restarts `Launch-CodaLoader.bat`.

Managed files are deliberately limited to:

```text
CodaLoader.jar
Launch-CodaLoader.bat
run/mods/hello-coda.jar
```

Worlds, configuration, custom music, Minecraft runtime downloads, and other mods are left alone.

The first launch creates `run/config/codaloader.properties`:

```properties
updates=auto
```

Supported values are `auto`, `notify`, and `off`.

## Current milestone: 0.0.3-hook

CodaLoader now enters the actual Minecraft JVM as a Java agent. The current proof hooks include in-JVM mod initialization, a custom window-title hook, a title-screen CodaLoader button, and optional custom menu music.

Put Vorbis `.ogg` files here:

```text
run/music/menu/
```

CodaLoader generates a resource pack for them inside the isolated game profile.

Java 25+ is required by Minecraft 26.4 Snapshot 3. Java 26 is supported.
