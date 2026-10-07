# CodaLoader

CodaLoader is a from-scratch Minecraft Java mod loader and bootstrap project targeting **Minecraft Java 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## Windows download

Releases are distributed as one versioned ZIP:

```text
CodaLoader-v0.0.18-essentials-win64.zip
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

## HW Essentials 0.1.0

The CML-native homes mod for **Minecraft Java 26.4 Snapshot 3** is now bundled with CodaLoader 0.0.18. It is installed automatically into the active game profile, including CodaLauncher-managed profiles.

Commands: `/sethome [name]`, `/home [name]`, `/homes`, `/delhome <name>` and `/hwessentials`.

Homes belong to the current player and world, preserve facing, and use atomic saves. The first version supports same-dimension travel in singleplayer, with conservative checks for collisions, fluid, missing support and world bounds. No cheats toggle is required for these personal utility commands.

See [HW Essentials](mods/hw-essentials/README.md) for storage, configuration and the live-game verification boundary. Its code stays in a separate mod JAR; the loader exposes a small server-thread command API.

## Current milestone: 0.0.18-essentials

### Random CML menu scenes

CML now prepares two title-background scene modes while preserving the existing menu artwork:

- **classic-panorama** uses the existing four horizontal panorama images plus the generated/custom sky and floor.
- **banner-sweep** uses an optional very wide `menu_banner.png`. If no banner is supplied yet, CML derives a test banner from the existing four panorama images.

One scene is chosen randomly at game startup. After leaving the title screen, CML chooses a different available scene when the player later returns and asks Minecraft to reload the branding resources.

The Banner Sweep cubemap is laid out forward and then mirrored, so Minecraft's normal panorama motion produces an experimental left-to-right-then-back visual sweep without requiring animated art.

Optional branding source:

```text
branding/menu_banner.png
```

The classic panorama remains available and is not replaced.

### Launcher-managed install root

CodaLauncher can now start CodaLoader with:

```text
java -jar CodaLoader.jar --root "%APPDATA%\.howlingshispers\minecraft" --base-pack "%APPDATA%\.howlingshispers\cml-base"
```

The supplied root is the actual Minecraft game directory. CodaLoader keeps runtime downloads below it and reads mandatory official presentation assets from the separate CML base-pack directory. User files under the Minecraft root remain overrides/additions.

### Grounded title panorama

Minecraft's title background is a cubemap, not a slideshow. CodaLoader now uses `panorama_0.png` through `panorama_3.png` only for the four horizontal scene faces. The up/down faces are generated as a twilight sky and dark tiled floor so Coda no longer appears above and below the camera.

Optional `run/branding/sky.png` and `run/branding/floor.png` files override the generated top/bottom textures.

### CodaLauncher bridge

CodaLoader now exposes machine-readable version information without starting Minecraft:

```text
java -jar CodaLoader.jar --version
java -jar CodaLoader.jar --version-json
```

The JSON form reports the CodaLoader version, exact Minecraft target, display name, and minimum Java level.

### Mandatory base-pack music

CodaLauncher installs the official Howling Whispers menu track under the managed CML base pack. CodaLoader reads that base music automatically, while user-added tracks under `music/menu/` remain additive and are never overwritten.

### Discord icon contrast fix

The Discord social control now uses a high-contrast blurple tile with a white Discord glyph so the mark stays visible at Minecraft's 20x20 title-screen button size. The YouTube button is unchanged.

### Branded social controls

The title screen now prefers real Discord and YouTube image-button sprites instead of the temporary `D` and `YT` labels. The icons ship as small resources inside CodaLoader.jar, then CodaLoader materializes them into an always-on `CodaLoader-UI` resource pack for Minecraft.

The YouTube control opens the Howling Whispers channel. Discord remains a visual placeholder until its destination is configured. If Snapshot 3 changes the image-button API, CML falls back to text controls rather than breaking the title screen.

### Bottom-left label spacing

Minecraft draws its own version label after normal title-screen widgets, so the vanilla text can appear over a CML button occupying the same pixels. CML now leaves that bottom strip alone and places its compact status badge directly above it.

### Clean Windows update handoff

Automatic updates now launch both the temporary updater and the restarted CodaLoader BAT through an explicit `cmd.exe /d /c call` handoff. This prevents Windows from leaving the helper command processor open as an empty shell after the update finishes.

The temporary updater is minimized while it waits for the old CodaLoader JVM to exit and replaces the managed files.

### Title-screen layout polish

CML now uses a compact bottom-left status button instead of the large top-left proof bar. Two 20x20 placeholder controls are added around Minecraft's existing three small title buttons: Discord on the left and YouTube on the right. Their real links/actions will be wired later.

### Shipped and user menu music

CodaLoader now reads menu music from two locations:

```text
run/music/default/   # CodaLoader project-shipped tracks
run/music/menu/      # user-added tracks
```

Both sets are combined into the generated menu-music resource pack. Project-owned defaults can therefore ship separately from user music rather than being embedded in Java.

### Coda-flavored title splashes

The Howling Whispers branding pack now replaces Minecraft's vanilla yellow title-screen splash messages with a built-in Coda/CML pool.

To supply your own messages, create:

```text
run/branding/splashes.txt
```

Put one message per line. When that file exists, CodaLoader uses it instead of the built-in list.

### Persistent CML title button

Snapshot 3 can rebuild the title-screen widgets after CodaLoader first reaches the screen. CML now tracks the exact injected button object and verifies that it remains present in the screen's widget/container state. If Minecraft clears the widget during menu initialization, CML injects a replacement on the render thread instead of treating the first add call as permanent success.

### Snapshot 3 title-screen probe

CML now walks nested Minecraft client state, including common holder and collection shapes, instead of assuming the active GUI screen is stored directly on `Minecraft`. It also discovers the render-thread Runnable scheduler by method signature when the readable method name is unavailable.

Successful runtime diagnostics include `Minecraft title screen located`, `Client render-thread scheduler located`, and `Added CML button to Minecraft title screen`.

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
    ├── menu_banner.png (optional wide Banner Sweep scene)
    ├── sky.png        (optional)
    ├── floor.png      (optional)
    └── splashes.txt   (optional)
```

`panorama_0.png` through `panorama_3.png` are the four horizontal Howling Whispers scenes. CodaLoader supplies the cubemap sky and floor automatically unless `sky.png` or `floor.png` overrides are present. The large Minecraft logo is replaced with the supplied transparent `title.png`.

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
