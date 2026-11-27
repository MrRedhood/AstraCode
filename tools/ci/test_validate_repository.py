from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.ci.validate_repository import parse_declared_modules, validate_action_reference, validate_repository


class RepositoryValidatorTests(unittest.TestCase):
    def create_repository(self, root: Path) -> None:
        (root / "app").mkdir(parents=True)
        (root / "core-filesystem").mkdir()
        (root / "gradle/wrapper").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        (root / "settings.gradle.kts").write_text(
            'rootProject.name = "Fixture"\ninclude(":app", ":core-filesystem")\n',
            encoding="utf-8",
        )
        (root / "build.gradle.kts").write_text("plugins {}\n", encoding="utf-8")
        (root / "app/build.gradle.kts").write_text(
            'dependencies { implementation(project(":core-filesystem")) }\n',
            encoding="utf-8",
        )
        (root / "core-filesystem/build.gradle.kts").write_text("plugins {}\n", encoding="utf-8")
        (root / "gradle/wrapper/gradle-wrapper.jar").write_bytes(b"wrapper")
        (root / "gradle/wrapper/gradle-wrapper.properties").write_text("distributionUrl=test\n", encoding="utf-8")
        (root / ".github/workflows/ci.yml").write_text(
            "steps:\n  - uses: actions/checkout@" + ("a" * 40) + "\n",
            encoding="utf-8",
        )

    def test_clean_repository_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            self.assertEqual([], validate_repository(root))

    def test_module_parser_reads_gradle_include_blocks(self) -> None:
        modules = parse_declared_modules('include(\n  ":app",\n  ":core-filesystem"\n)\n')
        self.assertEqual([":app", ":core-filesystem"], modules)

    def test_duplicate_module_declarations_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / "settings.gradle.kts").write_text(
                'include(":app", ":app", ":core-filesystem")\n',
                encoding="utf-8",
            )
            self.assertTrue(any("more than once" in error for error in validate_repository(root)))

    def test_missing_module_build_file_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / "core-filesystem/build.gradle.kts").unlink()
            self.assertTrue(any("no Gradle build file" in error for error in validate_repository(root)))

    def test_markdown_is_rejected_but_build_outputs_are_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / "build").mkdir()
            (root / "build/generated.md").write_text("generated", encoding="utf-8")
            self.assertEqual([], validate_repository(root))
            (root / "Architecture.md").write_text("not permitted", encoding="utf-8")
            self.assertTrue(any("Markdown files are not allowed" in error for error in validate_repository(root)))

    def test_external_actions_must_be_pinned_to_full_commit_sha(self) -> None:
        self.assertIsNone(validate_action_reference("actions/checkout@" + ("a" * 40)))
        self.assertIsNone(validate_action_reference("./.github/actions/local"))
        self.assertIsNone(validate_action_reference("docker://alpine:3.20"))
        self.assertIsNotNone(validate_action_reference("actions/checkout@v4"))

    def test_workflow_with_unpinned_action_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / ".github/workflows/ci.yml").write_text(
                "steps:\n  - uses: actions/checkout@v4\n", encoding="utf-8"
            )
            self.assertTrue(any("not pinned" in error for error in validate_repository(root)))

    def test_unknown_project_dependency_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / "app/build.gradle.kts").write_text(
                'dependencies { implementation(project(":missing")) }\n',
                encoding="utf-8",
            )
            self.assertTrue(any("undeclared Gradle module" in error for error in validate_repository(root)))

    def test_missing_gradle_wrapper_artifacts_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.create_repository(root)
            (root / "gradle/wrapper/gradle-wrapper.jar").unlink()
            errors = validate_repository(root)
            self.assertTrue(any("wrapper JAR is missing" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
