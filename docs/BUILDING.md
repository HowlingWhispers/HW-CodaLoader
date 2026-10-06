# Building CodaLoader

CodaLoader's foundation deliberately uses only the JDK build tools. No Gradle, Maven, Fabric Loom, ForgeGradle, or other mod-loader build system is required for this stage.

## Local build

Linux/macOS:

```bash
./scripts/build-dist.sh
```

Windows PowerShell:

```powershell
./scripts/build-dist.ps1
```

The build creates:

```text
dist/
├── CodaLoader-0.0.1-foundation.jar
└── hello-coda-0.0.1.jar
```

The CodaLoader JAR is executable:

```bash
java -jar dist/CodaLoader-0.0.1-foundation.jar run
```

To include the example mod:

```text
run/
└── mods/
    └── hello-coda-0.0.1.jar
```

## GitHub Actions

`.github/workflows/build.yml` runs on every push to `main`, on pull requests, and when manually dispatched.

The workflow checks out the repository, installs Temurin Java 21, compiles the loader, creates an executable loader JAR, compiles the Hello Coda example mod, starts the built loader with that mod, fails unless the mod initializes successfully, and uploads both JARs as a GitHub Actions artifact.

A green workflow therefore means more than "javac succeeded": the produced loader JAR was actually executed and successfully loaded a CodaLoader mod.

## Minecraft integration

This build proves the loader foundation. It does not yet launch Minecraft 26.4 Snapshot 3 itself. The Minecraft bootstrap/transform layer is the next milestone.
