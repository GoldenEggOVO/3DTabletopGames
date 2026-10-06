package dev.tabletop3d.render;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.resource.TabletopPack;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableAudienceTest {
    private Tabletop3D plugin;
    private World world;
    private Player nativePlayer, packedPlayer;
    private Entity nativePart, packedPart, commonPart;
    private TableAudience audience;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        plugin = mock(Tabletop3D.class);
        plugin.pack = mock(TabletopPack.class);
        world = mock(World.class);
        nativePlayer = player(false);
        packedPlayer = player(true);
        when(world.getPlayers()).thenReturn(List.of(nativePlayer, packedPlayer));
        audience = new TableAudience(plugin, new Location(world, 0, 80, 0));
        nativePart = mock(Entity.class);
        packedPart = mock(Entity.class);
        commonPart = mock(Entity.class);
        audience.add(nativePart, false);
        audience.add(packedPart, true);
        audience.common(commonPart);
        clearInvocations(nativePlayer, packedPlayer);
    }

    private Player player(boolean packed) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 0, 81, 0));
        when(plugin.allowed(player)).thenReturn(true);
        when(plugin.pack.packed(player)).thenReturn(packed);
        return player;
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void changingBackendUpdatesOnlyThatPlayersChangedLayers() {
        when(plugin.pack.packed(nativePlayer)).thenReturn(true);
        audience.refresh();
        assertFalse(audience.needed(false));
        assertTrue(audience.needed(true));
        verify(nativePlayer).hideEntity(plugin, nativePart);
        verify(nativePlayer).showEntity(plugin, packedPart);
        verify(nativePlayer, never()).showEntity(plugin, commonPart);
        verify(nativePlayer, never()).hideEntity(plugin, commonPart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, commonPart);
        verify(packedPlayer, never()).hideEntity(plugin, nativePart);
    }

    @Test
    void leavingRangeHidesVisibleLayersWithoutUpdatingOtherPlayers() {
        when(world.getPlayers()).thenReturn(List.of(packedPlayer));
        audience.refresh();
        assertEquals(java.util.Set.of(packedPlayer), audience.all());
        verify(nativePlayer).hideEntity(plugin, nativePart);
        verify(nativePlayer).hideEntity(plugin, commonPart);
        verify(nativePlayer, never()).hideEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(packedPlayer, never()).showEntity(plugin, commonPart);
    }

    @Test
    void unchangedAudienceKeepsGenerationAndSendsNoEntityUpdates() {
        long generation = audience.generation;
        audience.refresh();
        assertEquals(generation, audience.generation);
        verify(nativePlayer, never()).showEntity(plugin, nativePart);
        verify(packedPlayer, never()).showEntity(plugin, packedPart);
        verify(nativePlayer, never()).hideEntity(plugin, nativePart);
    }
}
