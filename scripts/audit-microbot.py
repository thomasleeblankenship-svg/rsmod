#!/usr/bin/env python3
"""Inventory source dependencies; not a Java symbol resolver or compatibility proof."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("runemobile", type=Path)
parser.add_argument("microbot", type=Path)
args = parser.parse_args()

def sha(root):
    return subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()

root = args.microbot / "runelite-client/src/main/java"
sources = {}
for path in sorted(root.rglob("*.java")):
    text = path.read_text(errors="replace")
    package = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    if package:
        sources[package[1] + "." + path.stem] = (path, text)

graph = {}
for name, (path, text) in sources.items():
    imports = re.findall(r"^import\s+(?:static\s+)?([\w.*]+)\s*;", text, re.M)
    dependencies = set()
    unresolved = []
    for item in imports:
        candidate = item
        while candidate and candidate not in sources:
            candidate = candidate.rpartition(".")[0]
        if candidate:
            dependencies.add(candidate)
        elif item.endswith(".*"):
            dependencies.update(n for n in sources if n.rpartition(".")[0] == item[:-2])
        else:
            unresolved.append(item)
    package = name.rpartition(".")[0]
    tokens = set(re.findall(r"\b[A-Za-z_$][\w$]*\b", text))
    dependencies.update(n for n in sources if n.rpartition(".")[0] == package and n.rpartition(".")[2] in tokens)
    dependencies.discard(name)
    graph[name] = {"source_dependencies": sorted(dependencies), "external_or_unresolved_imports": unresolved}

def closure(name):
    seen, pending = set(), [name]
    while pending:
        current = pending.pop()
        if current in seen or current not in graph:
            continue
        seen.add(current)
        pending.extend(graph[current]["source_dependencies"])
    return sorted(seen)

api_diffs = {}
for module in ["runelite-api", "runelite-client", "runelite-mixins"]:
    def files(base):
        return {str(p.relative_to(base)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in base.rglob("*.java")} if base.exists() else {}
    mobile = files(args.runemobile / module / "src/main/java")
    desktop = files(args.microbot / module / "src/main/java")
    api_diffs[module] = {
        "mobile_only": sorted(mobile.keys() - desktop.keys()),
        "desktop_only": sorted(desktop.keys() - mobile.keys()),
        "different": sorted(n for n in mobile.keys() & desktop.keys() if mobile[n] != desktop[n]),
        "identical_count": sum(mobile[n] == desktop[n] for n in mobile.keys() & desktop.keys()),
    }

entries = ["net.runelite.client.plugins.microbot." + n for n in
           ["Script", "Microbot", "statemachineexample.StateMachineExampleScript"]]
print(json.dumps({
    "runemobile_sha": sha(args.runemobile), "microbot_sha": sha(args.microbot),
    "analysis": "Import and same-package lexical graph; wildcard imports conservative; reflection, fully qualified body references and runtime service wiring may be missing. Not a complete compiler dependency graph.",
    "module_source_differences": api_diffs,
    "entry_closures": {name: closure(name) for name in entries},
    "graph": {name: graph[name] for name in sorted(set().union(*(set(closure(e)) for e in entries)))},
}, indent=2))
