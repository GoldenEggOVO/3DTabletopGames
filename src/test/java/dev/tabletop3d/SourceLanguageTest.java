package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class SourceLanguageTest {
    @Test
    void executableSourceKeepsLocalizedProseInCatalogues() throws Exception {
        List<String> violations = new ArrayList<>();
        for (String folder : List.of("src/main/java", "src/test/java", "tools")) {
            try (var files = Files.walk(Path.of(folder))) {
                for (Path file :
                        files.filter(Files::isRegularFile)
                                .filter(
                                        p ->
                                                p.toString().endsWith(".java")
                                                        || p.toString().endsWith(".py"))
                                .toList()) {
                    var lines = Files.readAllLines(file);
                    for (int line = 0; line < lines.size(); line++) {
                        if (lines.get(line)
                                .codePoints()
                                .anyMatch(
                                        c ->
                                                Character.UnicodeScript.of(c)
                                                        == Character.UnicodeScript.HAN))
                            violations.add(file + ":" + (line + 1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Non-English source text: " + violations);
    }
}
