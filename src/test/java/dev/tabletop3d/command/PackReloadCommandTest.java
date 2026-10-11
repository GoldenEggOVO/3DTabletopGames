package dev.tabletop3d.command;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.resource.TabletopPack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackReloadCommandTest {
    private Tabletop3D plugin() throws Exception {
        var plugin = mock(Tabletop3D.class);
        var rooms = Tabletop3D.class.getField("rooms");
        rooms.setAccessible(true);
        rooms.set(plugin, new java.util.LinkedHashMap<>());
        return plugin;
    }

    @Test
    void consoleCanDiscoverResourceReload() throws Exception {
        var plugin = plugin();
        var command = new TabletopCommand(plugin);
        assertEquals(java.util.List.of("reload-pack"), command.onTabComplete(
                mock(CommandSender.class), null, "3dtabletop", new String[]{"reload-p"}));
    }

    @Test
    void resourceReloadIsNotAdvertisedToOrdinaryPlayers() throws Exception {
        var plugin = plugin();
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        assertTrue(new TabletopCommand(plugin).onTabComplete(
                player, null, "3dtabletop", new String[]{"reload-p"}).isEmpty());
    }

    @Test
    void onlyAdministratorOrConsoleCanStartResourceReload() throws Exception {
        var plugin = plugin();
        plugin.pack = mock(TabletopPack.class);
        var command = new TabletopCommand(plugin);
        var player = mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        command.onCommand(player, null, "3dtabletop", new String[]{"reload-pack"});
        verify(plugin.pack, never()).reload(any());
        when(player.hasPermission("3dtabletop.admin")).thenReturn(true);
        command.onCommand(player, null, "3dtabletop", new String[]{"RELOAD-PACK"});
        verify(plugin.pack).reload(player);
        var console = mock(CommandSender.class);
        command.onCommand(console, null, "3dtabletop", new String[]{"reload-pack"});
        verify(plugin.pack).reload(console);
    }
}
