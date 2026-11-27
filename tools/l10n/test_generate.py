#!/usr/bin/env python3
"""Check bundled policies and the determinism of Android resource generation."""
import json, os, random, shutil, subprocess, sys, tempfile

from generate import FOLDER, string_texts

here = os.path.dirname(os.path.abspath(__file__))
locales = sys.argv[1]
res = os.path.join(here, "..", "..", "app", "src", "main", "res")


def snapshot():
    out = {}
    for d in sorted(os.listdir(res)):
        p = os.path.join(res, d, "strings.xml")
        if d.startswith("values") and d != "values-ko" and os.path.exists(p):
            out[d] = open(p, encoding="utf-8").read()
        if d.startswith("raw"):
            p = os.path.join(res, d, "sample_words.json")
            if os.path.exists(p):
                out[d] = open(p, encoding="utf-8").read()
    return out


# Compare the shipped legal text before generation can repair a stale resource.
# The English policy must be in Android's unqualified fallback folder.
korean = string_texts(res, "values-ko")
policy_keys = {rid: key for rid, key in korean.items() if rid == "terms" or rid.startswith(("privacy_", "terms_", "policy_"))}
for tag, folder in {**FOLDER, "ko": "values-ko", "en": "values"}.items():
    with open(os.path.join(locales, "site", f"{tag}.json"), encoding="utf-8") as site:
        approved = json.load(site)
    bundled = string_texts(res, folder)
    for rid, key in policy_keys.items():
        if key not in approved or bundled.get(rid) != approved[key]:
            raise SystemExit(f"{tag}: bundled {rid} differs from the web policy")
print(f"ok: {len(policy_keys)} policy strings match the web in every locale and the English fallback")

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

# A key missing in a later language (Japanese) after an earlier one (English) changed: the run
# fails and no file changes at all.
with tempfile.TemporaryDirectory() as tmp:
    broken = os.path.join(tmp, "locales")
    shutil.copytree(locales, broken)
    en = os.path.join(broken, "demo", "en.json")
    d = json.load(open(en, encoding="utf-8")); d["진동"] = "Vibration (changed)"
    json.dump(d, open(en, "w", encoding="utf-8"), ensure_ascii=False)
    ja = os.path.join(broken, "demo", "ja.json")
    d = json.load(open(ja, encoding="utf-8")); del d["진동"]
    json.dump(d, open(ja, "w", encoding="utf-8"), ensure_ascii=False)
    result = subprocess.run([sys.executable, os.path.join(here, "generate.py"), broken], capture_output=True, text=True)
    if result.returncode == 0:
        raise SystemExit("generation with a missing Japanese key should fail")
    if snapshot() != before:
        raise SystemExit("a failed generation changed some files")
print("ok: a missing key fails without touching any file")
