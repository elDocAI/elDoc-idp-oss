# elDoc third-party license bundle

Generated from the current POMs (elDoc revision 7.2026.4-SNAPSHOT). **Use this as the application license bundle, subject to the release checks below; it is not a certification of the final container.**

Included: `THIRD_PARTY_NOTICES.md`, original texts in `licenses/`, `RUNTIME_INVENTORY.csv`, and source/checksum evidence. The authoritative component list and count are in `RUNTIME_INVENTORY.csv`; test/build-only dependencies are not listed as runtime components. No JPEG2000 dependency appears in the resolved runtime inventory.

## Before distributing

1. **Compare the final WAR/Uber JAR.** Match its bundled JARs to `RUNTIME_INVENTORY.csv` (including classifiers/versions). Check shaded code, embedded JavaScript/fonts, manual `--addLibs` and build-time copying. Keep existing legal resources inside all JARs, especially PrimeFaces' bundled web assets. Copy this bundle's notices and entire `licenses/` directory into the release/container together with elDoc's own LICENSE.
2. **Custom Bytedeco natives.** Inventory every shipped binary and static dependency from your build logs/link maps. Match the supplied reference texts to your actual source revisions. Add Leptonica, OpenBLAS, codecs and optional OpenCV/Tesseract dependencies as applicable, preserving their copyright/notices. Dynamic-link inspection alone cannot find statically linked code. Fill `NATIVE_COMPONENTS.csv`; no rows are pre-certified.
3. **Payara.** Compare your redistributed Payara against the collected 7.2026.10 artifact/hash. If repackaged, patched or replaced, use that build's full legal files and check source availability/modification requirements under its actual licenses. The original two legal files are already included here. Provided Jakarta/container APIs are covered through the actual server/container distribution, not assumed covered by an API POM alone.
4. **Other natives and assets.** ONNX Runtime, WebP and Graal legal material is included; verify your packaged native variants and any runtime downloads. Inventory redistributed ONNX/model/tokenizer files separately. Check DJL's runtime-fetched native libraries if bundled or cached into the image.
5. **Container/JRE.** Retain Rocky/RPM and Java licensing metadata/files. Inventory the exact image/JRE and satisfy applicable source-availability or source-offer obligations; notices alone do not fulfill those obligations.

## Regenerate the bundle after a dependency upgrade

The bundle is generated from the actual Maven runtime graph, so do not edit `THIRD_PARTY_NOTICES.md` or `RUNTIME_INVENTORY.csv` by hand. After changing versions in the POMs run:

```sh
python3 scripts/license_inventory.py --update
```

The generator:

1. runs `maven-dependency-plugin:tree` for `eldoc-idp-web` with `-Dscope=runtime`;
2. uses local Maven JARs/POMs or downloads them from the configured repository and records their SHA-256;
3. extracts `LICENSE`, `NOTICE`, `COPYING`, `3RD-PARTY` and similar files from each artifact into `licenses/maven/<group>.<artifact>/<version>/artifact/`;
4. refreshes versioned upstream license bundles using the URLs in `scripts/license-inventory.json`;
5. rewrites both generated documents and removes license directories for versions that are no longer resolved.

Curated values (special license wording, upstream source URLs and extra evidence paths) live in `scripts/license-inventory.json`. Paths there may use `{version}`, `{baseVersion}` and `{classifier}` placeholders. When a version changes, upstream evidence is downloaded automatically when the bundle has a `baseUrl`; otherwise the run reports the missing files and marks the component as incomplete.

To verify in CI or before a commit:

```sh
python3 scripts/license_inventory.py --check
```

`--check` exits non-zero if the committed documents differ from the generated result or if referenced license material is missing. Use `--strict` with `--update` to fail on incomplete evidence. The underlying Maven command is:

```sh
./mvnw -pl modules/eldoc-idp-web -am org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree -Dscope=runtime -DoutputType=json -DoutputFile=runtime-tree.json
```

Releases use `maven.repository` (Maven Central by default). Snapshots use `maven.snapshotRepository.id` and `.url`, currently `sonatype-snapshots` at https://central.sonatype.com/repository/maven-snapshots/. The generator prefers `maven-metadata-<repository-id>.xml` in the local Maven repository, matching extension and classifier separately (a classified JAR still has an unclassified POM). It records timestamped snapshot URLs; if local metadata has no matching entry, it fetches fresh remote metadata and uses the timestamped artifact rather than a potentially stale local SNAPSHOT alias. Resolution fails clearly if no matching timestamp can be established; `--no-download` requires usable local metadata.

A floating `SNAPSHOT` is **not reproducible**. Regenerate whenever Maven updates a snapshot, even without a POM version change. Keep the Maven repository and packaged artifacts aligned; local metadata is provenance evidence, not proof that an alias was never overwritten. Remote fallback describes the remote selection, which may differ from an earlier application build. Before distribution, pin a release or the resolved timestamped artifact, retain its bytes/checksum, and compare against the packaged application. Timestamped artifacts may disappear from snapshot repositories.

For the PrimeFaces migration handoff, regenerate with `python scripts/license_inventory.py --update --no-prune` after the application changes are joined; review the resulting evidence without deleting older license directories. PrimeFlex's curated MIT/upstream entry does not replace collecting and reviewing its artifact legal files.

SHA-256 values in the CSV are provenance/integrity records, not a claim of signature verification. See https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html for scope/mediation rules.

## Evidence and limitations

`licenses/maven/` contains the legal files extracted from each resolved artifact. `licenses/upstream/` contains release-specific texts fetched from the upstream projects named in the CSV `upstream_source` column. `licenses/common/` contains canonical texts that are not shipped inside an artifact (currently Apache-2.0). The CSV is the provenance record for artifact URLs, checksums and legal file paths.

POM license data is cross-checked against the curated summaries in `scripts/license-inventory.json`; the generator reports wording changes for review but does not automatically reinterpret a license. Composite upstream legal files are retained intact; incidental mentions of build/test software do not expand this inventory.

For `de.taimos:totp:1.0`, the exact published POM declares Apache-2.0; the canonical license is included, but no separate license/notice was found in its source JAR or v1.0 source tree. Confirm with the maintainer if stronger provenance is required. The `eldoc-base-api` POM declares Hazelcast as provided without a version/parent; this needs correction or your normal external build context for a standalone build, but introduces no application runtime dependency here. Its own artifact was unavailable in Central; Maven reported that warning during graph resolution.

The bundle does not assert that third-party source is unmodified, select a license for the whole application, or establish legal coverage for unprovided binaries. The actual grants and copied notices control.
