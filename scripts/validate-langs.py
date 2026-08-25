#!/usr/bin/env python3
"""
Validate NextVoiceCord translation files against the English source.

Missing or malformed translations are close to invisible at runtime: per-key fallback
means a broken file still renders, just in English, and a bad placeholder only shows up
as literal "{1}" in a message somebody eventually screenshots. This check turns those
into build-time failures.

Checks per language:
  1. no orphan keys           - keys English does not define are unreachable, and usually
                                mean a key was renamed in English while the translation
                                kept the old spelling
  2. placeholder parity       - the SET of {n} indices must match English exactly; order
                                may differ, because word order does
  3. markdown parity          - ** and ` counts must match, since an unclosed pair eats
                                the rest of the message in Discord
  4. untranslated literals    - command names, config paths and enum values must survive
                                verbatim; translating `/settc` or `linear` breaks the
                                instruction it appears in
  5. metadata present         - _meta.reviewed must exist, so review status is explicit

Usage:
    python3 scripts/validate-langs.py [langs_dir]

Exit code 0 if every file passes, 1 otherwise.
"""

import json
import pathlib
import re
import sys

PLACEHOLDER = re.compile(r"\{(\d{1,2})\}")

# Text that must appear byte-identical in every language. These are things a user types
# or a machine parses, not prose: translating them produces instructions that do not work.
LITERALS = re.compile(
    r"(/[a-z]+\b"                      # slash commands: /settc, /play
    r"|`(?:off|all|single|linear|fair|full|minimal|inherit"
    r"|on|ONLINE|IDLE|DND|INVISIBLE)`"  # enum values shown in code spans
    r"|playback\.maxHistorySize"        # config paths
    r"|ui\.language"
    r"|HH:MM:SS|MM:SS)"
)

SOURCE = "EN"


def flatten(node, prefix=""):
    """Collapse nested objects to dot-keys, skipping _-prefixed metadata."""
    out = {}
    for key, value in node.items():
        if not prefix and key.startswith("_"):
            continue
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            out.update(flatten(value, path))
        elif isinstance(value, str):
            out[path] = value
    return out


def check(code, translated, english, raw):
    problems = []

    orphans = sorted(set(translated) - set(english))
    if orphans:
        problems.append(f"{len(orphans)} orphan key(s) English does not define: "
                        + ", ".join(orphans[:5]) + ("..." if len(orphans) > 5 else ""))

    meta = raw.get("_meta", {})
    if "reviewed" not in meta:
        problems.append("_meta.reviewed missing — review status must be explicit")

    for key, source_text in english.items():
        text = translated.get(key)
        if text is None:
            continue  # absent is fine; it falls back to English

        expected = set(PLACEHOLDER.findall(source_text))
        actual = set(PLACEHOLDER.findall(text))
        if expected != actual:
            problems.append(
                f"{key}: placeholders {sorted(expected)} in {SOURCE} but {sorted(actual)} here")

        for marker in ("**", "`"):
            if source_text.count(marker) != text.count(marker):
                problems.append(
                    f"{key}: {marker!r} appears {source_text.count(marker)}x in {SOURCE} "
                    f"but {text.count(marker)}x here — an unclosed pair eats the message")

        for literal in set(LITERALS.findall(source_text)):
            if literal not in text:
                problems.append(f"{key}: {literal!r} was translated; it must stay verbatim")

        if source_text.count("\n") != text.count("\n"):
            problems.append(f"{key}: line-break count differs from {SOURCE}")

    return problems


def main():
    langs_dir = pathlib.Path(sys.argv[1] if len(sys.argv) > 1
                             else "src/main/resources/langs")
    source_file = langs_dir / f"{SOURCE}.json"
    if not source_file.exists():
        print(f"FAIL: source language file missing: {source_file}")
        return 1

    english = flatten(json.loads(source_file.read_text(encoding="utf-8")))
    print(f"{SOURCE}: {len(english)} keys (source)\n")

    failed = False
    for path in sorted(langs_dir.glob("*.json")):
        code = path.stem
        if code == SOURCE:
            continue

        raw = json.loads(path.read_text(encoding="utf-8"))
        translated = flatten(raw)
        problems = check(code, translated, english, raw)

        coverage = len(set(translated) & set(english)) * 100 // len(english)
        reviewed = raw.get("_meta", {}).get("reviewed")
        badge = "reviewed" if reviewed else "UNREVIEWED"

        if problems:
            failed = True
            print(f"FAIL {code}  {coverage}% coverage, {badge}")
            for problem in problems:
                print(f"       - {problem}")
        else:
            print(f"ok   {code}  {coverage}% coverage, {badge}")

    print()
    print("FAILED" if failed else "All translation files pass.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
