#!/usr/bin/env python3
"""
Writes app/src/main/res/values*/strings.xml for the 16 translated locales. English goes to the
default values/ folder, so any language outside the 17 falls back to English (as on the web);
Korean, the source, lives in values-ko/.

- Rows marked 그대로 in the Figma handoff (node 311-3097) are copied from the web dictionaries at
  the reference commit (elpalaiso/tokipin 9e512c1, locales/demo and locales/site). The web
  dictionaries are keyed by the Korean source sentence, so each resource is looked up by its own
  Korean text in values-ko (never by position: reordering a JSON file changes nothing). The TP ids
  below document the handoff row.
- Rows marked 조정 / 별도 come from tools/l10n/app_strings.py.
- Development-only strings (debug_*), the brand name and pure number formats are not translated.

Usage: python3 tools/l10n/generate.py <path to a checkout of the web repo's locales directory>
"""
import json, os, re, sys
from xml.sax.saxutils import escape

sys.path.insert(0, os.path.dirname(__file__))
from app_strings import APP, LANGS  # noqa: E402

WEB = {  # resource id -> (domain, TP number)
    "cd_close": ("demo", 3), "cd_search": ("demo", 55), "cd_new_record": ("demo", 74), "cd_view_menu": ("demo", 75),
    "view_menu_title": ("demo", 75), "cancel": ("demo", 80), "undo": ("demo", 84), "loading": ("site", 160),
    "settings": ("demo", 2), "settings_haptics": ("demo", 4), "settings_haptics_body": ("demo", 5),
    "settings_export": ("demo", 6), "settings_storage_title": ("demo", 8), "editor_cancel": ("demo", 80),
    "cd_delete": ("demo", 81), "item_delete": ("demo", 81), "kind_word": ("demo", 92), "kind_emoji": ("demo", 93),
    "kind_place": ("demo", 94), "kind_time": ("demo", 95), "editor_invalid_time": ("demo", 98),
    "editor_invalid_emoji": ("demo", 99), "cd_confirm_item": ("demo", 112), "cd_select_items": ("demo", 119),
    "cd_save_record": ("demo", 120), "move_up": ("demo", 124), "move_down": ("demo", 125),
    "editor_item_deleted": ("demo", 136), "editor_retry": ("site", 169), "today": ("demo", 72),
    "time_picker_cancel": ("demo", 80), "cd_add_time": ("demo", 133), "view_daily": ("demo", 47),
    "day_empty": ("demo", 69), "view_records": ("demo", 48), "view_month": ("demo", 49), "search_none": ("demo", 86),
    "search_hint": ("demo", 89), "cd_close_search": ("demo", 91), "privacy_policy": ("demo", 10), "terms": ("demo", 11),
    "policy_draft_badge": ("site", 175), "privacy_lead": ("site", 176), "privacy_h1": ("site", 177),
    "privacy_b1": ("site", 178), "privacy_h2": ("site", 179), "privacy_b2": ("site", 180), "privacy_h3": ("site", 181),
    "privacy_b3": ("site", 182), "privacy_h4": ("site", 183), "privacy_b4": ("site", 184), "privacy_h5": ("site", 185),
    "terms_lead": ("site", 188), "terms_h1": ("site", 189), "terms_b1": ("site", 190), "terms_h2": ("site", 191),
    "terms_b2": ("site", 192), "terms_h3": ("site", 193), "terms_b3": ("site", 194), "terms_h4": ("site", 195),
    "usage_label": ("demo", 348), "usage_prompt": ("demo", 349), "usage_body": ("demo", 350),
    "usage_share": ("demo", 351), "usage_later": ("demo", 352),
    "privacy_usage_body": ("site", 353), "privacy_usage_title": ("site", 354),
    # TP-0198 is split per tool on Android so each uses the web's own phrase (TP-0108…0111).
    "cd_tool_word": ("demo", 108), "cd_tool_emoji": ("demo", 109), "cd_tool_place": ("demo", 110), "cd_tool_time": ("demo", 111),
}

FOLDER = {  # English is the default (fallback) folder; Korean, the source, is values-ko
    "en": "values", "ja": "values-ja", "de": "values-de", "fr": "values-fr", "id": "values-in", "vi": "values-vi",
    "tr": "values-tr", "ru": "values-ru", "hi": "values-hi", "th": "values-th", "pt-BR": "values-pt-rBR",
    "es-ES": "values-es-rES", "es-419": "values-b+es+419", "zh-Hans": "values-b+zh+Hans",
    "zh-Hant-TW": "values-b+zh+Hant+TW", "zh-Hant-HK": "values-b+zh+Hant+HK",
}


def android(text: str) -> str:
    s = escape(text).replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def source_names(res: str):
    """Every user-facing name in the Korean source (values-ko); debug_* and untranslatable excluded."""
    text = open(os.path.join(res, "values-ko", "strings.xml"), encoding="utf-8").read()
    names = re.findall(r'<(?:string|plurals) name="([^"]+)"(?![^>]*translatable="false")', text)
    return [n for n in names if not n.startswith("debug_")]


def source_texts(res: str):
    """Korean text of each plain string in values-ko, unescaped like the web keys."""
    text = open(os.path.join(res, "values-ko", "strings.xml"), encoding="utf-8").read()
    out = {}
    for name, value in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S):
        out[name] = value.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"').replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return out


def main(locales: str, res: str):
    expected = set(source_names(res))
    produced = set(WEB) | set(APP)
    missing, extra = sorted(expected - produced), sorted(produced - expected)
    if missing or extra:
        raise SystemExit(f"Korean source and translations differ.\n  missing translations: {missing}\n  not in the source: {extra}")
    for rid, values in APP.items():
        if len(values) != len(LANGS):
            raise SystemExit(f"{rid}: {len(values)} values for {len(LANGS)} languages")
    korean = source_texts(res)
    # Every language is checked and prepared first; files are written only when all of them pass,
    # so a missing key in one language never leaves the others half regenerated.
    outputs = {}
    for i, lang in enumerate(LANGS):
        dicts = {d: json.load(open(os.path.join(locales, d, f"{lang}.json"), encoding="utf-8")) for d in ("demo", "site")}
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               f"<!-- Generated by tools/l10n/generate.py ({lang}). Edit app_strings.py or the web dictionaries, not this file. -->",
               "<resources>"]
        for rid, (domain, tp) in WEB.items():
            key = korean[rid]
            if key not in dicts[domain]:
                raise SystemExit(f"{rid} (TP-{tp:04d}): {key!r} is not a key of locales/{domain}/{lang}.json")
            value = dicts[domain][key]
            if not isinstance(value, str) or "{" in value:
                raise SystemExit(f"{rid}: web value is not a plain string: {value!r}")
            out.append(f'    <string name="{rid}">{android(value)}</string>')
        for rid, values in APP.items():
            value = values[i]
            if isinstance(value, dict):
                out.append(f'    <plurals name="{rid}">')
                for q in ("zero", "one", "two", "few", "many", "other"):
                    if q in value:
                        out.append(f'        <item quantity="{q}">{android(value[q])}</item>')
                out.append("    </plurals>")
            else:
                out.append(f'    <string name="{rid}">{android(value)}</string>')
        out.append("</resources>\n")
        outputs[os.path.join(res, FOLDER[lang], "strings.xml")] = "\n".join(out)
    for path, text in outputs.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    main(sys.argv[1], os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res"))
