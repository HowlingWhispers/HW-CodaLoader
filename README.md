# CodaLoader

CodaLoader is a from-scratch Minecraft Java mod loader and bootstrap project targeting **Minecraft Java 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## Windows download

Releases are distributed as one versioned ZIP:

```text
CodaLoader-v0.0.5-menu-win64.zip
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

## Current milestone: 0.0.5-menu

### CML main-menu hook

The client hook no longer assumes Minecraft exposes its current screen as a field literally declared as `Screen`. It searches the live client object graph for the active title screen, including common holder shapes such as `Optional` and `AtomicReference`.

The CML button is retried until it is successfully attached to a title-screen instance. This makes the hook tolerant of the title screen appearing before or after CodaLoader's background hook thread reaches it.

### Howling Whispers menu branding

Place the theme source images here:

```text
run/
└── branding/
    ├── title.png
    ├── panorama_0.png
    ├── panorama_1.png
    ├── panorama_2.png
    ├── panorama_3.png
    ├── panorama_4.png
    └── panorama_5.png
```

The panorama images may be six completely different Howling Whispers scenes. CodaLoader converts them into Minecraft's rotating title panorama and replaces the large Minecraft logo with the supplied transparent `title.png`.

At launch it generates:

```text
run/game/resourcepacks/HowlingWhispers-Branding/
```

The source artwork under `run/branding/` is never overwritten by the automatic updater.

## Previous hook milestone

CodaLoader enters the actual Minecraft JVM as a Java agent. The hook milestone proved in-JVM mod initialization, the custom window-title hook, and optional custom menu music.

Put Vorbis `.ogg` files here:

```text
run/music/menu/
```

CodaLoader generates a resource pack for them inside the isolated game profile.

Java 25+ is required by Minecraft 26.4 Snapshot 3. Java 26 is supported.
