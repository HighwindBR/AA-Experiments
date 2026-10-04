#!/usr/bin/env python3
"""Generate conservative boolean getter candidates from apktool Smali output.

This desktop helper never modifies an APK. A candidate is emitted only when one method
contains exactly one requested stable flag literal and has a boolean no-argument return.
The generated JSON is evidence for review, not an auto-approved runtime profile.
"""
from __future__ import annotations
import argparse, hashlib, json, re
from pathlib import Path

METHOD = re.compile(r"(?ms)^\.method (?P<header>.*?)\n(?P<body>.*?)^\.end method")
FLAG = re.compile(r'const-string\s+\S+,\s+"([A-Za-z0-9_]+__[^"]+)"')

def scan(root: Path) -> dict:
    found = {}
    ambiguous = {}
    for path in root.rglob("*.smali"):
        text = path.read_text(encoding="utf-8", errors="replace")
        class_match = re.search(r"(?m)^\.class .*? L([^;]+);", text)
        if not class_match: continue
        cls = class_match.group(1).replace("/", ".")
        for match in METHOD.finditer(text):
            header, body = match.group("header"), match.group("body")
            if not re.search(r"\(\)Z$", header): continue
            name_match = re.search(r"([\w$<>]+)\(\)Z$", header)
            if not name_match: continue
            for flag in FLAG.findall(body):
                item = {"className": cls, "methodName": name_match.group(1), "descriptor": "()Z", "source": str(path)}
                if flag in found:
                    ambiguous.setdefault(flag, [found.pop(flag)]).append(item)
                elif flag in ambiguous:
                    ambiguous[flag].append(item)
                else: found[flag] = item
    return {"getters": found, "ambiguous": ambiguous}

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("smali_root", type=Path)
    p.add_argument("--base-apk", type=Path)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = scan(args.smali_root)
    result["schemaVersion"] = 1
    if args.base_apk:
        result["baseSha256"] = hashlib.sha256(args.base_apk.read_bytes()).hexdigest()
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True), encoding="utf-8")

if __name__ == "__main__": main()
