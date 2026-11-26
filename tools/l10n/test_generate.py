#!/usr/bin/env python3
"""Reordering the web dictionaries must not change any generated resource (review on PR #1)."""
import json, os, random, shutil, subprocess, sys, tempfile

here = os.path.dirname(os.path.abspath(__file__))
locales = sys.argv[1]
res = os.path.join(here, "..", "..", "app", "src", "main", "res")


def snapshot():
    out = {}
    for d in sorted(os.listdir(res)):
        p = os.path.join(res, d, "strings.xml")
        if d.startswith("values") and d != "values-ko" and os.path.exists(p):
            out[d] = open(p, encoding="utf-8").read()
    return out


subprocess.run([sys.executable, os.path.join(here, "generate.py"), locales], check=True)
before = snapshot()
with tempfile.TemporaryDirectory() as tmp:
    shuffled = os.path.join(tmp, "locales")
    shutil.copytree(locales, shuffled)
    rng = random.Random(7)
    for root, _, files in os.walk(shuffled):
        for f in files:
            p = os.path.join(root, f)
            items = list(json.load(open(p, encoding="utf-8")).items())
            rng.shuffle(items)
            json.dump(dict(items), open(p, "w", encoding="utf-8"), ensure_ascii=False)
    subprocess.run([sys.executable, os.path.join(here, "generate.py"), shuffled], check=True)
after = snapshot()
subprocess.run([sys.executable, os.path.join(here, "generate.py"), locales], check=True)
changed = [d for d in before if before[d] != after.get(d)]
if changed:
    raise SystemExit(f"shuffling the web dictionaries changed {changed}")
print(f"ok: {len(before)} folders unchanged after shuffling every web dictionary")
