# H.O.W.L.

**Howling Open Works Loader**, by Howling Whispers. CodaLauncher remains the player launcher.

H.O.W.L. is a from-scratch Minecraft Java mod loader and bootstrap project targeting **Minecraft Java 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## First-party mod policy

H.O.W.L. player releases have **no optional first-party mods**. All officially released gameplay modules must install and update automatically; missing required modules trigger repair or a visible error, not a skip option. Unfinished prototypes such as BuildCraft CML and HW Quiet Underground stay in development rather than appearing as optional player downloads.

Verified world-generation changes must become required, automatically enabled content in new H.O.W.L. worlds. Existing world saves must not be silently rewritten; that requires a separate, tested backup/migration plan. User-developed community mods are separate from the official required set.

## Development: safe single-player inventory inspection

The H.O.W.L. server tick context can now expose a read-only, tick-scoped
`CodaWorldView` for an integrated single-player server. It reports loaded
dimensions and chest-like inventory slot occupancy without loading chunks,
exposing mutable Minecraft objects, or permitting extraction. The adapter
fails closed on unknown Snapshot 3 mappings and rejects wrong-thread or
after-tick access. A Java instrumentation fixture verifies it. **This is not
a playable BuildCraft chest-to-chest bridge yet**; transactional inventory
moves, block/item registrations, rendering, and a live Snapshot 3 test remain
release gates. No shared-play or networking features are introduced here.

## Development: verified Quiet Underground world bootstrap

A cross-platform Java `RequiredWorldPacks.prepareNewWorld` utility now validates
the exact Snapshot 3 worldgen ZIP and its pinned SHA-256. It atomically stages
the pack only in ungenerated, safe world-creation folders; existing worlds,
generated chunk data, community packs and conflicting files remain untouched.
The Java fixture tests reject corrupted hashes, unexpected save contents,
symlinks and wrong data formats. **It is not enabled for players yet.**
Minecraft's initial data-pack discovery must still be hooked before the first
world creation, and the pack must be marked active in its world data
configuration. Simply writing `datapacks/*.zip` does not activate the pack.
Live new-world integration and terrain checks remain release blockers.

## Development: native server tick API for BuildCraft

The next H.O.W.L. API milestone adds a version-pinned JVM callback at the end
of `MinecraftServer.tickServer(BooleanSupplier)`. Mods can register callbacks
through `CodaContext.registerServerTick`; errors are isolated and repeated
faults disable the failing listener. Each Minecraft server instance gets an
opaque session ID and its own tick counter. This is **source-only development**,
not part of the released 0.0.26 build. JVM fixture checks are wired into CI;
live Snapshot 3 verification remains mandatory. No block or inventory APIs are
claimed, and BuildCraft remains non-playable until its world adapters exist.

## H.O.W.L. 0.0.26 and developer downloads

The loader now displays **H.O.W.L.** in the game window, status badge and pack
descriptions. The shipped yellow splash pool includes
`COWL?! ...no. *huff* H.O.W.L.!`, including when an older base pack supplies
splash text. A player's custom `branding/splashes.txt` remains authoritative.

Existing repository names, `CodaLoader.jar`, update bundles, `coda.mod.json`,
Java API packages, configuration keys and save paths stay compatible.
CodaLauncher 0.7.3 renames its existing official installation to
**Howling Whispers | H.O.W.L.** without duplicating it or moving worlds.

