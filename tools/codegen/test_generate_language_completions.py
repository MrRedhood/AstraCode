from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.codegen.generate_language_completions import CodegenError, load_catalog, normalize_catalog, render_kotlin


class LanguageCompletionCodegenTests(unittest.TestCase):
    def test_generated_kotlin_is_deterministic(self) -> None:
        catalog = {"Kotlin": ["suspend", "inline"], "Rust": ["fn", "Result"]}
        first = render_kotlin(normalize_catalog({"schemaVersion": 1, "languages": catalog}))
        second = render_kotlin(normalize_catalog({"schemaVersion": 1, "languages": catalog}))
        self.assertEqual(first, second)
        self.assertIn('"Kotlin" to setOf(', first)
        self.assertIn('"Result"', first)

    def test_duplicate_tokens_are_removed_without_reordering(self) -> None:
        normalized = normalize_catalog({"schemaVersion": 1, "languages": {"Kotlin": ["fun", "val", "fun"]}})
        self.assertEqual(["fun", "val"], normalized["Kotlin"])

    def test_invalid_token_is_rejected(self) -> None:
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 1, "languages": {"Kotlin": ["fun", "bad token"]}})

    def test_control_characters_are_rejected(self) -> None:
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 1, "languages": {"Kotlin": ["fun\nclass"]}})

    def test_empty_or_wrong_version_catalog_is_rejected(self) -> None:
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 2, "languages": {"Kotlin": ["fun"]}})
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 1, "languages": {}})

    def test_language_names_and_tokens_are_bounded(self) -> None:
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 1, "languages": {"Kotlin": ["x" * 129]}})
        with self.assertRaises(CodegenError):
            normalize_catalog({"schemaVersion": 1, "languages": {" " * 65: ["fun"]}})

    def test_duplicate_json_keys_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "languages.json"
            source.write_text('{"schemaVersion":1,"languages":{"Kotlin":["fun"],"Kotlin":["val"]}}', encoding="utf-8")
            with self.assertRaises(CodegenError):
                load_catalog(source)

    def test_kotlin_interpolation_is_escaped(self) -> None:
        generated = render_kotlin({"Kotlin": ["value$1"]})
        self.assertIn(r'"value\$1"', generated)


if __name__ == "__main__":
    unittest.main()
