package dev.tabletop3d;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LanguageReloadCommandTest {
    @TempDir(factory=WorkspaceTempFactory.class) Path data;

    private Tabletop3D plugin() {
        var plugin=mock(Tabletop3D.class);
        when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("LanguageReloadTest"));
        when(plugin.onCommand(any(),any(),anyString(),any())).thenCallRealMethod();
        plugin.menus=mock(GameMenus.class);
        return plugin;
    }

    @Test void deniedPlayersCannotReadLanguageFiles() {
        var plugin=plugin();var player=mock(Player.class);
        when(plugin.allowed(player)).thenReturn(true);
        assertTrue(plugin.onCommand(player,null,"3dtabletop",new String[]{"reload-language"}));
        verify(plugin,never()).getDataFolder();verify(plugin.menus,never()).languageChanged();
        verify(plugin).tell(eq(player),any(net.kyori.adventure.text.Component.class));
    }

    @Test void consoleReloadReadsOnlyLanguageAndRejectsInvalidCandidateWithoutClosingMenus() throws Exception {
        var plugin=plugin();var console=mock(CommandSender.class);
        String config="language: custom\nmax-rooms: 23\n";Files.writeString(data.resolve("config.yml"),config);
        Language.load(data.resolve("languages"),"en_US",w->fail(w));
        Path custom=data.resolve("languages/custom.yml");Files.writeString(custom,"menu.create: 'Reloaded Create'\n");
        try {
            assertTrue(plugin.onCommand(console,null,"3dtabletop",new String[]{"reload-language"}));
            assertEquals("Reloaded Create",dev.tabletop3d.ui.MessageText.plain(Language.component("menu.create")));
            verify(plugin.menus).languageChanged();clearInvocations(plugin.menus);
            Files.writeString(custom,"chat.joined: '{wrong}'\n");
            plugin.onCommand(console,null,"3dtabletop",new String[]{"reload-language"});
            verify(plugin.menus,never()).languageChanged();
            assertEquals("Reloaded Create",dev.tabletop3d.ui.MessageText.plain(Language.component("menu.create")));
            assertEquals(config,Files.readString(data.resolve("config.yml")));verify(plugin,never()).reloadConfig();
        }finally{Language.reload(data.resolve("languages"),"en_US",w->fail(w));}
    }
}
