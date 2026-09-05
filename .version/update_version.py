#!/usr/bin/env python3

import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
POM_FILE = ROOT / "pom.xml"
README_FILE = ROOT / "README.md"
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def get_project_info() -> tuple[str, str, str]:
    root = ET.parse(POM_FILE).getroot()

    artifact_id = root.findtext("m:artifactId", namespaces=MAVEN_NS)
    project_name = root.findtext("m:name", namespaces=MAVEN_NS)
    version = root.findtext("m:version", namespaces=MAVEN_NS)

    if not artifact_id or not project_name or not version:
        raise RuntimeError("Missing artifactId, name or version in pom.xml")

    return artifact_id.strip(), project_name.strip(), version.strip()


def get_version(current_version: str) -> str:
    major, minor, _ = map(int, current_version.split("."))
    build = int(git("rev-list", "--count", "HEAD"))

    return f"{major}.{minor}.{build}"


def update_pom(current_version: str, version: str) -> None:
    content = POM_FILE.read_text(encoding="utf-8")

    content = re.sub(
        rf"(<version>){re.escape(current_version)}(</version>)",
        rf"\g<1>{version}\g<2>",
        content,
        count=1
    )

    POM_FILE.write_text(content, encoding="utf-8")


def update_readme(project_name: str, artifact_id: str, version: str) -> None:
    content = README_FILE.read_text(encoding="utf-8")

    content = re.sub(
        rf"^# {re.escape(project_name)}(?: v\d+\.\d+\.\d+)?$",
        f"# {project_name} v{version}",
        content,
        count=1,
        flags=re.MULTILINE
    )

    content = re.sub(
        rf"(target[\\/]{re.escape(artifact_id)}-)\d+\.\d+\.\d+(\.jar)",
        rf"\g<1>{version}\g<2>",
        content
    )

    README_FILE.write_text(content, encoding="utf-8")


def main() -> None:
    artifact_id, project_name, current_version = get_project_info()
    version = get_version(current_version)

    update_pom(current_version, version)
    update_readme(project_name, artifact_id, version)
    git("add", POM_FILE.relative_to(ROOT).as_posix(), README_FILE.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()