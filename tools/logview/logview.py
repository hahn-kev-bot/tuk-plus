#!/usr/bin/env python3
"""Read Tuk plus log files (PLAN.md section 9).

Input: .jsonl files, or the .zip that the app's "Share logs" makes.

Examples:
  logview.py tukplus-logs-20260927-1531.zip --sessions
  logview.py tukplus-logs-20260927-1531.zip --session k3x9qa
  logview.py 2026-09-27.jsonl --level W --tag net
  logview.py logs.zip --event http --grep transactions --full
"""
import argparse
import io
import json
import sys
import zipfile
from collections import OrderedDict

LEVELS = {"D": 0, "I": 1, "W": 2, "E": 3}
RESERVED = ("t", "s", "lvl", "tag", "ev")


def read_lines(paths):
    for path in paths:
        if path.endswith(".zip"):
            with zipfile.ZipFile(path) as z:
                for name in sorted(z.namelist()):
                    if name.endswith(".jsonl"):
                        with z.open(name) as f:
                            for line in io.TextIOWrapper(f, encoding="utf-8"):
                                yield name, line
        else:
            with open(path, encoding="utf-8") as f:
                for line in f:
                    yield path, line


def events(paths):
    for source, line in read_lines(paths):
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            print(f"[bad line in {source}] {line[:120]}", file=sys.stderr)
            continue
        if isinstance(event, dict):
            yield event


def short(value, full):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    if not full and len(text) > 160:
        text = text[:157] + "..."
    return text.replace("\n", "\\n") if not full else text


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+")
    parser.add_argument("--sessions", action="store_true", help="list sessions and stop")
    parser.add_argument("--session", help="only this session id")
    parser.add_argument("--level", default="D", choices=LEVELS.keys(), help="lowest level to show")
    parser.add_argument("--tag", help="only this tag, for example net")
    parser.add_argument("--event", help="only this event name, for example http")
    parser.add_argument("--grep", help="only events whose text contains this")
    parser.add_argument("--full", action="store_true", help="do not cut long values (bodies, stacks)")
    args = parser.parse_args()

    all_events = sorted(events(args.files), key=lambda e: e.get("t", ""))

    if args.sessions:
        sessions = OrderedDict()
        for e in all_events:
            s = sessions.setdefault(e.get("s"), {"first": e.get("t"), "last": e.get("t"), "count": 0, "warn": 0, "start": None})
            s["last"] = e.get("t")
            s["count"] += 1
            if LEVELS.get(e.get("lvl"), 0) >= 2:
                s["warn"] += 1
            if e.get("ev") == "session_start":
                s["start"] = e
        for sid, s in sessions.items():
            start = s["start"] or {}
            info = f"app {start.get('app_version', '?')} {start.get('build_type', '')} {start.get('git_commit', '')}".strip()
            print(f"{sid}  {s['first']} → {s['last']}  {s['count']} events, {s['warn']} warn/error  {info}")
        return

    min_level = LEVELS[args.level]
    for e in all_events:
        if args.session and e.get("s") != args.session:
            continue
        if LEVELS.get(e.get("lvl"), 0) < min_level:
            continue
        if args.tag and e.get("tag") != args.tag:
            continue
        if args.event and e.get("ev") != args.event:
            continue
        if args.grep and args.grep not in json.dumps(e, ensure_ascii=False):
            continue
        time = e.get("t", "")[11:23]
        fields = " ".join(f"{k}={short(v, args.full)}" for k, v in e.items() if k not in RESERVED)
        print(f"{time} {e.get('s', '?')} {e.get('lvl', '?')} {e.get('tag', '?'):5} {e.get('ev', '?')}  {fields}")


if __name__ == "__main__":
    main()
