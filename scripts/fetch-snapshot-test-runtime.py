#!/usr/bin/env python3
"""Download Mojang's exact Snapshot 3 client and libraries for headless CI; verify every published hash."""
import concurrent.futures, hashlib, json, pathlib, sys, urllib.request
root = pathlib.Path(sys.argv[1]).resolve(); root.mkdir(parents=True, exist_ok=True)
def read(url):
    with urllib.request.urlopen(url,timeout=60) as response: return response.read()
manifest=json.loads(read('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'))
version=next(v for v in manifest['versions'] if v['id']=='26.4-snapshot-3')
raw=read(version['url']); assert hashlib.sha1(raw).hexdigest()==version['sha1'], 'Version metadata hash mismatch'
metadata=json.loads(raw); (root/'version.json').write_bytes(raw)
def download(asset,path):
    if path.exists() and hashlib.sha1(path.read_bytes()).hexdigest()==asset['sha1']: return
    data=read(asset['url']); assert hashlib.sha1(data).hexdigest()==asset['sha1'], 'Minecraft artifact hash mismatch'
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
client=metadata['downloads']['client']
assert client['sha1']=='96611d3cbefeae43527552fd008a7031b6f46e71', 'Unexpected Snapshot 3 client identity'
download(client,root/'client.jar')
assets=[lib['downloads']['artifact'] for lib in metadata['libraries'] if 'artifact' in lib.get('downloads',{})]
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    list(pool.map(lambda a:download(a,root/'libraries'/a['path']),assets))
print('PASS: checksum-verified Minecraft 26.4 Snapshot 3 client and',len(assets),'libraries')
