# Local build and verification

GitHub Actions is disabled. Run validation locally before pushing source.

Use JDK 25, Maven 3.9+ and Python 3.12+:

```sh
mvn -B -ntp package
python -m unittest discover -s tests -p "test_*.py"
python tools/package_source.py
```

Java results are written to `target/surefire-reports/`; generated artifacts remain in `target/`. Install the shaded JAR, not `original-*.jar`.

Check saved-room recovery and gameplay on an isolated server. Minecraft client rendering, interaction and sounds need in-game acceptance. Keep current local validation outputs outside source documentation.
