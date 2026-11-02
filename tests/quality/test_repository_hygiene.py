"""Toolchain-independent repository quality checks."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ElementTree


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NAMESPACE}}}name"
ANDROID_EXPORTED = f"{{{ANDROID_NAMESPACE}}}exported"


class RepositoryHygieneTests(unittest.TestCase):
    def test_android_xml_files_are_well_formed(self):
        xml_files = [REPOSITORY_ROOT / "AndroidManifest.xml"]
        xml_files.extend(sorted((REPOSITORY_ROOT / "res").rglob("*.xml")))
        self.assertGreater(len(xml_files), 0)

        failures = []
        for xml_file in xml_files:
            try:
                ElementTree.parse(xml_file)
            except ElementTree.ParseError as error:
                failures.append(f"{xml_file.relative_to(REPOSITORY_ROOT)}: {error}")

        self.assertEqual([], failures, "\n".join(failures))

    def test_manifest_components_with_intent_filters_declare_exported(self):
        manifest = ElementTree.parse(REPOSITORY_ROOT / "AndroidManifest.xml")
        expected = {
            ".ui.NotesListActivity": "true",
            ".ui.NoteEditActivity": "true",
            ".widget.NoteWidgetProvider_2x": "false",
            ".widget.NoteWidgetProvider_4x": "false",
            ".ui.AlarmInitReceiver": "false",
        }

        actual = {}
        for tag in ("activity", "receiver", "service"):
            for component in manifest.findall(f".//{tag}"):
                if component.find("intent-filter") is not None:
                    actual[component.get(ANDROID_NAME)] = component.get(
                        ANDROID_EXPORTED
                    )

        self.assertEqual(expected, actual)

    def test_all_pending_intents_declare_mutability(self):
        java_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted((REPOSITORY_ROOT / "src").rglob("*.java"))
        )
        calls = re.findall(
            r"PendingIntent\.get(?:Activity|Broadcast|Service)\([^;]+;",
            java_sources,
            re.DOTALL,
        )

        self.assertGreater(len(calls), 0)
        self.assertEqual(
            [],
            [
                call
                for call in calls
                if "FLAG_IMMUTABLE" not in call and "pendingFlags" not in call
            ],
        )
        self.assertIn(
            "pendingFlags |= PendingIntent.FLAG_IMMUTABLE",
            java_sources,
        )

    def test_no_merge_conflict_markers_in_source_or_resources(self):
        markers = ("<<<<<<< ", "=======", ">>>>>>> ")
        failures = []
        candidate_files = [
            *REPOSITORY_ROOT.rglob("*.java"),
            *REPOSITORY_ROOT.rglob("*.xml"),
        ]

        for candidate in candidate_files:
            for line_number, line in enumerate(
                candidate.read_text(encoding="utf-8").splitlines(), start=1
            ):
                if any(line.startswith(marker) for marker in markers):
                    failures.append(
                        f"{candidate.relative_to(REPOSITORY_ROOT)}:{line_number}"
                    )

        self.assertEqual([], failures, "\n".join(failures))


if __name__ == "__main__":
    unittest.main()