[Releases](https://github.com/HowlingWhispers/HW-CodaLoader/releases) include
`HOWL-SDK-v0.0.26.zip` and `howl-api-0.0.26.jar` for developers. The SDK has an
editable mod, compile-only API, API sources and build scripts.
See [HOWL API](docs/HOWL-API.md) for supported hooks and current revival limits.
Block/item/machine/transport APIs needed for BuildCraft remain development work.

This remains a prerelease: automated menu and agent checks do not replace a
live Minecraft Snapshot 3 test of launch, Settings return, menu spacing and music.

## Custom Coda menu ownership (source milestone)

CodaLoader now defaults to a CML-owned **widget layout** for the Minecraft 26.4
Snapshot 3 title and pause screens. This stage keeps Minecraft's native screen
classes and action callbacks, but CML owns the known button captions, spacing,
positions and the visibility of unwanted native controls:

- Title: **My Worlds**, **Coda's Settings**, **Clock Out**, plus a separate
  centred row for native and HW social controls.
- Pause: **Back to Adventure**, **Pawprints**, **Coda's Ledger**, native
  World Rules / Settings, icon controls and **Save & Curl Up**.
- Legacy multiplayer/Realms and LAN/feedback shortcuts are removed from
  input and rendering. Unknown third-party controls remain untouched.
- Both layouts are rebuilt on Minecraft's GUI thread, including after resize.
  Duplicate asynchronous CML control injection is guarded.
- A failed layout or unknown essential native action falls back to the older
  native customization, instead of replacing functional game actions.
- To force legacy layout during development, launch with Java property
  `-Dcodaloader.menu.mode=legacy`. The default is `owned`.

**Scope:** This does not yet create standalone Screen subclasses or a custom
frame renderer. It is the safe menu-control foundation for that subsequent
milestone and the planned HW Clipboard. Automated fixture tests validate
spacing, callbacks and repeated initialization; live Snapshot 3 verification
remains required before a production release.

## Windows download

Releases are distributed as one versioned ZIP:

```text
CodaLoader-v0.0.24-win64.zip
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

## HW Essentials 0.2.0

The CML-native homes mod for **Minecraft Java 26.4 Snapshot 3** is now bundled with CodaLoader 0.0.24. It is installed automatically into the active game profile, including CodaLauncher-managed profiles.

Commands: `/sethome [name]`, `/home [name]`, `/back`, `/homes`, `/delhome <name>` and `/hwessentials`.

`/back` restores the departure point of the last successful `/home` or `/back`. Return points last until Minecraft restarts; refused teleports preserve them.

Homes belong to the current player and world, preserve facing, and use atomic saves. The first version supports same-dimension travel in singleplayer, with conservative checks for collisions, fluid, missing support and world bounds. No cheats toggle is required for these personal utility commands.

See [HW Essentials](https://github.com/HowlingWhispers/HW-Mods/tree/main/mods/hw-essentials) for storage, configuration and the live-game verification boundary. Its source and storage tests live in HW-Mods and it ships as a separate mod JAR; the loader exposes a small server-thread command API.

## Current milestone: 0.0.24

### Coda's personal-world menus

Discord, YouTube and Snapshot 3's three native small controls occupy one centred row below Settings and Clock Out. The complete row is recalculated immediately after social-button injection, with eight-pixel gaps between hitboxes.

The title menu removes Realms and traditional Multiplayer, keeping My Worlds, Coda's Settings and Clock Out. The pause menu removes Open to LAN and vanilla feedback/bug-report shortcuts, while preserving native resume, advancements, statistics, settings and save-and-quit actions.

Coda labels: Back to Adventure, Pawprints, Coda's Ledger and Save & Curl Up. The UI pack supplies English Coda wording for world selection, settings and resource packs. Destructive world deletion still says clearly that the world is permanently deleted.

The distribution bundles ASM 9.9 (BSD 3-Clause), pinned by SHA-256 at build time, for bytecode hooks on Java 25 and 26.

The Java agent applies the layout at the end of native initialization and rebuild methods, before rendering, instead of waiting for the background polling thread. Snapshot 3 pause icons have a dedicated row and World Options remains accessible alongside Settings.

The hook recognizes translation keys, not displayed English text. It runs widget changes on Minecraft's GUI thread, handles rebuilt menus and leaves unknown third-party controls intact. Fixtures check input/render removal, retained callbacks, layout and repeated initialization. Live Snapshot 3 GUI verification remains outstanding.

Friends and shared-world controls will be added when those features are implemented.

### Random CML menu scenes

CML now prepares two title-background scene modes while preserving the existing menu artwork:

- **classic-panorama** uses the existing four horizontal panorama images plus the generated/custom sky and floor.
- **banner-sweep** uses an optional very wide `menu_banner.png`. If no banner is supplied yet, CML derives a test banner from the existing four panorama images.

One scene is chosen randomly at game startup. CML chooses a different available scene only after the player leaves an actual world and returns to the title screen. Settings, world selection and other title submenus do not trigger resource reloads, a Mojang splash or music restarts.

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


## Verified account launch (source development)

The source now requires a CodaLauncher account handoff for normal Minecraft launches. Online launches independently verify Java entitlements and the account UUID/name against Minecraft services; the old CodaPlayer test identity is removed. Offline launches preserve the verified UUID, contain no online token and use cached metadata/files. Missing assets require online repair. Direct loader-only/agent test paths are unchanged.

This is not in release 0.0.24 yet. Publish it together with the launcher's configured and live-tested authentication release. `coda.offline=true` is a local mod hint, not a remote authentication credential. Shared services must validate their own online credentials when implemented.
