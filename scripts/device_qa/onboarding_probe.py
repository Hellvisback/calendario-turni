#!/usr/bin/env python3
"""Repeat onboarding checks on the separate, disposable setupqa installation.

This tool cannot target the personal app. Captures require setupqa to be resumed;
state records contain counts and hashes, never a copy of notes or the database.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

PACKAGE = "com.turni.calendario.setupqa"
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / "qa-artifacts" / "onboarding"
BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def assert_qa_package(package: str) -> None:
    if package != PACKAGE or not package.endswith(".setupqa"):
        raise ValueError("Only the separate com.turni.calendario.setupqa package is allowed")


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    match = BOUNDS_PATTERN.fullmatch(value)
    if not match:
        raise ValueError(f"Invalid UI bounds: {value!r}")
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise ValueError(f"Empty UI bounds: {value!r}")
    return left, top, right, bottom


def artifact_path(output: Path, name: str, suffix: str) -> Path:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,95}", name):
        raise ValueError("Artifact names may contain letters, digits, underscores, dashes and dots only")
    for extension in (".png", ".xml", ".json"):
        if name.endswith(extension):
            name = name[:-len(extension)]
            break
    output.mkdir(parents=True, exist_ok=True)
    return output / (name + suffix)


def canonical_hash(value: object) -> str:
    payload = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def semantic_calendar(database: dict) -> dict:
    # Date-map ordering is irrelevant; assignment order within each date is meaningful.
    shifts: dict[str, list] = {}
    for assignment in database.get("calendarShifts", []):
        shifts.setdefault(assignment["date"], []).append(assignment)
    extras = {entry["date"]: entry for entry in database.get("dayExtraInfo", [])}
    return {"shifts": shifts, "extras": extras}


class Probe:
    def __init__(self, args: argparse.Namespace):
        self.package = PACKAGE
        assert_qa_package(self.package)
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        sdk_adb = str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")) if sdk else None
        self.executable = os.environ.get("ADB") or shutil.which("adb") or sdk_adb
        if not self.executable:
            raise ValueError("Set ADB or ANDROID_HOME, or put adb on PATH")
        self.serial = args.serial
        self.timeout = args.timeout
        self.output = Path(args.output).expanduser().resolve()

    def adb(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess:
        result = subprocess.run(
            [self.executable, "-s", self.serial, *arguments],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=self.timeout,
        )
        if check and result.returncode:
            error = result.stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"ADB command failed ({result.returncode}): {error}")
        return result

    def guard_input(self) -> None:
        assert_qa_package(self.package)
        activities = self.adb("shell", "dumpsys", "activity", "activities").stdout.decode("utf-8", errors="replace")
        resumed = [line for line in activities.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line]
        if not any(re.search(r"\b" + re.escape(self.package) + r"/", line) for line in resumed):
            raise RuntimeError("Bring the setupqa app to the foreground before input or capture; no action performed")

    def nodes(self, name: str = "ui") -> list[ET.Element]:
        self.guard_input()
        remote = "/sdcard/turnisync-onboarding-qa.xml"
        assert_qa_package(self.package)
        # UIAutomator can report an idle timeout with exit code 0. Never reuse its previous dump.
        self.adb("shell", "rm", "-f", remote)
        self.adb("shell", "uiautomator", "dump", remote)
        raw = self.adb("shell", "cat", remote).stdout
        root = ET.fromstring(raw)
        nodes = list(root.iter("node"))
        if not any(node.get("package") == self.package for node in nodes):
            raise RuntimeError("UI dump does not contain setupqa; capture refused")
        artifact_path(self.output, name, ".xml").write_bytes(raw)
        return nodes

    def capture(self, name: str, screenshot_only: bool = False) -> None:
        if not screenshot_only:
            self.nodes(name)
        self.guard_input()
        png = self.adb("exec-out", "screencap", "-p").stdout
        if not png.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("ADB did not return a PNG screenshot")
        path = artifact_path(self.output, name, ".png")
        path.write_bytes(png)
        print(path)

    def input_point(self, args: argparse.Namespace) -> tuple[int, int]:
        if args.xy:
            if args.label:
                raise ValueError("Choose an exact label or --xy coordinates, not both")
            x, y = args.xy
            if x < 0 or y < 0:
                raise ValueError("Coordinates must be nonnegative")
            return x, y
        if not args.label:
            raise ValueError("Provide an exact text/content-description label or --xy X Y")
        matches = []
        seen = set()
        for node in self.nodes():
            if node.get("package") != self.package or node.get("enabled") == "false":
                continue
            if args.label not in (node.get("text"), node.get("content-desc")):
                continue
            bounds = node.get("bounds", "")
            if bounds not in seen:
                matches.append(parse_bounds(bounds))
                seen.add(bounds)
        if not matches:
            raise ValueError(f"No setupqa control exactly matches {args.label!r}; use list")
        if args.index is None and len(matches) > 1:
            raise ValueError(f"{len(matches)} controls match; choose --index 0..{len(matches)-1} or --xy")
        index = args.index if args.index is not None else 0
        if not 0 <= index < len(matches):
            raise ValueError("Control index is out of range")
        left, top, right, bottom = matches[index]
        return (left + right) // 2, (top + bottom) // 2

    def tap(self, args: argparse.Namespace, long_press: bool = False) -> None:
        point = self.input_point(args)
        self.guard_input()
        assert_qa_package(self.package)
        x, y = map(str, point)
        if long_press:
            self.adb("shell", "input", "swipe", x, y, x, y, str(args.duration))
        else:
            self.adb("shell", "input", "tap", x, y)

    def private_file(self, path: str) -> bytes | None:
        assert_qa_package(self.package)
        result = self.adb("exec-out", "run-as", self.package, "cat", path, check=False)
        if result.returncode:
            error = (result.stderr + result.stdout).decode("utf-8", errors="replace")
            if "No such file or directory" in error:
                return None
            raise RuntimeError("Cannot read setupqa private state; install a debuggable -PsetupProbe build")
        return result.stdout

    def state(self, name: str, compare: str | None) -> None:
        raw = self.private_file("files/turnisync-database-v3.json")
        database = json.loads(raw) if raw else {}
        semantic = semantic_calendar(database)
        prefs_raw = self.private_file("shared_prefs/turnisync_first_run_v1.xml")
        first_run = {}
        if prefs_raw:
            for node in ET.fromstring(prefs_raw):
                if node.get("name") in ("state", "step"):
                    value = node.get("value", node.text)
                    first_run[node.get("name")] = int(value) if node.tag == "int" else value
        preferences = database.get("appPreferences", {})
        summary = {
            "package": self.package, "serial": self.serial,
            "capturedAt": datetime.now(timezone.utc).isoformat(),
            "databaseExists": raw is not None, "firstRun": first_run,
            "counts": {
                "assignments": len(database.get("calendarShifts", [])),
                "assignedDates": len(semantic["shifts"]),
                "dayExtras": len(database.get("dayExtraInfo", [])),
                "models": len(database.get("shiftDefinitions", [])),
                "rotations": len(database.get("rotations", [])),
                "workProfiles": len(database.get("workProfiles", [])),
                "preferenceKeys": len(preferences),
            },
            "preferences": {key: preferences[key] for key in ("themeMode", "weekStartMonday", "timeFormat24h") if key in preferences},
            "hashes": {
                "shifts": canonical_hash(semantic["shifts"]),
                "extras": canonical_hash(semantic["extras"]),
                "calendar": canonical_hash(semantic),
            },
        }
        if compare:
            baseline = json.loads(Path(compare).read_text(encoding="utf-8"))
            assert_qa_package(baseline.get("package", ""))
            summary["calendarUnchanged"] = summary["hashes"] == baseline.get("hashes")
        target = artifact_path(self.output, name, ".json")
        target.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        print(f"State summary: {target}")
        if compare and not summary["calendarUnchanged"]:
            raise RuntimeError("Setupqa assignments or day extras changed; inspect the QA flow")

    def reset(self, launch: bool) -> None:
        assert_qa_package(self.package)
        result = self.adb("shell", "pm", "clear", self.package).stdout.decode("utf-8").strip()
        if result != "Success":
            raise RuntimeError("setupqa reset was not confirmed")
        if launch:
            self.adb("shell", "am", "start", "-n", self.package + "/com.turni.calendario.MainActivity")
        print("Only setupqa was cleared" + (" and launched" if launch else ""))


def parser() -> argparse.ArgumentParser:
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    cli.add_argument("--timeout", type=int, default=120, help="Timeout per ADB command in seconds (default: 120)")
    cli.add_argument("--output", default=os.environ.get("TURNISYNC_ONBOARDING_QA_DIR", str(DEFAULT_OUTPUT)))
    commands = cli.add_subparsers(dest="command", required=True)
    listing = commands.add_parser("list", help="Save XML and list setupqa labels, descriptions and bounds")
    listing.add_argument("--name", default="ui")
    for name in ("capture", "shot", "dump"):
        commands.add_parser(name, help={"capture": "Save XML and PNG", "shot": "Save PNG", "dump": "Save XML"}[name]).add_argument("name")
    for name in ("tap", "long"):
        tap = commands.add_parser(name, help="Tap or long-press an exact label, or --xy X Y")
        tap.add_argument("label", nargs="?")
        tap.add_argument("--xy", type=int, nargs=2, metavar=("X", "Y"))
        tap.add_argument("--index", type=int, help="Zero-based choice when exact labels repeat")
        if name == "long":
            tap.add_argument("--duration", type=int, default=850, help="Press duration in milliseconds")
    swipe = commands.add_parser("swipe", help="Swipe within the setupqa app")
    swipe.add_argument("coordinates", nargs=4, type=int, metavar=("X1", "Y1", "X2", "Y2"))
    swipe.add_argument("--duration", type=int, default=500)
    state = commands.add_parser("state", help="Save counts, safe preferences and calendar hashes")
    state.add_argument("--name", default="state")
    state.add_argument("--compare", help="Baseline state JSON; fail if shifts/extras changed")
    reset = commands.add_parser("reset", help="Explicitly clear ONLY setupqa (never the personal app)")
    reset.add_argument("--launch", action="store_true", help="Launch onboarding after clearing setupqa")
    return cli


def main() -> int:
    args = parser().parse_args()
    if args.timeout <= 0:
        raise ValueError("Timeout must be positive")
    if hasattr(args, "duration") and args.duration < 1:
        raise ValueError("Gesture duration must be positive")
    probe = Probe(args)
    if args.command == "list":
        for node in probe.nodes(args.name):
            if node.get("package") == PACKAGE and (node.get("text") or node.get("content-desc")):
                print(json.dumps({key: node.get(key, "") for key in ("text", "content-desc", "bounds", "clickable", "enabled")}, ensure_ascii=False))
    elif args.command == "dump":
        probe.nodes(args.name)
        print(artifact_path(probe.output, args.name, ".xml"))
    elif args.command in ("capture", "shot"):
        probe.capture(args.name, screenshot_only=args.command == "shot")
    elif args.command in ("tap", "long"):
        probe.tap(args, long_press=args.command == "long")
    elif args.command == "swipe":
        if min(args.coordinates) < 0:
            raise ValueError("Coordinates must be nonnegative")
        probe.guard_input()
        assert_qa_package(probe.package)
        probe.adb("shell", "input", "swipe", *map(str, args.coordinates), str(args.duration))
    elif args.command == "state":
        probe.state(args.name, args.compare)
    elif args.command == "reset":
        probe.reset(args.launch)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, subprocess.TimeoutExpired, OSError, ET.ParseError) as error:
        print(f"Onboarding QA: {error}", file=sys.stderr)
        raise SystemExit(1)
