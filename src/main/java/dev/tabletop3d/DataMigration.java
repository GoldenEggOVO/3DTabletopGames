package dev.tabletop3d;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Copies legacy plugin data once, retaining the source as a backup. */
final class DataMigration {
    private static final String MARKER = "migration-from-serverboards.txt";

    static void copyLegacy(Path old, Path current) throws IOException {
        if (!Files.exists(old, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(old))
            throw new IllegalStateException("Legacy plugin data is not a regular directory: " + old);
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isRegularFile(current.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) return;
            throw new IllegalStateException("Both old and new plugin data directories exist; resolve them before startup: " + old + " / " + current);
        }
        Path staging = current.resolveSibling(current.getFileName() + ".migrating-" + UUID.randomUUID());
        Files.createDirectories(staging);
        Files.walkFileTree(old, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(staging.resolve(old.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) throw new IOException("Unsupported legacy data entry: " + file);
                Files.copy(file, staging.resolve(old.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        Files.writeString(staging.resolve(MARKER), "Copied from " + old.getFileName() + "; original data was retained.\n");
        Files.move(staging, current);
    }

    private DataMigration() {}
}
