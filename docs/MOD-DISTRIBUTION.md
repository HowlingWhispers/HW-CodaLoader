# H.O.W.L. mod distribution contract

All optional H.O.W.L. gameplay mods are published as **individual, directly
downloadable `.jar` GitHub Release assets**.

- Player installation: `GitHub Release/*.jar` -> active
  `<Minecraft profile>/mods/*.jar`. No `.zip` extraction or nested archives.
- Every JAR includes `coda.mod.json`, its entrypoint classes, and the
  relevant assets/license notices. A JAR is already a ZIP-format container
  internally, and needs no outer ZIP.
- Only installed, launcher-managed optional JARs are updated on Play;
  newly discovered mods require the player's INSTALL action.
- Keep loader distributions (which include the launcher scripts), native app
  installers, and resource/data packs in their appropriate package formats.
  Required H.O.W.L. dependencies may remain embedded in the loader.
- BuildCraft Lite is a **direct JAR** in the H.O.W.L. v0.0.35 GitHub Release:
  `buildcraft-lite-0.1.0-dev.jar`.
- GitHub Actions `upload-artifact` automatically serves a ZIP wrapper around
  CI artifacts. This is a GitHub Actions transport detail, **not** a mod
  distribution format. Players should use GitHub Release assets and the
  CodaLauncher mod shelf, not download workflow artifacts.

Never publish optional mods as a `.zip` containing a `.jar`, nor as a
`.zip` containing another `.zip` containing a `.jar`.
