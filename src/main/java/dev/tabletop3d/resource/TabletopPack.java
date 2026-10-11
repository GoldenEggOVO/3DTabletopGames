package dev.tabletop3d.resource;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.text.Language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

public final class TabletopPack implements Listener, AutoCloseable {
    private static final byte[] RESOURCE_PACK_HASH = bundledHash();

    private static byte[] bundledHash() {
        try (var input = TabletopPack.class.getResourceAsStream("/resource-pack.sha1")) {
            if (input == null) throw new IOException("Missing generated resource-pack.sha1");
            byte[] hash =
                    HexFormat.of()
                            .parseHex(
                                    new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                                            .trim());
            if (hash.length != 20) throw new IOException("Invalid generated resource-pack SHA-1");
            return hash;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public enum Mode {
        VANILLA,
        RESOURCE_PACK,
        MIXED
    }

    private enum Stage {
        LOADING,
        READY,
        FAILED
    }

    private record Request(UUID id, Stage stage) {}

    private final Tabletop3D plugin;
    private final BooleanSupplier available;
    private final Function<String, ItemStack> models;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final NamespacedKey preference = new NamespacedKey("tabletop3d", "resource_pack");
    private final Path localFolder;
    private final InetSocketAddress localAddress;
    private final String publicUrl;
    private volatile LocalResourcePack local;
    private volatile boolean closed;
    private boolean reloading;
    public final Mode mode;

    public TabletopPack(Tabletop3D plugin) {
        this(plugin, new CraftEngineModels());
    }

    private TabletopPack(Tabletop3D plugin, CraftEngineModels models) {
        this(plugin, models::ready, models::item);
    }

    public TabletopPack(Tabletop3D plugin, BooleanSupplier available, Function<String, ItemStack> models) {
        this.plugin = plugin;
        this.available = available;
        this.models = models;
        mode =
                switch (plugin.getConfig().getString("rendering.mode", "auto")) {
                    case "auto" -> org.bukkit.Bukkit.getPluginManager().isPluginEnabled("CraftEngine")
                            ? Mode.MIXED : Mode.VANILLA;
                    case "resource-pack" -> Mode.RESOURCE_PACK;
                    case "mixed" -> Mode.MIXED;
                    case "vanilla" -> Mode.VANILLA;
                    default ->
                            throw new IllegalArgumentException(
                                    "rendering.mode must be auto, vanilla, resource-pack or mixed");
                };
        var config = plugin.getConfig();
        String source = config.contains("rendering.resource-pack.source", true)
                ? config.getString("rendering.resource-pack.source")
                : config.getString("rendering.resource-pack.url", "").isBlank() ? "local" : "external";
        if (!Set.of("local", "external").contains(source))
            throw new IllegalArgumentException("rendering.resource-pack.source must be local or external");
        publicUrl = config.getString("rendering.resource-pack.local.public-url", "");
        if (mode != Mode.VANILLA && source.equals("local")) {
            localFolder = plugin.getDataFolder().toPath().resolve("resource-pack");
            localAddress = new InetSocketAddress(
                    config.getString("rendering.resource-pack.local.bind-address", "0.0.0.0"),
                    config.getInt("rendering.resource-pack.local.port", 8137));
            try {
                local = createLocal();
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().warning("Cannot initialize local resource pack: " + exception.getMessage());
            }
        } else {
            localFolder = null;
            localAddress = null;
        }
    }

    private LocalResourcePack createLocal() throws IOException {
        LocalResourcePack.extractDefaults(localFolder,
                TabletopPack.class.getResourceAsStream("/tabletop-resource-pack.zip"));
        return new LocalResourcePack(localFolder, localAddress);
    }

    public void reload(org.bukkit.command.CommandSender sender) {
        if (closed || localFolder == null) {
            sender.sendMessage(Language.component("pack.reload-unavailable"));
            return;
        }
        if (reloading) {
            sender.sendMessage(Language.component("pack.reload-busy"));
            return;
        }
        reloading = true;
        String previous = local == null ? "" : local.current().sha1();
        sender.sendMessage(Language.component("pack.reloading"));
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LocalResourcePack next = local;
                if (next == null) {
                    next = createLocal();
                    local = next;
                } else next.reload();
                if (closed) {
                    next.close();
                    return;
                }
                String hash = next.current().sha1();
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    reloading = false;
                    if (closed) return;
                    boolean changed = !previous.equals(hash);
                    if (changed) {
                        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                            if (!preferred(player)) continue;
                            remove(player);
                            request(player);
                            if (plugin.menus != null) plugin.menus.refresh(player);
                        }
                    }
                    sender.sendMessage(Language.component(changed ? "pack.reloaded" : "pack.reload-unchanged"));
                });
            } catch (IOException | RuntimeException exception) {
                if (closed) return;
                plugin.getLogger().warning("Cannot reload local resource pack: " + exception.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    reloading = false;
                    if (!closed) sender.sendMessage(Language.component("pack.reload-failed"));
                });
            }
        });
    }

    private String localUrl(Player player, LocalResourcePack resources, LocalResourcePack.Archive archive) {
        String base = publicUrl;
        if (base.isBlank()) {
            var host = player.getVirtualHost();
            if (host == null) throw new IllegalArgumentException("Set local resource-pack public-url");
            try {
                base = new URI("http", null, host.getHostString(), resources.port(), "", null, null).toString();
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Invalid local resource-pack host", exception);
            }
        }
        return resources.url(base, archive);
    }

    public boolean hasToggle() {
        return mode == Mode.MIXED;
    }

    boolean preferred(Player player) {
        return mode == Mode.RESOURCE_PACK
                || mode == Mode.MIXED
                        && player.getPersistentDataContainer()
                                        .getOrDefault(preference, PersistentDataType.BYTE, (byte) 0)
                                == 1;
    }

    public boolean packed(Player player) {
        Request r = requests.get(player.getUniqueId());
        return mode != Mode.VANILLA
                && preferred(player)
                && r != null
                && r.stage() == Stage.READY
                && available.getAsBoolean();
    }

    public boolean canPlay(Player player, String kind) {
        return mode != Mode.RESOURCE_PACK || !supported(kind) || packed(player);
    }

    public static boolean supported(String kind) {
        return kind.equals("mahjong") || kind.equals("color-eight") || kind.equals("yacht") || PackedBoardModels.supported(kind)
                || Set.of("doudizhu", "liars-bar", "texas-holdem").contains(kind);
    }

    public ItemStack item(String id) {
        return models.apply(id);
    }

    public UUID requestId(Player player) {
        Request r = requests.get(player.getUniqueId());
        return r == null ? null : r.id();
    }

    public void require(Player player, String kind) {
        if (canPlay(player, kind)) return;
        Request r = requests.get(player.getUniqueId());
        if (r == null || r.stage() == Stage.FAILED) request(player);
        throw new RuleViolation("pack.required", "Load the tabletop resource pack before playing");
    }

    public Component button(Player player) {
        Request r = requests.get(player.getUniqueId());
        return Language.component(
                        r != null && r.stage() == Stage.LOADING
                                ? "pack.loading"
                                : packed(player) ? "pack.disable" : "pack.enable")
                .color(
                        r != null && r.stage() == Stage.LOADING
                                ? NamedTextColor.AQUA
                                : packed(player) ? NamedTextColor.GOLD : NamedTextColor.GREEN);
    }

    public void toggle(Player player) {
        if (!hasToggle()) return;
        Request r = requests.get(player.getUniqueId());
        if (r != null && r.stage() != Stage.FAILED) {
            player.getPersistentDataContainer().set(preference, PersistentDataType.BYTE, (byte) 0);
            remove(player);
        } else {
            player.getPersistentDataContainer().set(preference, PersistentDataType.BYTE, (byte) 1);
            request(player);
        }
    }

    void request(Player player) {
        if (mode == Mode.VANILLA || !preferred(player)) return;
        Request existing = requests.get(player.getUniqueId());
        if (existing != null && existing.stage() != Stage.FAILED) return;
        if (!available.getAsBoolean()) {
            plugin.tell(player, Language.component("pack.unavailable"));
            return;
        }
        String url;
        byte[] hash;
        try {
            if (localFolder == null) {
                url = plugin.getConfig().getString("rendering.resource-pack.url", "");
                hash = RESOURCE_PACK_HASH.clone();
            } else {
                LocalResourcePack resources = local;
                if (resources == null) throw new IllegalArgumentException("Local resource pack is unavailable");
                LocalResourcePack.Archive archive = resources.current();
                url = localUrl(player, resources, archive);
                hash = HexFormat.of().parseHex(archive.sha1());
            }
            URI uri = URI.create(url);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            plugin.tell(player, Language.component("pack.configuration"));
            return;
        }
        remove(player);

        UUID id = UUID.randomUUID();
        requests.put(player.getUniqueId(), new Request(id, Stage.LOADING));
        try {
            player.addResourcePack(
                    id,
                    url,
                    hash,
                    dev.tabletop3d.ui.MessageText.plain(Language.component("pack.prompt")),
                    false);
        } catch (IllegalArgumentException ex) {
            requests.put(player.getUniqueId(), new Request(id, Stage.FAILED));
            plugin.tell(player, Language.component("pack.failed"));
        }
    }

    public void status(Player player, UUID id, PlayerResourcePackStatusEvent.Status status) {
        Request r = requests.get(player.getUniqueId());
        if (r == null || !r.id().equals(id) || r.stage() == Stage.FAILED || !preferred(player))
            return;
        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                requests.put(player.getUniqueId(), new Request(id, Stage.READY));
                plugin.tell(player, Language.component("pack.ready"));
            }
            case ACCEPTED, DOWNLOADED -> {}
            default -> {
                requests.put(player.getUniqueId(), new Request(id, Stage.FAILED));
                plugin.tell(player, Language.component("pack.failed"));
            }
        }
        if (status != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && status != PlayerResourcePackStatusEvent.Status.DOWNLOADED
                && plugin.menus != null) plugin.menus.refresh(player);
    }

    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        status(event.getPlayer(), event.getID(), event.getStatus());
    }

    @EventHandler
    public void joined(PlayerJoinEvent event) {
        org.bukkit.Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (event.getPlayer().isOnline()) request(event.getPlayer());
                        },
                        20);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
    }

    private void remove(Player player) {
        Request r = requests.remove(player.getUniqueId());
        if (r != null) player.removeResourcePack(r.id());
    }

    @Override
    public void close() {
        closed = true;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) remove(player);
        requests.clear();
        if (local != null) local.close();
    }
}
