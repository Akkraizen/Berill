import argparse
import re
import sys
from pathlib import Path

UNRELEASED = "## [Unreleased]"
VERSION = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def heading_index(lines, version):
    prefix = f"## [{version}]"
    for index, line in enumerate(lines):
        if line == prefix or line.startswith(prefix + " "):
            return index
    return None


def section_end(lines, start):
    for index in range(start + 1, len(lines)):
        if lines[index].startswith("## ") or re.match(r"^\[[^\]]+\]: ", lines[index]):
            return index
    return len(lines)


def anchor(heading):
    text = heading.lstrip("#").strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def release(path, version, date, repository):
    lines = path.read_text(encoding="utf-8").splitlines()
    if heading_index(lines, version) is not None:
        print(f"CHANGELOG.md already has a section for {version}")
        return
    start = next((i for i, line in enumerate(lines) if line == UNRELEASED), None)
    if start is None:
        fail(f"CHANGELOG.md has neither a [{version}] nor an [Unreleased] section")
    end = section_end(lines, start)
    if not any(line.strip() for line in lines[start + 1:end]):
        fail("The [Unreleased] section of CHANGELOG.md is empty")
    lines[start:start + 1] = [UNRELEASED, "", f"## [{version}] - {date}"]
    base = f"https://github.com/{repository}"
    link = next((i for i, line in enumerate(lines) if line.startswith("[Unreleased]: ")), None)
    if link is None:
        fail("CHANGELOG.md has no [Unreleased] comparison link")
    previous = re.search(r"/compare/(v\d+\.\d+\.\d+)\.\.\.HEAD$", lines[link])
    lines[link] = f"[Unreleased]: {base}/compare/v{version}...HEAD"
    target = f"{base}/compare/{previous.group(1)}...v{version}" if previous else f"{base}/releases/tag/v{version}"
    lines.insert(link + 1, f"[{version}]: {target}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Moved [Unreleased] to [{version}] - {date}")


def notes(path, version, repository, tag):
    lines = path.read_text(encoding="utf-8").splitlines()
    start = heading_index(lines, version)
    if start is None:
        fail(f"CHANGELOG.md has no section for {version}; run Prepare release or add ## [{version}] - YYYY-MM-DD")
    body = "\n".join(lines[start + 1:section_end(lines, start)]).strip()
    url = f"https://github.com/{repository}/blob/{tag}/CHANGELOG.md#{anchor(lines[start])}"
    print(f"{body}\n\n[Full changelog for {version}]({url})" if body else f"[Changelog for {version}]({url})")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["release", "notes"])
    parser.add_argument("version")
    parser.add_argument("--repository", required=True)
    parser.add_argument("--date")
    parser.add_argument("--tag")
    parser.add_argument("--file", default="CHANGELOG.md")
    args = parser.parse_args()
    if not VERSION.match(args.version):
        fail("Version must be MAJOR.MINOR.PATCH")
    path = Path(args.file)
    if args.command == "release":
        if not args.date:
            fail("--date is required")
        release(path, args.version, args.date, args.repository)
    else:
        notes(path, args.version, args.repository, args.tag or f"v{args.version}")


if __name__ == "__main__":
    main()
