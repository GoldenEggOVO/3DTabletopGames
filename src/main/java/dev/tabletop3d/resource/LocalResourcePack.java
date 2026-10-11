package dev.tabletop3d.resource;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

final class LocalResourcePack implements AutoCloseable {
    record Archive(String sha1, byte[] bytes) {}

    private static final Set<String> REQUIRED_ITEMS = bundledItems();

    private static Set<String> bundledItems() {
        try (var input = LocalResourcePack.class.getResourceAsStream("/tabletop-resource-pack.zip")) {
            if (input == null) throw new IOException("Missing bundled tabletop-resource-pack.zip");
            Set<String> items = new HashSet<>();
            try (var zip = new ZipInputStream(input)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;)
                    if (entry.getName().startsWith("assets/tabletop3d/items/") && entry.getName().endsWith(".json"))
                        items.add(entry.getName());
            }
            if (items.isEmpty()) throw new IOException("Missing bundled item definitions");
            return Set.copyOf(items);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private final Path source;
    private final HttpServer server;
    private final ExecutorService downloads = Executors.newVirtualThreadPerTaskExecutor();
    private volatile List<Archive> archives = List.of();
    private volatile boolean closed;

    LocalResourcePack(Path source, InetSocketAddress address) throws IOException {
        this.source = source;
        reload();
        server = HttpServer.create(address, 0);
        server.createContext("/tabletop3d/", this::download);
        server.setExecutor(downloads);
        server.start();
    }

    static void extractDefaults(Path source, InputStream defaults) throws IOException {
        if (defaults == null) throw new IOException("Missing bundled tabletop-resource-pack.zip");
        Path root = source.toAbsolutePath().normalize();
        try (var zip = new ZipInputStream(defaults)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                Path file = root.resolve(name).normalize();
                if (!file.startsWith(root) || !clientFile(name))
                    throw new IOException("Invalid bundled resource path: " + name);
                if (entry.isDirectory()) continue;
                for (Path parent = file; parent != null && parent.startsWith(root); parent = parent.getParent())
                    if (Files.isSymbolicLink(parent)) throw new IOException("Resource path is a symbolic link: " + parent);
                if (Files.exists(file)) continue;
                Files.createDirectories(file.getParent());
                Files.copy(zip, file);
            }
        }
    }

    private static boolean clientFile(String name) {
        return name.equals("pack.mcmeta") || name.startsWith("assets/") || name.startsWith("licenses/");
    }

