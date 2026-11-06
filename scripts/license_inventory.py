#!/usr/bin/env python3
"""
Regenerate the third-party license bundle for elDoc IDP OSS.

The script resolves the runtime dependency graph with Maven, downloads the
resolved artifacts, extracts their license/notice files into ``licenses/``,
records SHA-256 checksums and rewrites:

  * docs/RUNTIME_INVENTORY.csv
  * THIRD_PARTY_NOTICES.md

Curated metadata (upstream sources, additional license paths, special license
summaries and manually verified statuses) lives in scripts/license-inventory.json.
Paths may contain the placeholders ``{version}``, ``{baseVersion}`` (version
without a -beta/-rc/-SNAPSHOT suffix) and ``{classifier}``.

Typical use::

    python3 scripts/license_inventory.py --update   # after changing versions
    python3 scripts/license_inventory.py --check    # in CI / pre-commit

Only the Python standard library is required.
"""

from __future__ import annotations

import argparse
import csv
import difflib
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from datetime import date
from pathlib import Path, PurePosixPath

DEFAULT_CONFIG = "scripts/license-inventory.json"
DEFAULT_INVENTORY = "docs/RUNTIME_INVENTORY.csv"
DEFAULT_NOTICES = "THIRD_PARTY_NOTICES.md"
DEFAULT_TEMPLATE = "scripts/templates/THIRD_PARTY_NOTICES.md.tmpl"
DEFAULT_MODULE = "modules/eldoc-idp-web"
DEFAULT_DEPENDENCY_PLUGIN = "3.10.0"
DEFAULT_REPOSITORY = "https://repo.maven.apache.org/maven2"
DEFAULT_STATUS = "license material collected; final-distribution comparison pending"

CSV_FIELDS = [
    "coordinate",
    "scope",
    "type",
    "license_summary",
    "status",
    "artifact_url",
    "artifact_sha256",
    "license_paths",
    "upstream_source",
]

# Basenames (case-insensitive) that normally contain license material.
LEGAL_BASENAME_RE = re.compile(
    r"^(?:"
    r"LICEN[CS]ES?|COPYING|COPYRIGHT|NOTICE|UNLICENSE|"
    r"(?:3RD|THIRD)[-_ ]?PARTY.*|DEPENDENC(?:Y|IES).*|DEPENDENCY[-_ ]?LICENSES?"
    r")(?:[._ -].*)?$",
    re.IGNORECASE,
)
LEGAL_DIRS = {"license", "licenses", "licencing", "licensing", "legal", "notices"}

# Base URLs used when bootstrapping a config from an existing CSV. Only used by
# --bootstrap-config; the generated JSON remains the editable source of truth.
INFERRED_UPSTREAM_URLS = {
    "licenses/upstream/base62": "https://raw.githubusercontent.com/seruco/base62/{version}",
    "licenses/upstream/djl": "https://raw.githubusercontent.com/deepjavalibrary/djl/v{version}",
    "licenses/upstream/errorprone": "https://raw.githubusercontent.com/google/error-prone/v{version}",
    "licenses/upstream/eventstream": "https://raw.githubusercontent.com/awslabs/aws-eventstream-java/{version}",
    "licenses/upstream/graal": "https://raw.githubusercontent.com/oracle/graal/vm-{version}",
    "licenses/upstream/graaljs": "https://raw.githubusercontent.com/oracle/graaljs/vm-{version}",
    "licenses/upstream/gson": "https://raw.githubusercontent.com/google/gson/gson-parent-{version}",
    "licenses/upstream/icu": "https://raw.githubusercontent.com/unicode-org/icu/release-{version}",
    "licenses/upstream/jcommander": "https://raw.githubusercontent.com/cbeust/jcommander/{version}",
    "licenses/upstream/jspecify": "https://raw.githubusercontent.com/jspecify/jspecify/v{version}",
    "licenses/upstream/jtokkit": "https://raw.githubusercontent.com/knuddelsgmbh/jtokkit/{version}",
    "licenses/upstream/langchain4j": "https://raw.githubusercontent.com/langchain4j/langchain4j/{baseVersion}",
    "licenses/upstream/mongodb": "https://raw.githubusercontent.com/mongodb/mongo-java-driver/r{version}",
    "licenses/upstream/mutiny-zero": "https://raw.githubusercontent.com/smallrye/smallrye-mutiny-zero/{version}",
    "licenses/upstream/netty": "https://raw.githubusercontent.com/netty/netty/netty-{version}",
    "licenses/upstream/noggit": "https://raw.githubusercontent.com/yonik/noggit/noggit-{version}",
    "licenses/upstream/omnifaces": "https://raw.githubusercontent.com/omnifaces/omnifaces/{version}",
    "licenses/upstream/onnxruntime": "https://raw.githubusercontent.com/microsoft/onnxruntime/v{version}",
    "licenses/upstream/reactive-streams": "https://raw.githubusercontent.com/reactive-streams/reactive-streams/v{version}",
    "licenses/upstream/sanitizer": "https://raw.githubusercontent.com/OWASP/java-html-sanitizer/release-{version}",
    "licenses/upstream/twelvemonkeys": "https://raw.githubusercontent.com/haraldk/TwelveMonkeys/twelvemonkeys-{version}",
    "licenses/upstream/zxing": "https://raw.githubusercontent.com/zxing/zxing/zxing-{version}",
}


# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------


def log(message: str) -> None:
    print(message, file=sys.stderr)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def xml_children(element: ET.Element, name: str):
    return [child for child in element if local_name(child.tag) == name]


def xml_text(element: ET.Element | None, name: str) -> str | None:
    if element is None:
        return None
    for child in element:
        if local_name(child.tag) == name:
            text = (child.text or "").strip()
            return text or None
    return None


def render_tokens(value: str, version: str, base_version: str, classifier: str) -> str:
    return (
        value.replace("{version}", version)
        .replace("{baseVersion}", base_version)
        .replace("{classifier}", classifier or "")
    )


def version_base(version: str) -> str:
    """Strip prerelease / snapshot qualifiers: 1.22.0-beta32 -> 1.22.0."""
    base = re.sub(
        r"(?i)[-.](?:alpha|beta|rc|cr|m|milestone|snapshot)[\d.]*.*$", "", version
    )
    return base or version


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_legal_path(name: str) -> bool:
    """Return True for files that look like license/notice material."""
    parts = PurePosixPath(name).parts
    if not parts:
        return False
    if len(parts) >= 2 and parts[0].upper() == "META-INF" and parts[1].lower() == "maven":
        return False
    if len(parts) >= 2 and parts[0].upper() == "META-INF" and parts[1].lower() == "versions":
        return False
    suffix = PurePosixPath(name).suffix.lower()
    if suffix in {".class", ".jar", ".zip", ".so", ".dll", ".dylib", ".exe"}:
        return False
    if LEGAL_BASENAME_RE.match(parts[-1]):
        return True
    return any(part.lower() in LEGAL_DIRS for part in parts[:-1])


def normalize_license_name(name: str) -> str:
    lowered = re.sub(r"\s+", " ", name.lower()).strip()
    if "apache" in lowered and ("2" in lowered or "version 2" in lowered):
        return "apache-2.0"
    if "mit" in lowered:
        return "mit"
    if "bsd" in lowered:
        if "3" in lowered:
            return "bsd-3-clause"
        if "2" in lowered:
            return "bsd-2-clause"
        return "bsd"
    if "eclipse public license" in lowered:
        return "epl-2.0" if "2" in lowered else "epl-1.0"
    if "cddl" in lowered:
        return "cddl"
    if "lgpl" in lowered or "lesser general public" in lowered:
        return "lgpl"
    if "gpl" in lowered or "general public license" in lowered:
        return "gpl"
    if "unicode" in lowered:
        return "unicode"
    return lowered


def licenses_match(configured: str, resolved: str) -> bool:
    """Loose comparison so POM wording changes do not create noise."""
    left = normalize_license_name(configured)
    right = normalize_license_name(resolved)
    known = {
        "apache-2.0",
        "mit",
        "bsd",
        "bsd-2-clause",
        "bsd-3-clause",
        "epl-1.0",
        "epl-2.0",
        "cddl",
        "lgpl",
        "gpl",
        "unicode",
    }
    if left == right:
        return True
    # Only compare summaries we can classify; long composite or custom wording
    # is curated on purpose and should not be reported as a change.
    if left not in known or right not in known:
        return True
    return left in right or right in left


def read_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


# ---------------------------------------------------------------------------
# Maven / artifact access
# ---------------------------------------------------------------------------


