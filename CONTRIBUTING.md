# Contributing

Use Java 25 and Maven 3.9+. Build with `mvn -B -ntp package`; run Python migration tests with `python -m unittest discover -s tests -p "test_*.py"`.

Write executable source, comments, diagnostics and configuration instructions in English. Put translated UI wording in `src/main/resources/languages/`; fixed piece artwork uses named Unicode symbols. Keep non-English regression inputs in catalog fixtures or explicit Unicode notation.

Keep changes scoped to Tabletop. Casino is a reference project, not a runtime dependency. Preserve existing work, custom configuration, save schemas and upstream license notices. Add behavior tests for rule changes, coordinates, callbacks, permissions and recovery. Rendering tests should check entity reuse and the final rule state; client screenshots and feel require Minecraft acceptance.

Keep generated worlds, caches, credentials, server/plugin binaries and local acceptance packages out of Git. Runtime probes bind only to loopback and must never target production servers. Describe migration and validation limits in each change.

Ordinary commits may be synchronized to GitHub. Publishing a Release requires the maintainer's explicit approval after local acceptance. The CI workflow only builds, tests and uploads development artifacts.