    Archive current() {
        return archives.getFirst();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String url(String base) {
        return url(base, current());
    }

    String url(String base, Archive archive) {
        URI uri = URI.create(base);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("Local resource-pack public-url must be an HTTP(S) base URL");
        return base.replaceAll("/+$", "") + "/tabletop3d/" + archive.sha1() + ".zip";
    }

    synchronized void reload() throws IOException {
        if (closed) throw new IOException("Resource-pack host is closed");
        Map<String, byte[]> files = new TreeMap<>();
        if (Files.isSymbolicLink(source)) throw new IOException("Resource folder is a symbolic link");
        try (var paths = Files.walk(source)) {
            for (Path file : paths.toList()) {
                String name = source.relativize(file).toString().replace('\\', '/');
                if (!clientFile(name)) continue;
                if (Files.isSymbolicLink(file)) throw new IOException("Resource path is a symbolic link: " + name);
                if (Files.isRegularFile(file)) files.put(name, Files.readAllBytes(file));
            }
        }
        validate(files);
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.setLevel(9);
            for (var file : files.entrySet()) {
                var entry = new ZipEntry(file.getKey());
                entry.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        byte[] bytes = output.toByteArray();
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        if (closed) throw new IOException("Resource-pack host is closed");
        Path target = source.resolveSibling("resource-pack.zip");
        Path temporary = Files.createTempFile(target.getParent(), "resource-pack-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        Archive next = new Archive(hash, bytes);
        List<Archive> previous = archives;
        if (previous.isEmpty()) archives = List.of(next);
        else if (!previous.getFirst().sha1().equals(hash)) archives = List.of(next, previous.getFirst());
    }

    private static JsonObject json(String name, byte[] bytes) throws IOException {
        if (bytes == null) throw new IOException("Missing resource: " + name);
        try (var reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON content: " + name);
            return object;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid resource JSON: " + name, exception);
        }
    }

    private static void reference(Map<String, byte[]> files, String id, String folder, String suffix) throws IOException {
        if (id.startsWith("#") || id.startsWith("minecraft:")) return;
        String[] parts = id.split(":", 2);
        if (parts.length != 2 || !files.containsKey("assets/" + parts[0] + "/" + folder + "/" + parts[1] + suffix))
            throw new IOException("Missing resource reference: " + id);
    }

    private static void validate(Map<String, byte[]> files) throws IOException {
        try {
            JsonObject metadata = json("pack.mcmeta", files.get("pack.mcmeta")).getAsJsonObject("pack");
            JsonElement description = metadata == null ? null : metadata.get("description");
            if (description == null || description.isJsonNull()
                    || description.isJsonPrimitive() && !description.getAsJsonPrimitive().isString())
                throw new IOException("Missing pack description");
            if (metadata.has("pack_format")) format(metadata.get("pack_format"));
            else if (!metadata.has("min_format") || !metadata.has("max_format"))
                throw new IOException("Missing pack format");
            if (metadata.has("min_format")) format(metadata.get("min_format"));
            if (metadata.has("max_format")) format(metadata.get("max_format"));
        } catch (RuntimeException exception) {
            throw new IOException("Invalid pack metadata", exception);
        }
        var missing = REQUIRED_ITEMS.stream().filter(name -> !files.containsKey(name)).sorted().toList();
        if (!missing.isEmpty()) throw new IOException("Missing tabletop item definitions: " + missing);
        for (var file : files.entrySet()) {
            String name = file.getKey();
            byte[] bytes = file.getValue();
            if (name.endsWith(".png")) {
                byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
                if (bytes.length < 24 || !Arrays.equals(signature, Arrays.copyOf(bytes, 8))
                        || ImageIO.read(new ByteArrayInputStream(bytes)) == null)
                    throw new IOException("Invalid PNG texture: " + name);
            }
            if (!name.endsWith(".json")) continue;
            JsonObject object = json(name, bytes);
            try {
                if (name.contains("/models/") && object.has("textures"))
                    for (var texture : object.getAsJsonObject("textures").entrySet())
                        reference(files, texture.getValue().getAsString(), "textures", ".png");
                if (name.contains("/items/") && object.has("model")) {
                    var model = object.getAsJsonObject("model");
                    if (model.has("model")) reference(files, model.get("model").getAsString(), "models", ".json");
                }
                if (REQUIRED_ITEMS.contains(name)) {
                    var model = object.getAsJsonObject("model");
                    String type = model.get("type").getAsString();
                    if (type.isBlank() || type.equals("minecraft:model") && !model.has("model"))
                        throw new IOException("Missing item model: " + name);
                }
                if (name.endsWith("/sounds.json"))
                    for (var event : object.entrySet())
                        for (var sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds"))
                            reference(files, sound.isJsonPrimitive() ? sound.getAsString()
                                    : sound.getAsJsonObject().get("name").getAsString(), "sounds", ".ogg");
            } catch (RuntimeException exception) {
                throw new IOException("Invalid resource definition: " + name, exception);
            }
        }
    }

    private static void format(JsonElement value) throws IOException {
        if (value.isJsonArray()) {
            var version = value.getAsJsonArray();
            if (version.size() != 2) throw new IOException("Invalid pack format");
            format(version.get(0));
            if (!version.get(1).getAsJsonPrimitive().isNumber()
                    || version.get(1).getAsBigDecimal().intValueExact() < 0)
                throw new IOException("Invalid pack format");
        } else if (!value.getAsJsonPrimitive().isNumber() || value.getAsBigDecimal().intValueExact() <= 0)
            throw new IOException("Invalid pack format");
    }

    private void download(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!Set.of("GET", "HEAD").contains(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            Archive archive = archives.stream()
                    .filter(candidate -> path.equals("/tabletop3d/" + candidate.sha1() + ".zip"))
                    .findFirst().orElse(null);
            if (archive == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(archive.bytes().length));
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            exchange.getResponseHeaders().set("ETag", "\"" + archive.sha1() + "\"");
            boolean head = exchange.getRequestMethod().equals("HEAD");
            exchange.sendResponseHeaders(200, head ? -1 : archive.bytes().length);
            if (!head) exchange.getResponseBody().write(archive.bytes());
        }
    }

    @Override
    public void close() {
        closed = true;
        server.stop(0);
        downloads.shutdownNow();
    }
}
