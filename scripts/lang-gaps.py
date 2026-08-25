#!/usr/bin/env python3
"""Reports what each language is still missing, and folds translations back in.

  ./scripts/lang-gaps.py dump  <CODE> <out.json>   what CODE still needs, as flat key → English
  ./scripts/lang-gaps.py apply <CODE> <in.json>    merge flat key → translation into CODE.json
  ./scripts/lang-gaps.py status                    coverage for every language

Flat dot-keys in the transfer file, nested JSON in the language file. Translators work with
one key per line; the file on disk keeps the structure the loader expects.
"""
import json
import pathlib
import sys

LANGS = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/langs"


def flatten(node, prefix=""):
    out = {}
    for key, value in node.items():
        if key.startswith("_"):
            continue
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            out.update(flatten(value, path))
        else:
            out[path] = value
    return out


def load(code):
    return json.loads((LANGS / f"{code}.json").read_text(encoding="utf-8"))


def save(code, data):
    (LANGS / f"{code}.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def assign(root, dotted, value):
    parts = dotted.split(".")
    node = root
    for part in parts[:-1]:
        node = node.setdefault(part, {})
        if not isinstance(node, dict):
            raise SystemExit(f"{dotted}: {part} is a value, not a section")
    node[parts[-1]] = value


def codes():
    return sorted(p.stem for p in LANGS.glob("*.json") if p.stem != "EN")


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    command = sys.argv[1]
    english = flatten(load("EN"))

    if command == "status":
        for code in codes():
            have = flatten(load(code))
            missing = [k for k in english if k not in have]
            pct = 100 * (len(english) - len(missing)) // len(english)
            print(f"{code:6} {pct:3}%  missing {len(missing)}")
        return

    code, path = sys.argv[2], pathlib.Path(sys.argv[3])

    if command == "dump":
        have = flatten(load(code))
        gaps = {k: v for k, v in english.items() if k not in have}
        path.write_text(json.dumps(gaps, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{code}: {len(gaps)} keys to translate → {path}")
        return

    if command == "apply":
        incoming = json.loads(path.read_text(encoding="utf-8"))
        data = load(code)
        have = flatten(data)

        unknown = [k for k in incoming if k not in english]
        if unknown:
            raise SystemExit(f"{code}: keys not in EN.json: {unknown[:5]}")

        added = 0
        for key, value in incoming.items():
            if key in have:
                continue
            assign(data, key, value)
            added += 1

        save(code, data)
        still = len([k for k in english if k not in flatten(data)])
        print(f"{code}: +{added} translated, {still} still missing")
        return

    raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
