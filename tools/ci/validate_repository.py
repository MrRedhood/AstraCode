#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
IGNORED_PARTS = {".git", ".gradle", ".idea", ".venv", "__pycache__", "build", "node_modules"}
MODULE_PATTERN = re.compile(r"""["'](:[A-Za-z][A-Za-z0-9_-]*)["']""")
PROJECT_DEPENDENCY_PATTERN = re.compile(r"""project\(\s*["'](:[A-Za-z][A-Za-z0-9_-]*)["']\s*\)""")
ACTION_PATTERN = re.compile(r"^\s*uses:\s*([^\s#]+)", re.MULTILINE)
PINNED_ACTION_PATTERN = re.compile(r".+@[0-9a-fA-F]{40}\Z")


def repository_files(root: Path):
    for path in root.rglob("*"):
        relative = path.relative_to(root)
        if any(part in IGNORED_PARTS for part in relative.parts):
            continue
        if path.is_file():
            yield path


def parse_declared_modules(settings_text: str) -> list[str]:
    modules: list[str] = []
    for block in re.findall(r"\binclude\s*\((.*?)\)", settings_text, flags=re.DOTALL):
        modules.extend(MODULE_PATTERN.findall(block))
    return modules


def validate_action_reference(reference: str) -> str | None:
    if reference.startswith(("./", "docker://")):
        return None
    if not PINNED_ACTION_PATTERN.fullmatch(reference):
        return f"external action is not pinned to a full commit SHA: {reference}"
    return None


def validate_repository(root: Path) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    settings_path = root / "settings.gradle.kts"
    root_build = root / "build.gradle.kts"
    wrapper_jar = root / "gradle/wrapper/gradle-wrapper.jar"
    wrapper_properties = root / "gradle/wrapper/gradle-wrapper.properties"

    if not settings_path.is_file():
        errors.append("settings.gradle.kts is missing")
        modules: list[str] = []
    else:
        modules = parse_declared_modules(settings_path.read_text(encoding="utf-8"))
        if not modules:
            errors.append("settings.gradle.kts does not declare any Gradle modules")
        if len(modules) != len(set(modules)):
            errors.append("settings.gradle.kts declares a Gradle module more than once")

    if not root_build.is_file():
        errors.append("root build.gradle.kts is missing")

    for module in modules:
        module_name = module[1:]
        module_dir = root / module_name
        if not module_dir.is_dir():
            errors.append(f"declared module directory is missing: {module_name}")
        elif not (module_dir / "build.gradle.kts").is_file() and not (module_dir / "build.gradle").is_file():
            errors.append(f"declared module has no Gradle build file: {module_name}")

    known_modules = set(modules)
    for build_file in repository_files(root):
        if build_file.name not in {"build.gradle", "build.gradle.kts"}:
            continue
        try:
            build_source = build_file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(f"Gradle build file is not UTF-8: {build_file.relative_to(root)}")
            continue
        for dependency in PROJECT_DEPENDENCY_PATTERN.findall(build_source):
            if dependency not in known_modules:
                errors.append(
                    f"{build_file.relative_to(root)} references an undeclared Gradle module: {dependency}"
                )

    markdown_files = [
        path.relative_to(root).as_posix()
        for path in repository_files(root)
        if path.suffix.lower() == ".md"
    ]
    if markdown_files:
        errors.append("Markdown files are not allowed in this code-only repository: " + ", ".join(sorted(markdown_files)))

    if not wrapper_jar.is_file():
        errors.append("Gradle wrapper JAR is missing")
    if not wrapper_properties.is_file():
        errors.append("Gradle wrapper properties are missing")

    workflow_dir = root / ".github/workflows"
    if workflow_dir.is_dir():
        workflow_files = sorted(workflow_dir.glob("*.yml")) + sorted(workflow_dir.glob("*.yaml"))
        for workflow in workflow_files:
            try:
                content = workflow.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                errors.append(f"workflow is not UTF-8: {workflow.relative_to(root)}")
                continue
            for reference in ACTION_PATTERN.findall(content):
                error = validate_action_reference(reference)
                if error:
                    errors.append(f"{workflow.relative_to(root)}: {error}")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate AstraCode's code-only repository structure.")
    parser.add_argument("--root", type=Path, default=REPO_ROOT)
    args = parser.parse_args(argv)
    errors = validate_repository(args.root)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Repository validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
