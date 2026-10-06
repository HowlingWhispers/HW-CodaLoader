# CodaLoader

CodaLoader is a from-scratch Minecraft mod loader and bootstrap project targeting **Minecraft Java Edition 26.4 Snapshot 3**.

It does **not** depend on Fabric, Forge, NeoForge, Quilt, or another mod loader.

## Current milestone: 0.0.3-hook

The current prerelease moves CodaLoader into the actual Minecraft JVM.

Normal launch now:

1. resolves and prepares Minecraft 26.4 Snapshot 3,
2. generates the optional custom-menu-music resource pack,
3. launches Minecraft with the CodaLoader JAR attached as a Java agent,
4. initializes CodaLoader mods inside the Minecraft JVM,
5. applies the first client hooks.

Visible proof targets:

- window title: `CodaLoader 0.0.3-hook | Minecraft Java 26.4 Snapshot 3`
- a CodaLoader status button on the title screen
- `hello-coda` prints that it is running inside the Minecraft JVM

## Custom menu music

Put Vorbis `.ogg` files here:

```text
run/
└── music/
    └── menu/
        ├── track-one.ogg
        └── track-two.ogg
```

When at least one track exists, CodaLoader generates:

```text
run/game/resourcepacks/CodaLoader-Music/
```

and enables it in the isolated game profile. The generated pack replaces the vanilla `music.menu` sound event with the tracks from the CodaLoader music folder.

## Downloads

Compiled prereleases are published on the repository's GitHub Releases page.

For Windows, place these together:

```text
CodaLoader-0.0.3-hook.jar
Launch-CodaLoader.bat
```

Then double-click `Launch-CodaLoader.bat`.

Java 25+ is required by Minecraft 26.4 Snapshot 3. Java 26 is supported.

## Runtime layout

```text
run/
├── config/
├── mods/
├── music/
│   └── menu/
├── game/
│   └── resourcepacks/
└── runtime/
    ├── versions/
    ├── libraries/
    ├── assets/
    └── natives/
```

## Current boundary

The first hook is deliberately tiny. CodaLoader is now present in the Minecraft process, but this is not yet a complete registry/event API. The next layers can build proper lifecycle events, registries, commands, screens, and deeper bytecode hooks on top of this proven in-JVM foothold.
