package dev.tabletop3d;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

class TabletopPackTest {
    Tabletop3D plugin;
    Player player;
    YamlConfiguration config;
    TabletopPack pack;
    boolean available = true;

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        plugin = mock(Tabletop3D.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        var data = server.addPlayer().getPersistentDataContainer();
        when(player.getPersistentDataContainer()).thenReturn(data);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        config.set("rendering.mode", "mixed");
        config.set("rendering.resource-pack.url", "https://example.org/tabletop.zip");
        pack = new TabletopPack(plugin, () -> available, id -> new ItemStack(Material.PAPER));
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void terminalOwnedStatusRefreshesOpenMenuAndInvalidatesOldActions() throws Exception {
        when(player.getWorld()).thenReturn(Bukkit.getWorlds().getFirst());
        when(plugin.allowed(player)).thenReturn(true);
        plugin.menus = new GameMenus(plugin);
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, GameMenus.Session> sessions =
                (Map<UUID, GameMenus.Session>) field.get(plugin.menus);
        for (Status terminal : List.of(Status.SUCCESSFULLY_LOADED, Status.FAILED_DOWNLOAD)) {
            pack = new TabletopPack(plugin, () -> available, id -> new ItemStack(Material.PAPER));
            plugin.pack = pack;
            pack.toggle(player);
            UUID request = pack.requestId(player);
            plugin.menus.show(player, "Test", "", List.of(), null, "catalog");
            var loading = sessions.get(player.getUniqueId());
            pack.status(player, request, terminal);
            var updated = sessions.get(player.getUniqueId());
            assertNotEquals(
                    loading.token(), updated.token(), "Terminal status must redraw an active menu");
            assertEquals(pack.button(player), updated.buttons().getFirst().component());
            plugin.menus.handle(player, "3dtabletop:" + loading.token() + " 0");
            assertEquals(
                    request, pack.requestId(player), "Old loading-menu action must be invalid");
            plugin.menus.forget(player);
            pack.status(player, request, terminal);
            assertFalse(plugin.menus.active(player), "A closed menu must stay closed");
        }
    }

    @Test
    void toggleAppearsFirstOnlyOnMainCatalog() throws Exception {
        when(player.getWorld()).thenReturn(Bukkit.getWorlds().getFirst());
        when(plugin.allowed(player)).thenReturn(true);
        plugin.pack = pack;
        plugin.menus = new GameMenus(plugin);
        var field = GameMenus.class.getDeclaredField("sessions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, GameMenus.Session> sessions =
                (Map<UUID, GameMenus.Session>) field.get(plugin.menus);
        for (String page : GameMenuLayouts.PAGES) {
            plugin.menus.show(
                    player,
                    "Test",
                    "",
                    List.of(new GameMenus.Button("entry", "Entry", () -> {})),
                    null,
                    page);
            var buttons = sessions.get(player.getUniqueId()).buttons();
            assertEquals(
                    page.equals("catalog"),
                    buttons.stream().anyMatch(b -> b.id().equals("resource-pack")),
                    page);
            if (page.equals("catalog")) assertEquals("resource-pack", buttons.getFirst().id());
        }
    }

    @Test
    void requestsUseBundledHashAndGeneratedIdentityWithoutConfigFields() {
        config.set("rendering.resource-pack.sha1", null);
        config.set("rendering.resource-pack.uuid", null);
        pack.toggle(player);
        UUID request = pack.requestId(player);
        assertNotNull(request);
        verify(player)
                .addResourcePack(
                        eq(request),
                        eq("https://example.org/tabletop.zip"),
                        argThat(hash -> hash != null && hash.length == 20),
                        anyString(),
                        eq(false));
    }

    @Test
    void toggleColoursDistinguishEnableLoadingAndDisable() {
        assertEquals(
                net.kyori.adventure.text.format.NamedTextColor.GREEN, pack.button(player).color());
        pack.toggle(player);
        assertEquals(
                net.kyori.adventure.text.format.NamedTextColor.AQUA, pack.button(player).color());
        pack.status(player, pack.requestId(player), Status.SUCCESSFULLY_LOADED);
        assertEquals(
                net.kyori.adventure.text.format.NamedTextColor.GOLD, pack.button(player).color());
    }

    @Test
    void onlySuccessfulOwnedPackLoadsModels() {
        assertFalse(pack.packed(player));
        pack.toggle(player);
        UUID request = pack.requestId(player);
        assertNotNull(request);
        assertFalse(pack.packed(player));
        pack.status(player, UUID.randomUUID(), Status.SUCCESSFULLY_LOADED);
        assertFalse(pack.packed(player));
        pack.status(player, request, Status.ACCEPTED);
        assertFalse(pack.packed(player));
        pack.status(player, request, Status.SUCCESSFULLY_LOADED);
        assertTrue(pack.packed(player));
    }

    @Test
    void closingAndRetryingRejectsOldCallbacksAndOnlyRemovesOwnedPack() {
        pack.toggle(player);
        UUID first = pack.requestId(player);
        pack.toggle(player);
        verify(player).removeResourcePack(first);
        verify(player, never()).removeResourcePacks();
        pack.toggle(player);
        UUID second = pack.requestId(player);
        assertNotEquals(first, second);
        pack.status(player, first, Status.SUCCESSFULLY_LOADED);
        assertFalse(pack.packed(player));
        pack.status(player, second, Status.SUCCESSFULLY_LOADED);
        assertTrue(pack.packed(player));
    }

    @Test
    void failedLoadFallsBackAndCanRetry() {
        pack.toggle(player);
        UUID first = pack.requestId(player);
        pack.status(player, first, Status.FAILED_DOWNLOAD);
        assertFalse(pack.packed(player));
        assertTrue(pack.canPlay(player, "mahjong"));
        pack.toggle(player);
        assertNotEquals(first, pack.requestId(player));
    }

    @Test
    void pureModesOmitToggleAndOnlyPurePackGatesSupportedGames() {
        config.set("rendering.mode", "resource-pack");
        pack = new TabletopPack(plugin, () -> available, id -> new ItemStack(Material.PAPER));
        assertFalse(pack.hasToggle());
        assertFalse(pack.canPlay(player, "mahjong"));
        assertFalse(pack.canPlay(player, "color-eight"));
        assertTrue(pack.canPlay(player, "chess"));
        pack.request(player);
        pack.status(player, pack.requestId(player), Status.SUCCESSFULLY_LOADED);
        assertTrue(pack.canPlay(player, "mahjong"));
        config.set("rendering.mode", "vanilla");
        pack = new TabletopPack(plugin, () -> false, id -> null);
        assertFalse(pack.hasToggle());
        assertFalse(pack.packed(player));
        assertTrue(pack.canPlay(player, "mahjong"));
    }

    @Test
    void preferenceSurvivesServiceRecreationButLoadedStatusDoesNot() {
        pack.toggle(player);
        pack.status(player, pack.requestId(player), Status.SUCCESSFULLY_LOADED);
        pack = new TabletopPack(plugin, () -> available, id -> new ItemStack(Material.PAPER));
        assertTrue(pack.preferred(player));
        assertFalse(pack.packed(player));
        pack.request(player);
        assertNotNull(pack.requestId(player));
    }

    @Test
    void missingCraftEngineAndInvalidUrlDoNotSendPack() {
        available = false;
        pack.toggle(player);
        assertNull(pack.requestId(player));
        assertFalse(pack.packed(player));
        available = true;
        config.set("rendering.resource-pack.url", "file:///tabletop.zip");
        pack.request(player);
        assertNull(pack.requestId(player));
    }
}
