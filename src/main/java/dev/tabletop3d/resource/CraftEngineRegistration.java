package dev.tabletop3d.resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CraftEngineRegistration {
    private CraftEngineRegistration() {}

    public static int install(Path dataFolder) throws IOException {
        Path root = dataFolder.toAbsolutePath().normalize();
        Path pack = root.resolve("resources/tabletop3d");
        int added = 0;
        for (String name : List.of("pack.yml", "configuration/items.yml")) {
            Path target = pack.resolve(name);
            for (Path parent = target; parent != null && parent.startsWith(root); parent = parent.getParent())
                if (Files.isSymbolicLink(parent)) throw new IOException("CraftEngine resource path is a symbolic link: " + parent);
            if (Files.exists(target)) continue;
            try (var input = CraftEngineRegistration.class.getResourceAsStream("/craftengine/resources/tabletop3d/" + name)) {
                if (input == null) throw new IOException("Missing bundled CraftEngine registration: " + name);
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
                added++;
            }
        }
        return added;
    }
}
