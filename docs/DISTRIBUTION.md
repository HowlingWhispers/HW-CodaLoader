# CodaLoader distribution

## GitHub Releases

Compiled public-facing builds belong on the repository's Releases page, not only in short-lived GitHub Actions artifacts.

The first downloadable build is:

- tag: `v0.0.1-foundation`
- title: `CodaLoader v0.0.1 Foundation`
- status: prerelease
- assets:
  - `CodaLoader-0.0.1-foundation.jar`
  - `hello-coda-0.0.1.jar`

The foundation release is deliberately marked as a prerelease because it does not launch Minecraft yet.

## Future releases

Pushing a version tag matching `v*` invokes `.github/workflows/release.yml`. The workflow builds the JARs, runs the packaged smoke test, and creates a GitHub Release with the compiled assets.

Before the first Minecraft-capable release, the build scripts should be taught to derive filenames/version numbers from one version source instead of the current foundation constants.

## HW-Landing integration

A future Downloads / Mods section in HW-Landing should treat GitHub Releases as the distribution source of truth.

The landing page can surface:

- latest CodaLoader release
- supported Minecraft version
- prerelease/stable status
- release notes
- direct asset download links
- future CodaLoader-compatible mods

That keeps binaries in GitHub Releases while HW-Landing becomes the friendly storefront rather than a second binary store.
