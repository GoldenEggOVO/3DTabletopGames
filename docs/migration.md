# Updating the current Tabletop build

The current build keeps `3dtabletop` configuration, semantic `languages/` catalogs and schema 1 room data. Menu layouts are maintained in code; captions and styling remain editable in the language catalogs. CraftEngine remains optional. Old ServerBoards imports, 1.8.10 language converters, retired Aeroplane games and old game aliases are no longer supported.

1. Stop the server cleanly and back up the complete plugin directory.
2. Check the room file against the exact candidate shaded JAR:

   ```sh
   java -cp target/3dtabletop-1.10.28-SNAPSHOT.jar dev.tabletop3d.RoomReplayVerifier /backup/3dtabletop/rooms.json
   ```

3. Replace the plugin with the new shaded JAR. Preserve current rooms, custom language values and other plugins.
4. Use the matching delivered resource pack when pack rendering is enabled. The plugin supplies the pack hash and ID; configuration only supplies its URL.
5. Start the server and verify saved rooms, menus and player interactions. Keep the complete backup for rollback.

Recorded actions use the same validation as live actions. Historical Color Eight exhausted-deck passes or draws are no longer accepted. Finish retired games with their previous build before upgrading; they are not converted into a different game. Use a previous source revision for a one-time legacy conversion, then validate the resulting current-format data before installation.
