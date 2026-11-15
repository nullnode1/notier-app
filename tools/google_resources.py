"""Write resource ID classes for Google Play Services AARs after AAPT2 linking."""
import re
import sys
from pathlib import Path

symbols, target, *libraries = map(Path, sys.argv[1:])
final = {}
for row in symbols.read_text().splitlines():
    if match := re.match(r"(int(?:\[\])?) (\w+) (\w+) (.+)", row):
        kind, category, name, value = match.groups()
        final[category, name] = kind, value

for folder in libraries:
    manifest = (folder / "AndroidManifest.xml").read_text()
    namespace = re.search(r'package="([^"]+)"', manifest)[1]
    groups = {}
    for row in (folder / "R.txt").read_text().splitlines():
        _, category, name, _ = row.split(None, 3)
        if (category, name) in final:
            groups.setdefault(category, []).append(name)
    lines = [f"package {namespace}", "public object R {"]
    for category, names in groups.items():
        lines.append(f"    public object {category} {{")
        for name in names:
            kind, value = final[category, name]
            if kind == "int":
                lines.append(f"        public const val {name}: Int = {value}")
            else:
                values = value.strip("{} ").rstrip(",")
                lines.append(f"        @JvmField public val {name}: IntArray = intArrayOf({values})")
        lines.append("    }")
    lines.append("}")
    (target / f"{folder.name}-R.kt").write_text("\n".join(lines) + "\n")
