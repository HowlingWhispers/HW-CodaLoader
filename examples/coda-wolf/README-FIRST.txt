Coda Wolf 0.1.0-dev (experimental)
==================================
The launcher is NOT updated by this package.
Close Minecraft before installing, then extract this ZIP.

Windows (PowerShell in extracted folder):
    .\Install-CodaWolf.ps1
The script detects CodaLauncher's current Stable/Nightly setting and installs
the JAR only into that channel's active Minecraft mods folder. To override:
    .\Install-CodaWolf.ps1 -Channel Nightly

Manual install (all platforms):
    CodaLauncher > Mods > OPEN MODS FOLDER
    Copy coda-wolf-0.1.0-dev.jar into that exact directory
    CodaLauncher > Mods > REFRESH
Expected entry: Coda Wolf Companion | coda_wolf | 0.1.0-dev | Recognized

Do not install a second JAR with the same coda_wolf mod ID.
Live Minecraft entity APIs are UNVERIFIED; use a throwaway world only.
The first prototype has no voice, vision, or cloud AI connection.
Branch: https://github.com/HowlingWhispers/HW-CodaLoader/tree/feature/coda-wolf-companion
