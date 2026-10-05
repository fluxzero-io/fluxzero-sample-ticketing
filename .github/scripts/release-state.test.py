#!/usr/bin/env python3
"""Exercise release ordering and reruns with actual Git history, without publishing."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location("release_state", Path(__file__).with_name("release-state.py"))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class ReleaseStateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.previous = os.getcwd()
        os.chdir(self.directory.name)
        self.addCleanup(self.directory.cleanup)
        self.addCleanup(os.chdir, self.previous)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "test@example.invalid")
        self.counter = 0
        self.first = self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", *args], text=True).strip()

    def commit(self):
        self.counter += 1
        self.git("-c", "commit.gpgsign=false", "commit", "-q", "--allow-empty", "-m", f"fix: sample change {self.counter}")
        commit = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/main", commit)
        return commit

    def test_first_release_starts_at_sample_version(self):
        self.assertEqual({"skip": "false", "version": "0.1.0"}, policy.release_state(self.first))

    def test_existing_history_uses_semantic_version_calculation(self):
        self.git("tag", "v0.1.0")
        self.assertEqual({"skip": "false", "version": ""}, policy.release_state(self.commit()))

    def test_rerun_reuses_annotated_tag(self):
        self.git("-c", "tag.gpgsign=false", "tag", "-a", "v0.1.0", "-m", "First release")
        self.assertEqual({"skip": "false", "version": "0.1.0"}, policy.release_state(self.first))

    def test_older_build_cannot_replace_newer_release(self):
        self.git("tag", "v0.1.0")
        self.commit()
        self.git("tag", "v0.2.0")
        self.assertEqual({"skip": "true", "version": ""}, policy.release_state(self.first))

    def test_unreleased_older_build_cannot_replace_newer_release(self):
        self.commit()
        self.git("tag", "v0.1.0")
        self.assertEqual("true", policy.release_state(self.first)["skip"])

    def test_latest_tag_on_divergent_history_is_rejected(self):
        self.git("checkout", "-q", "-b", "other")
        self.commit()
        self.git("tag", "v0.2.0")
        self.git("checkout", "-q", "main")
        with self.assertRaisesRegex(ValueError, "outside"):
            policy.release_state(self.commit())

    def test_non_main_commit_is_rejected(self):
        self.git("checkout", "-q", "-b", "feature")
        other = self.commit()
        self.git("update-ref", "refs/remotes/origin/main", self.first)
        with self.assertRaisesRegex(ValueError, "main"):
            policy.release_state(other)

    def test_multiple_tags_on_same_commit_are_rejected(self):
        self.git("tag", "v0.1.0")
        self.git("tag", "v0.1.1")
        with self.assertRaisesRegex(ValueError, "Multiple"):
            policy.release_state(self.first)

    def test_newer_commit_cannot_reuse_a_lower_version(self):
        self.git("tag", "v0.2.0")
        current = self.commit()
        self.git("tag", "v0.1.0")
        with self.assertRaisesRegex(ValueError, "lower version"):
            policy.release_state(current)

    def test_prerelease_tags_do_not_define_stable_versions(self):
        self.git("tag", "v0.1.0-rc.1")
        self.assertEqual("0.1.0", policy.release_state(self.first)["version"])


if __name__ == "__main__":
    unittest.main()
