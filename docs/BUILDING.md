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

The BuildCraft creative-inventory regression test runs the production bytecode
transformer and reproduces the reported column-7 sprite-array crash before
checking the correction. The Snapshot API workflow also verifies the patch
against Mojang's checksum-verified 26.4 Snapshot 3 client and links the actual
screen class without starting graphics. Its `buildcraft-creative-inventory-fix`
artifact contains a test loader bundle; it does not publish a release.
On Windows, extract that artifact and run `Fix-BuildCraft.cmd` with Minecraft
and CodaLauncher closed. It verifies the bundle and loader hashes, backs up
the managed Nightly loader and its marker, and updates Nightly's hash marker
so the launcher accepts the test patch. Its Windows CI fixture checks both
installation and refusal of corrupt downloads or player-modified loaders.

The BuildCraft resource-pack test checks explicit modern atlas entries,
original texture bytes, exclusion of unsupported Forge expression models,
repeatable generation and preservation of player-edited packs. To use a real
BuildCraft H.O.W.L. mod JAR instead of fixture assets, after the normal tests:

```sh
java -cp out/test-classes:out/api-classes:dist/CodaLoader.jar \
  dev.howlingwhispers.codaloader.bootstrap.BuildCraftResourceInstallerTest \
  /path/to/buildcraft-cml-0.1.0-dev.jar
```

For live validation, open and reopen creative inventory in a throwaway world,
select BuildCraft's tab, and check the pipe, engine and wrench icons. The fix
keeps tab positions and item IDs unchanged, so existing worlds require no
migration.
