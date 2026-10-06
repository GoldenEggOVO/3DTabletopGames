# Build and verification

## Build and unit tests

Use JDK 25 and Maven 3.9+:

```sh
mvn -B -ntp package
python -m compileall -q tools
```

Maven runs the Java tests and writes results to `target/surefire-reports/`. Deploy the shaded JAR, not `original-*.jar`.

## Resource and native-head checks

Use Python 3.12+ with Pillow and SoundFile/libsndfile. Run the Java build first to export board models, then generate the resource pack and run tooling tests:

```sh
python tools/build-resource-pack.py
python -m unittest discover -s tools -p "test_*.py"
mvn -B -ntp package
python tools/package_source.py
```

Pack generation updates the checksum embedded in the plugin, so rebuild the JAR afterward and deliver its matching ZIP. Sources live under `resource-pack/`; generated files and previews remain under `target/`.

## Isolated server checks

`tools/standalone-probe/run_standalone.py` creates an isolated loopback fixture for startup, gameplay, restart, replay and entity cleanup checks. Supply `--server-dir` with a prepared Purpur 26.2 cache containing `purpur-2622.jar`, libraries and an already accepted EULA. Use `--maven-repo` for the dependency cache. Optional `--craftengine-jar` and `--craftengine-cache` enable resource-model checks.

`tools/standalone-probe/run_soak.py` checks repeated gameplay, replay and cleanup in another isolated fixture. These probes do not connect a Minecraft client. Their JSON receipts and logs belong in local build outputs, not source documentation.

## Client acceptance

Unit tests, server probes and generated previews do not establish actual client rendering, sound balance, privacy between real clients or frame-time performance. Use the [client checklist](acceptance.zh-CN.md).

Keep exact build, deployment and hash results with the corresponding local delivery.
