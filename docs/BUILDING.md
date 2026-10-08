# Building H.O.W.L.

Use JDK 21+ to build, and Java 25+ to run Minecraft Java 26.4 Snapshot 3.
The project uses JDK tools directly; no Forge, Fabric or external mod-loader
build system is required. Builds fetch SHA-256-pinned ASM and checksum-verified
HW Essentials from its separate release.

Linux/macOS: `bash scripts/build-dist.sh`. Windows PowerShell:
`./scripts/build-dist.ps1`.

The distribution includes `dist/CodaLoader.jar`, the versioned player bundle,
`update-manifest.json`, `hello-coda.jar`, `hw-essentials.jar`, the versioned
HOWL SDK ZIP and compile-only API JAR. Loader filenames remain compatible.

To test initialization, put a mod JAR into a scratch `mods` directory and run:

```sh
java -jar dist/CodaLoader.jar --loader-only /path/to/scratch
bash scripts/test-essentials.sh
```

The HOWL SDK has its own standalone mod build scripts; see
[sdk/README.md](../sdk/README.md) and [HOWL API](HOWL-API.md).

GitHub Actions compiles Windows/Linux distributions, checks identities,
commands, menus, scene visits and splash overrides, and exercises the agent
on Java 21 and 25. It also extracts the SDK, builds the starter using only
its API JAR and loads the result. Release builds use immutable version tags;
source-only changes never replace a published version.

Automated fixtures do not exercise the actual Minecraft GUI, Windows login
handoff or gameplay. Those still require a live target-version game test.
