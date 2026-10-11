package dev.tabletop3d.resource;

import dev.tabletop3d.support.WorkspaceTempFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class LocalResourcePackTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path data;

    private byte[] png(int color) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private Path fixture() throws Exception {
        Path source = data.resolve("resource-pack");
        Files.createDirectories(source.resolve("assets/tabletop3d/textures/item/color_eight"));
        Files.createDirectories(source.resolve("assets/tabletop3d/models/item/color_eight"));
        Files.createDirectories(source.resolve("assets/tabletop3d/items/color_eight"));
        Files.createDirectories(source.resolve("licenses"));
        Files.writeString(source.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":88,\"description\":\"Test\"}}");
        Files.write(texture(source), png(0xff0000ff));
        Files.writeString(source.resolve("assets/tabletop3d/models/item/color_eight/card_b2.json"),
                "{\"textures\":{\"face\":\"tabletop3d:item/color_eight/color_eight_blue_2_face\"}}");
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/tabletop-resource-pack.zip"))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                String name = entry.getName();
                if (!name.startsWith("assets/tabletop3d/items/") || !name.endsWith(".json")) continue;
                Path item = source.resolve(name);
                Files.createDirectories(item.getParent());
                Files.writeString(item,
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"tabletop3d:item/color_eight/card_b2\"}}");
            }
        }
        Files.writeString(source.resolve("licenses/test.txt"), "Test license");
        return source;
    }

    private Path texture(Path source) {
        return source.resolve("assets/tabletop3d/textures/item/color_eight/color_eight_blue_2_face.png");
    }

    private LocalResourcePack start(Path source) throws Exception {
        return new LocalResourcePack(source, new InetSocketAddress("127.0.0.1", 0));
    }

    private HttpResponse<byte[]> get(HttpClient client, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void sameNameReplacementChangesDownloadAndHashWithoutChangingAnyJar() throws Exception {
        Path source = fixture();
        Files.writeString(data.resolve("plugin.jar"), "Fixed plugin");
        try (var pack = start(source); var client = HttpClient.newHttpClient()) {
            String oldUrl = pack.url("http://127.0.0.1:" + pack.port());
            var original = get(client, oldUrl);
            assertEquals(200, original.statusCode());
            assertEquals(pack.current().sha1(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(original.body())));
            byte[] replacement = png(0xff00ff00);
            Files.write(texture(source), replacement);
            pack.reload();
            String newUrl = pack.url("http://127.0.0.1:" + pack.port());
            assertNotEquals(oldUrl, newUrl);
            var download = get(client, newUrl);
            try (var zip = new ZipInputStream(new ByteArrayInputStream(download.body()))) {
                boolean found = false;
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if (entry.getName().endsWith("color_eight_blue_2_face.png")) {
                        assertArrayEquals(replacement, zip.readAllBytes());
                        found = true;
                    }
                }
                assertTrue(found);
            }
            assertArrayEquals(original.body(), get(client, oldUrl).body(), "In-flight old downloads remain valid");
            assertEquals("Fixed plugin", Files.readString(data.resolve("plugin.jar")));
            assertArrayEquals(download.body(), Files.readAllBytes(data.resolve("resource-pack.zip")));
        }
    }

    @Test
    void brokenTextureJsonOrReferenceKeepsLastUsableArchive() throws Exception {
        Path source = fixture();
        try (var pack = start(source)) {
            String hash = pack.current().sha1();
            byte[] archive = Files.readAllBytes(data.resolve("resource-pack.zip"));
            byte[] image = Files.readAllBytes(texture(source));
            Files.writeString(texture(source), "Not a PNG");
            assertThrows(IOException.class, pack::reload);
            Files.write(texture(source), image);
            Path model = source.resolve("assets/tabletop3d/models/item/color_eight/card_b2.json");
            String originalModel = Files.readString(model);
            Files.writeString(model, "{");
            assertThrows(IOException.class, pack::reload);
            Files.writeString(model, originalModel.replace("color_eight_blue_2_face", "missing"));
            assertThrows(IOException.class, pack::reload);
            assertEquals(hash, pack.current().sha1());
            assertArrayEquals(archive, Files.readAllBytes(data.resolve("resource-pack.zip")));
        }
    }

    @Test
    void packingIsStableAndOnlyIncludesClientAssets() throws Exception {
        Path source = fixture();
        Files.createDirectories(source.resolve("originals"));
        Files.writeString(source.resolve("originals/private.txt"), "Excluded");
        try (var pack = start(source)) {
            String hash = pack.current().sha1();
            pack.reload();
            assertEquals(hash, pack.current().sha1());
            try (var archive = new ZipFile(data.resolve("resource-pack.zip").toFile())) {
                assertNull(archive.getEntry("originals/private.txt"));
                assertNotNull(archive.getEntry("pack.mcmeta"));
                assertNotNull(archive.getEntry("licenses/test.txt"));
            }
        }
    }

    @Test
    void missingRuntimeItemKeepsThePreviousPack() throws Exception {
        Path source = fixture();
        try (var pack = start(source)) {
            String hash = pack.current().sha1();
            Files.delete(source.resolve("assets/tabletop3d/items/color_eight/card_b2.json"));
            assertThrows(IOException.class, pack::reload);
            assertEquals(hash, pack.current().sha1());
        }
    }

    @Test
    void movedRuntimeItemKeepsThePreviousPack() throws Exception {
        Path source = fixture();
        try (var pack = start(source)) {
            String hash = pack.current().sha1();
            Files.createDirectories(source.resolve("assets/tabletop3d/items/backup"));
            Files.move(source.resolve("assets/tabletop3d/items/color_eight/card_b2.json"),
                    source.resolve("assets/tabletop3d/items/backup/card_b2.json"));
            assertThrows(IOException.class, pack::reload);
            assertEquals(hash, pack.current().sha1());
        }
    }

    @Test
    void invalidMetadataOrRequiredItemKeepsArchiveAndFile() throws Exception {
        Path source = fixture();
        try (var pack = start(source)) {
            String hash = pack.current().sha1();
            byte[] archive = Files.readAllBytes(data.resolve("resource-pack.zip"));
            Path metadata = source.resolve("pack.mcmeta");
            String original = Files.readString(metadata);
            for (String invalid : List.of("{\"pack\":null}", "{\"pack\":{}}",
                    "{\"pack\":{\"description\":\"Test\"}}",
                    "{\"pack\":{\"pack_format\":88}}",
                    "{\"pack\":{\"pack_format\":-1,\"description\":\"Test\"}}")) {
                Files.writeString(metadata, invalid);
                assertThrows(IOException.class, pack::reload, invalid);
                assertEquals(hash, pack.current().sha1());
                assertArrayEquals(archive, Files.readAllBytes(data.resolve("resource-pack.zip")));
            }
            Files.writeString(metadata, original);
            Path item = source.resolve("assets/tabletop3d/items/color_eight/card_b2.json");
            for (String invalid : List.of("{}", "{\"model\":null}",
                    "{\"model\":{\"type\":\"minecraft:model\"}}")) {
                Files.writeString(item, invalid);
                assertThrows(IOException.class, pack::reload, invalid);
                assertEquals(hash, pack.current().sha1());
                assertArrayEquals(archive, Files.readAllBytes(data.resolve("resource-pack.zip")));
            }
        }
    }

    @Test
    void modernFormatRangeAndComponentDescriptionCanBeReloaded() throws Exception {
        Path source = fixture();
        try (var pack = start(source)) {
            Files.writeString(source.resolve("pack.mcmeta"),
                    "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,1],\"description\":{\"text\":\"Custom\"}}}");
            pack.reload();
        }
    }

    @Test
    void defaultsFillMissingFilesAndPreserveCustomTextures() throws Exception {
        Path source = fixture();
        byte[] custom = Files.readAllBytes(texture(source));
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(source.relativize(texture(source)).toString().replace('\\', '/')));
            zip.write(png(0xffff0000));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("licenses/new.txt"));
            zip.write("New license".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        LocalResourcePack.extractDefaults(source, new ByteArrayInputStream(bytes.toByteArray()));
        assertArrayEquals(custom, Files.readAllBytes(texture(source)));
        assertEquals("New license", Files.readString(source.resolve("licenses/new.txt")));
    }

    @Test
    void defaultArchiveCannotWriteOutsideTheResourceFolder() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../config.yml"));
            zip.write("Overwrite".getBytes());
        }
        assertThrows(IOException.class, () -> LocalResourcePack.extractDefaults(
                data.resolve("resource-pack"), new ByteArrayInputStream(bytes.toByteArray())));
        assertFalse(Files.exists(data.resolve("config.yml")));
    }

    @Test
    void endpointServesOnlyArchivesSupportsHeadAndReleasesItsPort() throws Exception {
        Path source = fixture();
        int port;
        try (var pack = start(source); var client = HttpClient.newHttpClient()) {
            port = pack.port();
            String url = pack.url("http://127.0.0.1:" + port);
            var head = client.send(HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
            assertEquals(Files.size(data.resolve("resource-pack.zip")),
                    Long.parseLong(head.headers().firstValue("Content-Length").orElseThrow()));
            assertEquals(404, get(client, "http://127.0.0.1:" + port + "/tabletop3d/config.yml").statusCode());
            var post = client.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            assertEquals(405, post.statusCode());
        }
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }
}
