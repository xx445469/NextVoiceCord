#!/usr/bin/env python3
"""Folds the per-panel key fragments into EN.json under the "gui" section.

The panels were translated in parallel, each writing its own fragment rather than
editing EN.json directly — five processes appending to one JSON file would have
produced a file that only one of them recognised.
"""
import json
import pathlib
import sys

FRAG_DIR = pathlib.Path("/tmp/i18n-frag")
EN = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/langs/EN.json"


def main() -> int:
    en = json.loads(EN.read_text(encoding="utf-8"))
    gui = en.setdefault("gui", {})

    added, collided = 0, []

    for frag_path in sorted(FRAG_DIR.glob("*.json")):
        frag = json.loads(frag_path.read_text(encoding="utf-8"))
        for section, entries in frag.items():
            target = gui.setdefault(section, {})
            for key, value in entries.items():
                if key in target and target[key] != value:
                    collided.append(f"gui.{section}.{key}")
                    continue
                if key not in target:
                    added += 1
                target[key] = value

    # Sorted so a later diff shows what changed rather than where things moved to.
    en["gui"] = {s: dict(sorted(v.items())) for s, v in sorted(gui.items())}

    EN.write_text(json.dumps(en, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"added {added} keys to EN.json")
    if collided:
        print("COLLISIONS (kept existing value):", *collided, sep="\n  ")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
