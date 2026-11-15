"""Insert bytecode and keep the Android resource table stored and 4-byte aligned."""
import shutil
import struct
import sys
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

resources, dex_directory, destination = map(Path, sys.argv[1:])
with ZipFile(resources) as source, ZipFile(destination, "w") as apk:
    for entry in source.infolist():
        if entry.is_dir():
            continue
        target = ZipInfo(entry.filename)
        if entry.filename == "resources.arsc":
            target.compress_type = ZIP_STORED
            offset = apk.fp.tell() + 30 + len(entry.filename.encode("utf-8")) + 4
            padding = (-offset) % 4
            target.extra = struct.pack("<HH", 0xD935, padding) + bytes(padding)
        else:
            target.compress_type = ZIP_DEFLATED
        with source.open(entry) as reader, apk.open(target, "w") as writer:
            shutil.copyfileobj(reader, writer)
    for dex in sorted(dex_directory.glob("classes*.dex")):
        apk.write(dex, dex.name, compress_type=ZIP_DEFLATED)
