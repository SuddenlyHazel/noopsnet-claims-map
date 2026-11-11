#!/usr/bin/env python3
"""Fetch checksum-pinned compile dependencies; none are bundled in our addon."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache/dependencies'

def fetch(record):
    target = CACHE / record['filename']
    data = target.read_bytes() if target.exists() else b''
    if len(data) != record['bytes'] or hashlib.sha512(data).hexdigest() != record['sha512']:
        request = urllib.request.Request(record['url'], headers={'User-Agent':'NoopsNet-ClaimMaps/0.1'})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if len(data) != record['bytes'] or hashlib.sha512(data).hexdigest() != record['sha512']:
            raise RuntimeError('Checksum mismatch: ' + record['filename'])
        target.write_bytes(data)
    return record['filename'], data

def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    records = json.loads((ROOT / 'dependencies.json').read_text())
    with ThreadPoolExecutor(max_workers=3) as executor:
        downloaded = list(executor.map(fetch, records))
    nested = {}
    for filename, data in downloaded:
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            for entry in jar.namelist():
                if entry.startswith('META-INF/jarjar/xaerolib-') and entry.endswith('.jar'):
                    name = Path(entry).name
                    value = jar.read(entry)
                    if name in nested and nested[name] != value:
                        raise RuntimeError('Conflicting nested XaeroLib artifacts')
                    nested[name] = value
    for name, data in nested.items():
        (CACHE / name).write_bytes(data)
    print('Verified Flan, both Xaero maps, and nested XaeroLib compile dependencies')

if __name__ == '__main__':
    main()
