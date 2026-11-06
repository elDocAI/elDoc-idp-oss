"""Offline regression tests for Maven artifact provenance (stdlib only)."""

import argparse
from datetime import date
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import license_inventory
from scripts.license_inventory import Context, pom_metadata


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=Path(__file__).parent)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.ctx = Context(self.root, {"maven": {
            "localRepository": str(self.root / "repository"),
            "snapshotRepository": {
                "id": "sonatype-snapshots", "url": "https://snapshots.example/repo/",
            },
        }}, argparse.Namespace(local_repository=None, no_download=False))
        self.directory = self.ctx.local_repository / "org/primefaces/primefaces/17.0.0-SNAPSHOT"
        self.directory.mkdir(parents=True)
        self.ctx.download = Mock(side_effect=AssertionError("Unexpected network access"))

    def metadata(self, entries):
        return "<metadata><versioning><snapshotVersions>" + "".join(
            "<snapshotVersion><extension>" + extension + "</extension>"
            + ("<classifier>" + classifier + "</classifier>" if classifier else "")
            + "<value>17.0.0-" + timestamp + "</value></snapshotVersion>"
            for extension, classifier, timestamp in entries
        ) + "</snapshotVersions></versioning></metadata>"

    def local_metadata(self, entries):
        (self.directory / "maven-metadata-sonatype-snapshots.xml").write_text(
            self.metadata(entries), encoding="utf-8"
        )

    def url(self, extension="jar", classifier=""):
        return self.ctx.artifact_url(
            "org.primefaces", "primefaces", "17.0.0-SNAPSHOT", extension, classifier
        )

    def test_local_resolved_snapshot_url_not_central_or_remote_latest(self):
        self.local_metadata([("jar", "", "20261027.010203-2")])
        self.assertEqual(self.url(),
                         "https://snapshots.example/repo/org/primefaces/primefaces/"
                         "17.0.0-SNAPSHOT/primefaces-17.0.0-20261027.010203-2.jar")
        self.ctx.download.assert_not_called()

    def test_extension_and_classifier_are_matched_independently(self):
        self.local_metadata([
            ("jar", "jakarta", "20261027.010203-1"),
            ("jar", "", "20261027.010203-2"),
            ("pom", "", "20261027.010203-3"),
        ])
        self.assertTrue(self.url("jar", "jakarta").endswith("-1-jakarta.jar"))
        self.assertTrue(self.url().endswith("-2.jar"))
        self.assertTrue(self.url("pom").endswith("-3.pom"))

    def test_release_url_unchanged(self):
        self.assertEqual(self.ctx.artifact_url("org.example", "demo", "1.2", "jar", "tests"),
                         "https://repo.maven.apache.org/maven2/org/example/demo/1.2/demo-1.2-tests.jar")

    def remote_metadata(self, entries):
        def download(url, destination):
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(self.metadata(entries) if url.endswith("maven-metadata.xml")
                                   else url, encoding="utf-8")
        self.ctx.download.side_effect = download

    def test_remote_fallback_ignores_stale_alias_and_cache(self):
        alias = self.directory / "primefaces-17.0.0-SNAPSHOT.jar"
        alias.write_bytes(b"old local bytes")
        stale = self.ctx.cache_dir / "org/primefaces/primefaces/17.0.0-SNAPSHOT/primefaces-17.0.0-SNAPSHOT.jar"
        stale.parent.mkdir(parents=True)
        stale.write_bytes(b"old cached bytes")
        self.remote_metadata([("jar", "", "20261028.010203-4")])
        result = self.ctx.artifact_file("org.primefaces", "primefaces", "17.0.0-SNAPSHOT", "jar", "")
        self.assertEqual(result.name, "primefaces-17.0.0-20261028.010203-4.jar")
        self.assertEqual(result.read_text(), self.url())
        self.assertEqual(self.ctx.download.call_count, 2)
        self.assertEqual(self.ctx.download.call_args_list[0].args[0],
                         "https://snapshots.example/repo/org/primefaces/primefaces/"
                         "17.0.0-SNAPSHOT/maven-metadata.xml")
        self.url()
        self.assertEqual(self.ctx.download.call_count, 2)

    def test_local_aliases_use_independent_jar_and_pom_metadata(self):
        self.local_metadata([("jar", "jakarta", "20261027.010203-1"),
                             ("pom", "", "20261027.010203-2")])
        jar = self.directory / "primefaces-17.0.0-SNAPSHOT-jakarta.jar"
        pom = self.directory / "primefaces-17.0.0-SNAPSHOT.pom"
        jar.write_bytes(b"jar")
        pom.write_bytes(b"pom")
        self.assertEqual(self.ctx.artifact_file("org.primefaces", "primefaces",
                                               "17.0.0-SNAPSHOT", "jar", "jakarta"), jar)
        self.assertEqual(self.ctx.pom_file("org.primefaces", "primefaces", "17.0.0-SNAPSHOT"), pom)
        self.assertTrue(self.url("jar", "jakarta").endswith("-1-jakarta.jar"))
        self.assertTrue(self.url("pom").endswith("-2.pom"))
        self.ctx.download.assert_not_called()

    def test_timestamped_local_file_preferred_over_alias(self):
        self.local_metadata([("jar", "", "20261027.010203-2")])
        timestamped = self.directory / "primefaces-17.0.0-20261027.010203-2.jar"
        timestamped.write_bytes(b"resolved")
        (self.directory / "primefaces-17.0.0-SNAPSHOT.jar").write_bytes(b"alias")
        self.assertEqual(self.ctx.artifact_file("org.primefaces", "primefaces",
                                               "17.0.0-SNAPSHOT", "jar", ""), timestamped)
        self.ctx.download.assert_not_called()

    def test_download_pom_uses_timestamped_cache_path(self):
        self.remote_metadata([("pom", "", "20261028.010203-4")])
        result = self.ctx.download_pom("org.primefaces", "primefaces", "17.0.0-SNAPSHOT")
        self.assertEqual(result.name, "primefaces-17.0.0-20261028.010203-4.pom")
        self.assertEqual(result.read_text(), self.url("pom"))

    def test_missing_classifier_falls_back_without_using_other_jar(self):
        self.local_metadata([("jar", "jakarta", "20261027.010203-1")])
        self.remote_metadata([("jar", "", "20261028.010203-4")])
        self.assertTrue(self.url().endswith("-4.jar"))
        self.assertEqual(self.ctx.download.call_count, 1)

    def test_malformed_local_metadata_falls_back(self):
        (self.directory / "maven-metadata-sonatype-snapshots.xml").write_text("<broken")
        self.remote_metadata([("jar", "", "20261028.010203-4")])
        self.assertTrue(self.url().endswith("-4.jar"))

    def test_unresolved_remote_metadata_is_a_clear_error(self):
        for data in ("<broken", self.metadata([("jar", "jakarta", "20261027.010203-1")]),
                     self.metadata([("jar", "", "SNAPSHOT")])):
            with self.subTest(data=data):
                self.ctx.download.side_effect = lambda url, dest: dest.write_text(data)
                with self.assertRaisesRegex(SystemExit, "Cannot resolve snapshot.*jar"):
                    self.url()

    def test_remote_download_failure_is_a_clear_error(self):
        self.ctx.download.side_effect = SystemExit("Failed to download metadata")
        with self.assertRaisesRegex(SystemExit, "Cannot resolve snapshot.*Failed to download"):
            self.url()

    def test_snapshot_requires_repository_configuration(self):
        self.ctx.snapshot_repository = {}
        with self.assertRaisesRegex(SystemExit, "configure maven.snapshotRepository"):
            self.url()

    def test_no_download_uses_local_metadata_and_alias(self):
        self.ctx.args.no_download = True
        self.ctx.download = Context.download.__get__(self.ctx)
        self.local_metadata([("jar", "", "20261027.010203-2")])
        alias = self.directory / "primefaces-17.0.0-SNAPSHOT.jar"
        alias.write_bytes(b"local")
        self.assertEqual(self.ctx.artifact_file("org.primefaces", "primefaces",
                                               "17.0.0-SNAPSHOT", "jar", ""), alias)

    def test_no_download_rejects_alias_without_metadata(self):
        self.ctx.args.no_download = True
        self.ctx.download = Context.download.__get__(self.ctx)
        (self.directory / "primefaces-17.0.0-SNAPSHOT.jar").write_bytes(b"unknown")
        with self.assertRaisesRegex(SystemExit, "Cannot resolve snapshot.*--no-download"):
            self.url()

    def test_snapshot_pom_resolution_error_is_not_silenced(self):
        self.ctx.download.side_effect = SystemExit("Cannot resolve snapshot POM")
        with self.assertRaisesRegex(SystemExit, "Cannot resolve snapshot.*POM"):
            pom_metadata(self.ctx, "org.primefaces", "primefaces", "17.0.0-SNAPSHOT")

    def test_missing_release_pom_remains_optional(self):
        self.ctx.download.side_effect = SystemExit("Missing release POM")
        pom_metadata(self.ctx, "org.example", "demo", "1.2")


