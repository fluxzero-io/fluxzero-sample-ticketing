#!/usr/bin/env python3
"""Select an immutable release or allow the standard semantic-version action to calculate one."""
import re
import subprocess
import sys

STABLE_TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def ancestor(older, newer):
    result = subprocess.run(["git", "merge-base", "--is-ancestor", older, newer], check=False)
    if result.returncode not in (0, 1):
        raise RuntimeError("Cannot determine release ancestry")
    return result.returncode == 0


def release_state(commit):
    commit = git("rev-parse", f"{commit}^{{commit}}")
    if not ancestor(commit, "origin/main"):
        raise ValueError("Only a verified commit from main may be released")
    tags = [tag for tag in git("tag", "--list").splitlines() if STABLE_TAG.fullmatch(tag)]
    current = [tag for tag in tags if git("rev-parse", f"refs/tags/{tag}^{{commit}}") == commit]
    if len(current) > 1:
        raise ValueError("Multiple stable tags identify this commit; refusing an ambiguous release")
    if tags:
        latest = max(tags, key=lambda tag: tuple(map(int, STABLE_TAG.fullmatch(tag).groups())))
        tagged_commit = git("rev-parse", f"refs/tags/{latest}^{{commit}}")
        if tagged_commit != commit and ancestor(commit, tagged_commit):
            return {"skip": "true", "version": ""}  # An older build finished after a newer release.
        if not ancestor(tagged_commit, commit):
            raise ValueError("Latest stable tag is outside this commit's release history")
        if current and current[0] != latest:
            raise ValueError("A lower version already identifies this commit")
    return {"skip": "false", "version": current[0][1:] if current else ("" if tags else "0.1.0")}


if __name__ == "__main__":
    for key, value in release_state(sys.argv[1]).items():
        print(f"{key}={value}")
