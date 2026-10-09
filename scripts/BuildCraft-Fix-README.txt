BuildCraft creative inventory test patch for H.O.W.L. Nightly

1. Close Minecraft and CodaLauncher.
2. Extract the downloaded artifact ZIP into a folder.
3. Keep CodaLoader-v0.0.28-win64.zip zipped, beside the scripts and manifest.
4. Double-click Fix-BuildCraft.cmd. No administrator access is required.
5. Open CodaLauncher, select Nightly, and test creative inventory.

The script verifies the bundle and loader checksums, preserves the previous
loader in previous-loader-code, and updates Nightly's loader hash marker.
It preserves mods, worlds, settings, and the currently installed release tag.
The loader refreshes its unedited BuildCraft resource pack on next launch,
using the original BuildCraft textures. Player-edited packs are preserved.

This is a test patch on fix/buildcraft-creative-inventory, not a new release.
A future Nightly update or optional-mod reinstall may replace this test patch.