class GenerationDateTests(unittest.TestCase):
    def test_update_renders_new_date_and_later_check_preserves_it(self):
        for action in (["--update"], []):
            with self.subTest(action=action), tempfile.TemporaryDirectory(
                dir=Path(__file__).parent
            ) as temporary:
                root = Path(temporary).resolve()
                config_path = root / "config.json"
                inventory_path = root / "inventory.csv"
                notices_path = root / "notices.md"
                (root / "template.md").write_text("Prepared {{DATE}}\n", encoding="utf-8")
                license_inventory.write_json(config_path, {
                    "generatedOn": "2026-10-20",
                    "maven": {"localRepository": str(root / "repository")},
                    "outputs": {
                        "inventory": "inventory.csv", "notices": "notices.md",
                        "noticesTemplate": "template.md",
                    },
                })
                with patch.object(license_inventory, "__file__", str(root / "scripts" / "license_inventory.py")), \
                        patch.object(license_inventory, "date", wraps=date) as clock, \
                        patch.object(license_inventory, "generate", return_value=([], [], [])), \
                        patch.object(Context, "property_value", return_value=""), \
                        patch.object(license_inventory, "log"):
                    clock.today.return_value = date(2026, 10, 28)
                    arguments = ["--config", "config.json", "--no-download", "--no-prune"]
                    self.assertEqual(license_inventory.main(action + arguments), 0)
                    self.assertEqual(license_inventory.read_json(config_path)["generatedOn"],
                                     "2026-10-28")
                    self.assertEqual(notices_path.read_text(encoding="utf-8"),
                                     "Prepared 28 October 2026\n")
                    before = [path.read_bytes() for path in (config_path, inventory_path, notices_path)]
                    clock.today.return_value = date(2026, 10, 29)
                    self.assertEqual(license_inventory.main(["--check"] + arguments), 0)
                    self.assertEqual([path.read_bytes() for path in (config_path, inventory_path, notices_path)],
                                     before)


if __name__ == "__main__":
    unittest.main()