class Context:
    def __init__(self, root: Path, config: dict, args: argparse.Namespace) -> None:
        self.root = root
        self.config = config
        self.args = args
        self.cache_dir = root / ".cache" / "license-inventory"
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        maven_cfg = config.get("maven", {})
        self.module = maven_cfg.get("module", DEFAULT_MODULE)
        self.repository = maven_cfg.get("repository", DEFAULT_REPOSITORY).rstrip("/")
        self.snapshot_repository = maven_cfg.get("snapshotRepository", {})
        self._snapshots: dict[tuple[str, str, str, str, str], tuple[str, bool]] = {}
        self.dependency_plugin = maven_cfg.get(
            "dependencyPluginVersion", DEFAULT_DEPENDENCY_PLUGIN
        )
        self.maven_command = self._resolve_maven(maven_cfg.get("command"))
        self.local_repository = self._local_repository(maven_cfg.get("localRepository"))
        self._root_properties: dict[str, str] | None = None
        self._property_cache: dict[str, str] = {}

    def _resolve_maven(self, configured: str | None) -> list[str]:
        if configured:
            if os.name == "nt" and configured.replace("\\", "/") in {"./mvnw", "mvnw"}:
                wrapper = self.root / "mvnw.cmd"
                if wrapper.exists():
                    return [str(wrapper)]
            return [configured]
        wrapper = self.root / ("mvnw.cmd" if os.name == "nt" else "mvnw")
        if wrapper.exists():
            return [str(wrapper)]
        return ["mvn"]

    def _local_repository(self, configured: str | None) -> Path:
        if configured:
            return Path(configured).expanduser().resolve()
        if self.args.local_repository:
            return Path(self.args.local_repository).expanduser().resolve()
        try:
            result = subprocess.run(
                [*self.maven_command, "-q", "help:evaluate",
                 "-Dexpression=settings.localRepository", "-DforceStdout"],
                cwd=self.root,
                capture_output=True,
                text=True,
                timeout=120,
            )
            candidate = (result.stdout or "").strip().splitlines()
            if result.returncode == 0 and candidate:
                path = Path(candidate[-1].strip()).expanduser()
                if path.exists():
                    return path.resolve()
        except (OSError, subprocess.SubprocessError):
            pass
        return (Path.home() / ".m2" / "repository").resolve()

    def maven(self, *arguments: str, check: bool = True) -> subprocess.CompletedProcess:
        command = [*self.maven_command, *arguments]
        log("+ " + " ".join(command))
        result = subprocess.run(
            command, cwd=self.root, capture_output=True, text=True, timeout=900
        )
        if check and result.returncode != 0:
            log(result.stdout or "")
            log(result.stderr or "")
            raise SystemExit(f"Maven command failed: {' '.join(command)}")
        return result

    def root_properties(self) -> dict[str, str]:
        if self._root_properties is not None:
            return self._root_properties
        properties: dict[str, str] = {}
        pom = self.root / "pom.xml"
        if pom.exists():
            tree = ET.parse(pom)
            element = tree.getroot()
            for props in xml_children(element, "properties"):
                for prop in props:
                    properties[local_name(prop.tag)] = (prop.text or "").strip()
        self._root_properties = properties
        return properties

    def property_value(self, expression: str) -> str:
        if expression in self._property_cache:
            return self._property_cache[expression]
        properties = self.root_properties()

        def resolve(value: str, depth: int = 0) -> str:
            if depth > 10:
                return value
            match = re.fullmatch(r"\$\{([^}]+)\}", value.strip())
            if match and match.group(1) in properties:
                return resolve(properties[match.group(1)], depth + 1)
            return value

        value = resolve(properties.get(expression, ""))
        if not value:
            result = self.maven(
                "-q", "help:evaluate", f"-Dexpression={expression}", "-DforceStdout"
            )
            value = (result.stdout or "").strip().splitlines()[-1].strip()
        if not value:
            raise SystemExit(f"Cannot resolve Maven property {expression!r}")
        self._property_cache[expression] = value
        return value

    def snapshot_version(
        self, group: str, artifact: str, version: str, type_: str, classifier: str
    ) -> tuple[str, bool]:
        key = (group, artifact, version, type_, classifier)
        if key in self._snapshots:
            return self._snapshots[key]
        repository_id = self.snapshot_repository.get("id")
        repository_url = self.snapshot_repository.get("url", "").rstrip("/")
        label = f"{group}:{artifact}:{version}:{type_}:{classifier}"
        if not repository_id or not repository_url:
            raise SystemExit(f"Cannot resolve snapshot {label}: configure maven.snapshotRepository id/url")
        relative = Path(*group.split(".")) / artifact / version

        def resolved(path: Path) -> str:
            try:
                metadata = ET.parse(path).getroot()
            except (OSError, ET.ParseError):
                return ""
            for entry in metadata.findall("./versioning/snapshotVersions/snapshotVersion"):
                value = entry.findtext("value", "").strip()
                if (entry.findtext("extension", "").strip() == type_
                        and entry.findtext("classifier", "").strip() == classifier
                        and re.fullmatch(re.escape(version[:-9]) + r"-\d{8}\.\d{6}-\d+", value)):
                    return value
            return ""

        value = resolved(self.local_repository / relative / f"maven-metadata-{repository_id}.xml")
        local = bool(value)
        if not value:
            url = f"{repository_url}/{relative.as_posix()}/maven-metadata.xml"
            try:
                with tempfile.TemporaryDirectory(dir=self.cache_dir) as temporary:
                    path = Path(temporary) / "maven-metadata.xml"
                    self.download(url, path)
                    value = resolved(path)
            except SystemExit as error:
                raise SystemExit(f"Cannot resolve snapshot {label}: {error}") from error
        if not value:
            raise SystemExit(f"Cannot resolve snapshot {label}: no matching timestamped extension/classifier in metadata")
        self._snapshots[key] = (value, local)
        return value, local

    def artifact_url(
        self, group: str, artifact: str, version: str, type_: str, classifier: str
    ) -> str:
        group_path = group.replace(".", "/")
        repository = self.repository
        resolved_version = version
        if version.endswith("-SNAPSHOT"):
            resolved_version, _ = self.snapshot_version(group, artifact, version, type_, classifier)
            repository = self.snapshot_repository["url"].rstrip("/")
        filename = f"{artifact}-{resolved_version}"
        if classifier:
            filename += f"-{classifier}"
        filename += f".{type_}"
        return f"{repository}/{group_path}/{artifact}/{version}/{filename}"

    def pom_file(self, group: str, artifact: str, version: str) -> Path:
        if version.endswith("-SNAPSHOT"):
            return self.artifact_file(group, artifact, version, "pom", "")
        group_path = Path(*group.split("."))
        local = (
            self.local_repository
            / group_path
            / artifact
            / version
            / f"{artifact}-{version}.pom"
        )
        if local.exists():
            return local
        return self.download_pom(group, artifact, version)

    def download_pom(self, group: str, artifact: str, version: str) -> Path:
        url = self.artifact_url(group, artifact, version, "pom", "")
        dest = self.cache_dir / Path(*group.split(".")) / artifact / version / (
            url.rsplit("/", 1)[-1]
        )
        self.download(url, dest)
        return dest

    def artifact_file(
        self, group: str, artifact: str, version: str, type_: str, classifier: str
    ) -> Path:
        if version.endswith("-SNAPSHOT"):
            url = self.artifact_url(group, artifact, version, type_, classifier)
            _, local_metadata = self.snapshot_version(group, artifact, version, type_, classifier)
            relative = Path(*group.split(".")) / artifact / version
            filename = url.rsplit("/", 1)[-1]
            local = self.local_repository / relative / filename
            if local.exists():
                return local
            if local_metadata:
                suffix = f"-{classifier}" if classifier else ""
                local = self.local_repository / relative / f"{artifact}-{version}{suffix}.{type_}"
                if local.exists():
                    return local
            dest = self.cache_dir / relative / filename
            self.download(url, dest)
            return dest
        filename = f"{artifact}-{version}"
        if classifier:
            filename += f"-{classifier}"
        filename += f".{type_}"
        group_path = Path(*group.split("."))
        local = self.local_repository / group_path / artifact / version / filename
        if local.exists():
            return local
        dest = self.cache_dir / group_path / artifact / version / filename
        self.download(
            self.artifact_url(group, artifact, version, type_, classifier), dest
        )
        return dest

    def download(self, url: str, destination: Path) -> None:
        if destination.exists() and destination.stat().st_size > 0:
            return
        if self.args.no_download:
            raise SystemExit(f"Missing cached artifact and --no-download is set: {url}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        last_error: Exception | None = None
        for attempt in range(1, 4):
            try:
                request = urllib.request.Request(
                    url, headers={"User-Agent": "eldoc-license-inventory/1.0"}
                )
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                temporary = destination.with_suffix(destination.suffix + ".part")
                temporary.write_bytes(data)
                os.replace(temporary, destination)
                return
            except (urllib.error.URLError, OSError) as error:
                last_error = error
                if attempt == 3:
                    break
        raise SystemExit(f"Failed to download {url}: {last_error}")


def resolve_tree(ctx: Context) -> list[dict]:
    if ctx.args.tree_file:
        with Path(ctx.args.tree_file).open(encoding="utf-8") as handle:
            tree = json.load(handle)
    else:
        temporary = tempfile.NamedTemporaryFile(
            suffix=".json", prefix="eldoc-dependency-tree-", delete=False
        )
        temporary.close()
        output = Path(temporary.name)
        try:
            ctx.maven(
                "-q",
                "-pl",
                ctx.module,
                "-am",
                f"org.apache.maven.plugins:maven-dependency-plugin:{ctx.dependency_plugin}:tree",
                "-Dscope=runtime",
                "-DoutputType=json",
                f"-DoutputFile={output}",
            )
            with output.open(encoding="utf-8") as handle:
                tree = json.load(handle)
        finally:
            output.unlink(missing_ok=True)

    artifacts: dict[tuple[str, str, str, str], dict] = {}
    scope_priority = {"compile": 0, "runtime": 1, "provided": 2, "test": 3}

    def walk(node: dict, inherited_scope: str = "compile") -> None:
        for child in node.get("children") or []:
            group = child.get("groupId") or ""
            artifact = child.get("artifactId") or ""
            version = child.get("version") or ""
            type_ = child.get("type") or "jar"
            classifier = child.get("classifier") or ""
            scope = child.get("scope") or inherited_scope
            if group.startswith("ai.eldoc") or not group or not version:
                walk(child, scope)
                continue
            if scope in {"test", "provided"}:
                walk(child, scope)
                continue
            key = (group, artifact, type_, classifier)
            candidate = {
                "group": group,
                "artifact": artifact,
                "version": version,
                "type": type_,
                "classifier": classifier,
                "scope": scope,
            }
            existing = artifacts.get(key)
            if existing is None or scope_priority.get(scope, 9) < scope_priority.get(
                existing["scope"], 9
            ):
                artifacts[key] = candidate
            walk(child, scope)

    walk(tree)
    return sorted(
        artifacts.values(),
        key=lambda item: (
            item["group"],
            item["artifact"],
            item["classifier"],
            item["version"],
        ),
    )


def pom_metadata(ctx: Context, group: str, artifact: str, version: str) -> dict:
    root = None
    current = (group, artifact, version)
    seen: set[tuple[str, str, str]] = set()
    url = None
    scm = None
    licenses: list[str] = []

    while current not in seen:
        seen.add(current)
        try:
            path = ctx.pom_file(*current)
            element = ET.parse(path).getroot()
        except SystemExit:
            if current[2].endswith("-SNAPSHOT"):
                raise
            break
        except (OSError, ET.ParseError):
            break
        if root is None:
            root = element
            url = xml_text(element, "url")
            for scm_element in xml_children(element, "scm"):
                scm = xml_text(scm_element, "url") or xml_text(
                    scm_element, "connection"
                )
        if not licenses:
            for licenses_element in xml_children(element, "licenses"):
                for license_element in xml_children(licenses_element, "license"):
                    name = xml_text(license_element, "name")
                    if name:
                        licenses.append(name)
        parent = None
        for candidate in xml_children(element, "parent"):
            parent = candidate
            break
        if parent is None:
            break
        parent_group = xml_text(parent, "groupId")
        parent_artifact = xml_text(parent, "artifactId")
        parent_version = xml_text(parent, "version")
        if not (parent_group and parent_artifact and parent_version):
            break
        current = (parent_group, parent_artifact, parent_version)

    return {"licenses": licenses, "url": url, "scm": scm}


# ---------------------------------------------------------------------------
# License collection
# ---------------------------------------------------------------------------


def collect_artifact_licenses(
    ctx: Context,
    artifact_path: Path,
    destination: Path,
    dry_run: bool,
    prefixes: list[str] | None = None,
) -> list[str]:
    """Extract license files from a JAR, preserving their path inside the JAR."""
    if artifact_path.suffix.lower() != ".jar":
        if not dry_run and destination.exists():
            shutil.rmtree(destination)
        return []

    selected: list[tuple[str, bytes]] = []
    with zipfile.ZipFile(artifact_path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            name = info.filename
            pure = PurePosixPath(name)
            if pure.is_absolute() or ".." in pure.parts:
                continue
            if prefixes:
                if not any(name.startswith(prefix) for prefix in prefixes):
                    continue
            elif not is_legal_path(name):
                continue
            data = archive.read(info)
            if not data:
                continue
            # Keep the bundle byte-stable across platforms: legal texts are
            # checked in with LF line endings even when the JAR uses CRLF.
            # This also covers legacy non-UTF-8 license texts.
            if b"\x00" not in data:
                data = data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
            selected.append((name, data))

    selected.sort(key=lambda item: item[0])
    if not dry_run:
        if destination.exists():
            shutil.rmtree(destination)
        if selected:
            destination.mkdir(parents=True, exist_ok=True)
            for name, data in selected:
                target = destination / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
    return [name for name, _ in selected]


def fetch_upstream_bundle(
    ctx: Context,
    bundle_key: str,
    bundle: dict,
    version: str,
    base_version: str,
    classifier: str,
) -> tuple[list[str], list[str]]:
    """Return (existing file paths, missing file paths) for an upstream bundle."""
    directory = render_tokens(bundle_key, version, base_version, classifier)
    base_url = bundle.get("baseUrl")
    if base_url:
        base_url = render_tokens(base_url, version, base_version, classifier)
    existing: list[str] = []
    missing: list[str] = []
    for filename in bundle.get("files", []):
        path = Path(directory) / filename
        if path.exists():
            existing.append(path.as_posix())
            continue
        if base_url and not ctx.args.no_download and not ctx.args.check:
            url = base_url.rstrip("/") + "/" + filename
            try:
                ctx.download(url, path)
                existing.append(path.as_posix())
                continue
            except SystemExit as error:
                log(f"warning: {error}")
        missing.append(path.as_posix())
    return existing, missing


def group_license_paths(paths: list[str]) -> list[tuple[str, int]]:
    """Group file paths by parent directory for compact Markdown links."""
    groups: dict[str, int] = {}
    order: list[str] = []
    for path in paths:
        cleaned = path.rstrip("/")
        parent = str(Path(cleaned).parent).replace(os.sep, "/")
        if cleaned.endswith("/"):
            parent = cleaned
        if parent not in groups:
            groups[parent] = 0
            order.append(parent)
        groups[parent] += 1
    return [(directory, groups[directory]) for directory in order]


def render_material_links(paths: list[str]) -> str:
    if not paths:
        return "—"
    parts = []
    for directory, count in group_license_paths(paths):
        parts.append(f"[files {count}]({directory}/)")
    return " · ".join(parts)


# ---------------------------------------------------------------------------
# Generation
# ---------------------------------------------------------------------------


def generate(ctx: Context) -> tuple[list[dict], list[str], list[str]]:
    artifacts = resolve_tree(ctx)
    config = ctx.config
    components_cfg = config.get("components", {})
    bundles = config.get("upstreamBundles", {})
    warnings: list[str] = []
    errors: list[str] = []
    rows: list[dict] = []

    # Extra artifacts are not part of the Maven dependency graph but are part of
    # the distributed runtime (currently Payara Micro).
    for extra in config.get("extraArtifacts", []):
        if extra.get("enabled", True) is False:
            continue
        version = extra.get("version")
        if not version and extra.get("versionExpression"):
            version = ctx.property_value(extra["versionExpression"])
        if not version:
            raise SystemExit(f"Extra artifact {extra} has no version/versionExpression")
        artifacts.append(
            {
                "group": extra["groupId"],
                "artifact": extra["artifactId"],
                "version": version,
                "type": extra.get("type", "jar"),
                "classifier": extra.get("classifier", ""),
                "scope": extra.get("scope", "runtime"),
                "extra": extra,
            }
        )

    artifacts.sort(
        key=lambda item: (
            item["group"],
            item["artifact"],
            item["classifier"],
            item["version"],
        )
    )

    expected_prune_roots: set[Path] = set()
    used_bundle_dirs: list[tuple[str, str]] = []

    for item in artifacts:
        group = item["group"]
        artifact = item["artifact"]
        version = item["version"]
        type_ = item["type"]
        classifier = item["classifier"]
        base = version_base(version)
        key = f"{group}:{artifact}"
        component = dict(components_cfg.get(key, {}))
        # A coordinate-specific override is useful for classified artifacts.
        coordinate_override = components_cfg.get(
            f"{key}:{classifier}" if classifier else "", {}
        )
        component.update(coordinate_override)

        extra = item.get("extra") or {}
        licenses_dir_cfg = config.get("outputs", {}).get("licenses", "licenses")
        licenses_root = ctx.root / licenses_dir_cfg

        if extra.get("licenseDir"):
            destination = ctx.root / render_tokens(
                extra["licenseDir"], version, base, classifier
            )
        else:
            version_dir = version + (f"-{classifier}" if classifier else "")
            destination = (
                licenses_root / "maven" / f"{group}.{artifact}" / version_dir / "artifact"
            )

        metadata = pom_metadata(ctx, group, artifact, version)
        artifact_path = ctx.artifact_file(group, artifact, version, type_, classifier)
        digest = sha256_file(artifact_path)

        maven_paths = collect_artifact_licenses(
            ctx,
            artifact_path,
            destination,
            dry_run=ctx.args.check,
            prefixes=extra.get("extractPrefixes"),
        )
        maven_license_paths = [
            (destination / name).relative_to(ctx.root).as_posix()
            for name in maven_paths
        ]

        expected_prune_roots.add(destination.parent)

        upstream_paths: list[str] = []
        missing_paths: list[str] = []
        for bundle_key in component.get("upstream", []):
            if bundle_key not in bundles:
                warnings.append(f"{key}: unknown upstream bundle {bundle_key!r}")
                continue
            bundle = bundles[bundle_key]
            used_bundle_dirs.append(
                (
                    bundle_key,
                    render_tokens(bundle_key, version, base, classifier),
                )
            )
            found, missing = fetch_upstream_bundle(
                ctx, bundle_key, bundle, version, base, classifier
            )
            upstream_paths.extend(found)
            missing_paths.extend(missing)

        configured_paths: list[str] = []
        for configured in component.get("licensePaths", []):
            rendered = render_tokens(configured, version, base, classifier)
            if (ctx.root / rendered).exists():
                configured_paths.append(rendered)
            else:
                missing_paths.append(rendered)

        # De-duplicate while preserving order: upstream evidence first, then
        # artifact files and other curated material, then the generic texts.
        common_paths = [
            path for path in configured_paths if path.startswith("licenses/common/")
        ]
        other_configured = [
            path for path in configured_paths if not path.startswith("licenses/common/")
        ]
        license_paths: list[str] = []
        for path in [
            *upstream_paths,
            *other_configured,
            *maven_license_paths,
            *common_paths,
        ]:
            if path not in license_paths:
                license_paths.append(path)

        resolved_summary = "; ".join(metadata["licenses"])
        configured_summary = component.get("licenseSummary")
        if configured_summary and resolved_summary:
            if not licenses_match(configured_summary, resolved_summary):
                warnings.append(
                    f"{key}: POM now declares {resolved_summary!r} but config has "
                    f"{configured_summary!r}; review license terms"
                )
        license_summary = configured_summary or resolved_summary

        if not license_paths and license_summary:
            if "apache" in normalize_license_name(license_summary):
                common = "licenses/common/Apache-2.0.txt"
                if (ctx.root / common).exists():
                    license_paths.append(common)
        if not license_summary:
            license_summary = "UNKNOWN — license review required"

        if component.get("status"):
            status = component["status"]
        elif maven_license_paths or upstream_paths:
            status = DEFAULT_STATUS
        elif license_paths:
            status = (
                "license declared in Maven POM; canonical text included; "
                "review required"
            )
        else:
            status = "no license material found; review required"

        if missing_paths:
            status = (
                f"license material incomplete: {len(missing_paths)} referenced "
                "upstream file(s) missing; review required"
            )

        upstream_source = (
            component.get("upstreamSource") or metadata["scm"] or metadata["url"] or ""
        )

        coordinate = f"{group}:{artifact}:{type_}"
        if classifier:
            coordinate += f":{classifier}"
        coordinate += f":{version}"

        rows.append(
            {
                "coordinate": coordinate,
                "scope": item["scope"],
                "type": type_,
                "license_summary": license_summary,
                "status": status,
                "artifact_url": ctx.artifact_url(
                    group, artifact, version, type_, classifier
                ),
                "artifact_sha256": digest,
                "license_paths": "; ".join(license_paths),
                "upstream_source": upstream_source,
            }
        )

        if missing_paths:
            errors.append(
                f"{coordinate}: missing license material: "
                + ", ".join(sorted(set(missing_paths)))
            )

    if not ctx.args.check and not ctx.args.no_prune:
        prune_generated_dirs(ctx, expected_prune_roots, warnings)
        prune_upstream_bundle_dirs(ctx, used_bundle_dirs, warnings)

    rows.sort(key=lambda row: row["coordinate"])
    return rows, warnings, errors


def prune_upstream_bundle_dirs(
    ctx: Context, used_bundle_dirs: list[tuple[str, str]], warnings: list[str]
) -> None:
    """Remove old versioned upstream evidence directories defined by a bundle."""
    current_directories = {directory for _, directory in used_bundle_dirs}
    candidates: dict[Path, re.Pattern[str]] = {}
    for bundle_key, directory in used_bundle_dirs:
        if "{version}" not in bundle_key and "{baseVersion}" not in bundle_key:
            continue
        name_pattern = PurePosixPath(bundle_key).name
        token_match = re.search(r"\{(?:version|baseVersion)\}", name_pattern)
        if not token_match:
            continue
        prefix = name_pattern[: token_match.start()]
        suffix = name_pattern[token_match.end():]
        pattern = re.compile(
            "^" + re.escape(prefix) + r"[0-9][0-9A-Za-z._-]*" + re.escape(suffix) + "$"
        )
        candidates[PurePosixPath(directory).parent] = pattern

    for parent, pattern in candidates.items():
        parent_path = ctx.root / parent
        if not parent_path.exists():
            continue
        for sibling in sorted(parent_path.iterdir()):
            if not sibling.is_dir():
                continue
            relative = sibling.relative_to(ctx.root).as_posix()
            if relative in current_directories:
                continue
            if pattern.match(sibling.name):
                warnings.append(f"Removing stale upstream licenses: {relative}")
                shutil.rmtree(sibling)


def prune_generated_dirs(
    ctx: Context, expected_roots: set[Path], warnings: list[str]
) -> None:
    """Remove generated version directories that no longer match the runtime tree."""
    licenses_root = ctx.root / ctx.config.get("outputs", {}).get("licenses", "licenses")
    for base_name in ("maven", "payara"):
        base = licenses_root / base_name
        if not base.exists():
            continue
        for component_dir in sorted(base.iterdir()):
            if not component_dir.is_dir():
                continue
            for version_dir in sorted(component_dir.iterdir()):
                if not version_dir.is_dir():
                    continue
                if version_dir in expected_roots:
                    continue
                warnings.append(f"Removing stale generated licenses: {version_dir}")
                shutil.rmtree(version_dir)
            if not any(component_dir.iterdir()):
                component_dir.rmdir()


def render_csv(rows: list[dict]) -> str:
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(CSV_FIELDS)
    for row in rows:
        writer.writerow([row[field] for field in CSV_FIELDS])
    return "\ufeff" + buffer.getvalue()


def render_notices(ctx: Context, rows: list[dict]) -> str:
    outputs = ctx.config.get("outputs", {})
    template_path = ctx.root / outputs.get("noticesTemplate", DEFAULT_TEMPLATE)
    if not template_path.exists():
        raise SystemExit(f"Notices template not found: {template_path}")
    template = template_path.read_text(encoding="utf-8")

    generated_on = ctx.config.get("generatedOn") or date.today().isoformat()
    parsed = date.fromisoformat(generated_on)
    formatted_date = f"{parsed.day} {parsed.strftime('%B %Y')}"
    revision = ctx.property_value("revision")

    payara_version = ""
    for extra in ctx.config.get("extraArtifacts", []):
        if extra.get("artifactId") == "payara-micro":
            payara_version = extra.get("version") or ctx.property_value(
                extra.get("versionExpression", "")
            )
    if not payara_version:
        payara_version = ctx.property_value("payara.version")

    lines = [
        "| Component (group:artifact:type[:classifier]:version) | "
        "Principal license terms | License material |",
        "|---|---|---|",
    ]
    for row in rows:
        summary = row["license_summary"].replace("|", "\\|")
        paths = [path for path in row["license_paths"].split("; ") if path]
        lines.append(
            f"| {row['coordinate']} | {summary} | {render_material_links(paths)} |"
        )

    return (
        template.replace("{{DATE}}", formatted_date)
        .replace("{{REVISION}}", revision)
        .replace("{{PAYARA_VERSION}}", payara_version)
        .replace("{{DEPENDENCY_TABLE}}", "\n".join(lines))
    )


# ---------------------------------------------------------------------------
# Config bootstrap
# ---------------------------------------------------------------------------


def bootstrap_config(root: Path, inventory_path: Path) -> dict:
    with inventory_path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))

    components: dict[str, dict] = {}
    bundles: dict[str, dict] = {}

    def add_bundle(directory: str, files: list[str]) -> None:
        if directory not in bundles:
            bundle: dict = {"files": sorted(files)}
            family = "/".join(directory.split("/")[:3])
            base_url = INFERRED_UPSTREAM_URLS.get(family)
            if base_url:
                bundle = {"baseUrl": base_url, **bundle}
            bundles[directory] = bundle
        else:
            existing = set(bundles[directory]["files"])
            existing.update(files)
            bundles[directory]["files"] = sorted(existing)

    for row in rows:
        coordinate = row["coordinate"]
        parts = coordinate.split(":")
        if len(parts) < 4:
            continue
        group, artifact = parts[0], parts[1]
        version = parts[-1]
        if group == "fish.payara.extras" and artifact == "payara-micro":
            continue
        base = version_base(version)
        key = f"{group}:{artifact}"
        component = components.setdefault(key, {})

        summary = row.get("license_summary", "").strip()
        if summary:
            component["licenseSummary"] = summary
        status = row.get("status", "").strip()
        if status and status != DEFAULT_STATUS:
            component["status"] = status
        upstream_source = row.get("upstream_source", "").strip()
        if upstream_source:
            component["upstreamSource"] = upstream_source

        for path in filter(None, (p.strip() for p in row["license_paths"].split(";"))):
            if path.startswith("licenses/maven/") or path.startswith("licenses/payara/"):
                continue
            if path.startswith("licenses/upstream/"):
                path_parts = path.split("/")
                directory = "/".join(path_parts[:4])
                filename = "/".join(path_parts[4:])
                directory = directory.replace(version, "{version}")
                if base != version:
                    directory = directory.replace(base, "{baseVersion}")
                add_bundle(directory, [filename])
                component.setdefault("upstream", [])
                if directory not in component["upstream"]:
                    component["upstream"].append(directory)
            else:
                tokenized = path.replace(version, "{version}")
                if base != version:
                    tokenized = tokenized.replace(base, "{baseVersion}")
                component.setdefault("licensePaths", [])
                if tokenized not in component["licensePaths"]:
                    component["licensePaths"].append(tokenized)

    for component in components.values():
        if "upstream" in component:
            component["upstream"].sort()
        if "licensePaths" in component:
            component["licensePaths"].sort()

    config = {
        "schemaVersion": 1,
        "generatedOn": date.today().isoformat(),
        "maven": {
            "command": "./mvnw",
            "module": DEFAULT_MODULE,
            "repository": DEFAULT_REPOSITORY,
            "dependencyPluginVersion": DEFAULT_DEPENDENCY_PLUGIN,
        },
        "outputs": {
            "inventory": DEFAULT_INVENTORY,
            "notices": DEFAULT_NOTICES,
            "noticesTemplate": DEFAULT_TEMPLATE,
            "licenses": "licenses",
        },
        "extraArtifacts": [
            {
                "groupId": "fish.payara.extras",
                "artifactId": "payara-micro",
                "versionExpression": "payara.version",
                "type": "jar",
                "scope": "runtime",
                "licenseDir": (
                    "licenses/payara/fish.payara.extras.payara-micro/"
                    "{version}/artifact"
                ),
                "extractPrefixes": ["MICRO-INF/legal/"],
                "licenseSummary": "CDDL + GPLv2 with classpath exception",
                "status": (
                    "original exact-version distribution legal files extracted; "
                    "custom build comparison pending"
                ),
                "upstreamSource": "scm:git:git@github.com:payara/payara.git",
            }
        ],
        "upstreamBundles": dict(sorted(bundles.items())),
        "components": dict(sorted(components.items())),
    }
    return config


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument(
        "--update", action="store_true", help="rewrite the generated files (default)"
    )
    action.add_argument(
        "--check",
        action="store_true",
        help="fail when the committed files or license material are out of date",
    )
    action.add_argument(
        "--bootstrap-config",
        action="store_true",
        help="create the config from the current RUNTIME_INVENTORY.csv",
    )
    parser.add_argument("--config", default=DEFAULT_CONFIG)
    parser.add_argument("--tree-file", help="reuse a Maven dependency:tree JSON file")
    parser.add_argument("--no-download", action="store_true", help="forbid network downloads")
    parser.add_argument("--no-prune", action="store_true", help="keep stale generated license dirs")
    parser.add_argument(
        "--local-repository",
        help="skip Maven settings lookup and use this local repository",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="exit non-zero when any referenced upstream license file is missing",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = Path(__file__).resolve().parents[1]
    config_path = (root / args.config).resolve()

    if args.bootstrap_config:
        inventory = root / DEFAULT_INVENTORY
        config = bootstrap_config(root, inventory)
        write_json(config_path, config)
        log(f"Wrote {config_path.relative_to(root)}")
        return 0

    if not config_path.exists():
        raise SystemExit(
            f"Config not found: {config_path}. Run --bootstrap-config once to create it."
        )
    config = read_json(config_path)
    if not args.check:
        config["generatedOn"] = date.today().isoformat()
    ctx = Context(root, config, args)

    rows, warnings, errors = generate(ctx)

    for warning in warnings:
        log(f"warning: {warning}")
    for error in errors:
        log(f"error: {error}")

    outputs = config.get("outputs", {})
    inventory_path = root / outputs.get("inventory", DEFAULT_INVENTORY)
    notices_path = root / outputs.get("notices", DEFAULT_NOTICES)
    expected_inventory = render_csv(rows)
    expected_notices = render_notices(ctx, rows)

    if args.check:
        failures: list[str] = []
        for path, expected in (
            (inventory_path, expected_inventory),
            (notices_path, expected_notices),
        ):
            actual = path.read_text(encoding="utf-8") if path.exists() else ""
            if actual != expected:
                diff = "".join(
                    difflib.unified_diff(
                        actual.splitlines(keepends=True),
                        expected.splitlines(keepends=True),
                        fromfile=f"committed/{path.relative_to(root)}",
                        tofile=f"generated/{path.relative_to(root)}",
                        n=3,
                    )
                )
                failures.append(f"{path.relative_to(root)} is out of date:\n{diff}")
        if errors:
            failures.append(
                "License material is incomplete:\n  " + "\n  ".join(errors)
            )
        if failures:
            log("\n".join(failures))
            return 1
        log(
            f"License bundle is up to date ({len(rows)} components; "
            f"{inventory_path.relative_to(root)}, {notices_path.relative_to(root)})."
        )
        return 0

    write_json(config_path, config)
    inventory_path.parent.mkdir(parents=True, exist_ok=True)
    inventory_path.write_text(expected_inventory, encoding="utf-8", newline="")
    notices_path.write_text(expected_notices, encoding="utf-8", newline="")
    log(
        f"Updated {inventory_path.relative_to(root)} and "
        f"{notices_path.relative_to(root)} ({len(rows)} components)."
    )
    if errors:
        log(f"{len(errors)} component(s) have incomplete license material; see errors above.")
        if args.strict:
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
