# CodaLoader build and release channels

## Development builds: GitHub Actions

Every push to `main` builds and tests CodaLoader. Successful runs upload a
`CodaLoader-development-<commit>` artifact containing the candidate loader,
bundle and `dist/build-channel.json` metadata:

- `channel: development`
- `autoInstall: false`
- `requiresVerifiedAccount: true`

These artifacts can be used by developers with a compatible, verified
launcher/account setup, but **they are not published GitHub Releases and must
not be treated as normal player updates**.

The custom title/pause menu work can therefore be tested in CI without
replacing the known published version or bypassing identity verification.
Minecraft 26.4 Snapshot 3 must still be checked in-game before public release.

## Player updates: GitHub Releases

CodaLauncher downloads loader ZIPs only from
`HowlingWhispers/HW-CodaLoader` GitHub Releases, comparing the installed
version with the latest published version. A successful `main` build is
*not* a new player release.

The `Publish CodaLoader bundle` workflow is manual-only. Before it runs,
the developer must explicitly confirm the public launcher and Microsoft/
Minecraft account handoff have been live-verified. The workflow:

1. Builds and tests the pinned Minecraft target on Java 21 and Java 25.
2. Derives the version from `CodaTarget.LOADER_VERSION`.
3. Refuses to overwrite an existing versioned release.
4. Publishes the versioned ZIP and manifest only after those checks.

Tag pushes validate version consistency but **do not publish** or bypass
the readiness gate.

### Why the gate exists

CodaLoader `0.0.24` predates the verified launcher-identity contract.
Current development source requires CodaLauncher to pass a verified player
name, UUID and a Minecraft access token (or a previously verified offline
identity). The Microsoft app registration is not yet completed, and
CodaLauncher users may still have older launcher binaries.

Shipping this source automatically would risk disabling Play for those
users. Do not publish the newer loader merely to deliver menu layout
changes. Complete compatible launcher authentication or provide a separate
fully tested local-only play mode before publishing.

## Howling Whispers landing page

HW-Landing should offer **published releases** as player downloads. It
may link to CI development builds only with an explicit developer-only
label; they should never be called the latest player version.

The approved published assets are the only source for normal updater
version checks. Never substitute the latest `main` commit hash, an
Actions artifact or a GitHub source archive for a release ZIP.
