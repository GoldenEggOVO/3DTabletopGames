package dev.tabletop3d;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockMenuUpgradeTest {
    @TempDir Path data;
    private static final String OLD_SETUP="""
        # Game setup; rules are immutable after room creation. Existing custom pages stay intact.
        Title: '<white>@title@'
        Body:
          content:
            type: message
            text: '<white>@description@'
            width: 370
        Bottom:
          type: multi
          columns: 2
          buttons:
            mode:
              text: '<aqua>@label@'
              width: 180
            capacity:
              text: '<aqua>@label@'
              width: 180
            entry:
              text: '<white>@label@'
              width: 180
          exit:
            text: '<gray>@label@'
            width: 180
        """;
    @Test void oldStockUsesNewOrderWithoutWritingTheFile() throws Exception {
        var layouts=layouts(OLD_SETUP.replace("\n","\r\n"));
        assertEquals(List.of("start","mode"),render(layouts).buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(OLD_SETUP.replace("\n","\r\n"),Files.readString(data.resolve("menus/setup.yml")));
    }
    @Test void customizedStockKeepsItsOrderAndFile() throws Exception {
        String custom=OLD_SETUP.replace("columns: 2","columns: 3");
        var result=render(layouts(custom));
        assertEquals(List.of("mode","start"),result.buttons().stream().map(GameMenus.Button::id).toList());
        assertEquals(3,result.config().getInt("Bottom.columns"));
        assertEquals(custom,Files.readString(data.resolve("menus/setup.yml")));
    }
    private GameMenuLayouts layouts(String template) throws Exception {
        Tabletop3D plugin=mock(Tabletop3D.class);when(plugin.getDataFolder()).thenReturn(data.toFile());
        when(plugin.getResource(anyString())).thenAnswer(i->getClass().getClassLoader().getResourceAsStream(i.getArgument(0)));
        var layouts=new GameMenuLayouts(plugin);Files.writeString(data.resolve("menus/setup.yml"),template);return layouts;
    }
    private GameMenuLayouts.Rendered render(GameMenuLayouts layouts){
        return layouts.load("setup",Component.text("Setup"),Component.empty(),List.of(
            new GameMenus.Button("mode","Mode",()->{}),new GameMenus.Button("start","Start",()->{})),UUID.randomUUID());
    }
}
