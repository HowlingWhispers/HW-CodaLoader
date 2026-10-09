#!/usr/bin/env python3
"""Fetch verified upstream catalogs; apply only the target's Identifier rename.

These sources exercise the real BCCE catalog, not a recreated implementation.
They are test inputs only and are never bundled in CodaLoader.jar.
"""
import hashlib
from pathlib import Path
import urllib.request

REVISION = 'b1b166d29da797abf6df3e0618a3bd62f14bc41e'
FILES = {
    'source-shared/src/main/java/buildcraft/lib/platform/registry/BCRegistryBinder.java': '1fbf38df11373e02a8b7535cb0e566e667e6926d57dd736a8e0693baea559d80',
    'source-shared/src/main/java/buildcraft/lib/platform/registry/BCDeferredRegister.java': '24576b7cf4aeaccb62f69558f37d00e0834229bcd3888be662a9b99d4cf00981',
    'source-shared/src/main/java/buildcraft/lib/platform/registry/BCRegistryEntry.java': '6982fa5229db1915f2050589226884ae14739e47bedf3c69b2a93921ab671071',
    'source-families/modern/src/main/java/buildcraft/lib/platform/registry/RegistryNames.java': 'ec9a7a6119c0bfe9916dcf3a3eff900245506f8f72702cc19f759aba10db307a',
    'LICENSE.txt': '1f256ecad192880510e84ad60474eab7589218784b9a50bc7ceee34c2b91f1d5',
}
root = Path('inspection/bcce')
for path, expected in FILES.items():
    data = urllib.request.urlopen(f'https://raw.githubusercontent.com/BCCE-team/BuildCraft/{REVISION}/{path}', timeout=45).read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise RuntimeError(f'BCCE source checksum mismatch: {path}')
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    if path.endswith('.java'):
        source = data.decode().replace('ResourceLocation', 'Identifier')
        if path.endswith('/RegistryNames.java'):
            # Select upstream's >=1.21.11 Stonecutter branch. Snapshot 3 also
            # uses identifier(), and no longer has the older location().
            source = source.replace('        //? } else {\n        return key.location().toString();\n', '')
        generated = root / 'mapped/buildcraft/lib/platform/registry' / target.name
        generated.parent.mkdir(parents=True, exist_ok=True)
        generated.write_text(source)
print(f'Verified BCCE catalog sources from {REVISION}; only Minecraft names adapted.')
