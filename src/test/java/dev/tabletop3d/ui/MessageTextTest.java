package dev.tabletop3d.ui;

import net.kyori.adventure.text.format.NamedTextColor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTextTest {
    @Test void stylesAndLegacyColorsRenderThroughOneInterface() {
        assertEquals("Hello world", MessageText.plain(MessageText.render("<gold>Hello</gold> &Aworld")));
        assertEquals(NamedTextColor.GOLD, MessageText.render("<gold>Hello").color());
    }

    @Test void dynamicValuesAreLiteralAndNeverBecomeCommandsOrColors() {
        String name = "<red>Player&c<click:run_command:'/op x'>";
        var component = MessageText.render("<gold>{player}</gold>", "player", name);
        assertEquals(name, MessageText.plain(component));
        assertNull(component.clickEvent());
        assertEquals("<click:run_command:'/op x'>x</click>",
            MessageText.plain(MessageText.render("<click:run_command:'/op x'>x</click>")));
    }

    @Test void legacyColorResetsDecorationsAndHexIsSupported() {
        assertEquals("BoldGreenHex", MessageText.plain(MessageText.render("&lBold§aGreen&#123abcHex")));
        var parsed = MessageText.render("&lBold§aGreen");
        var green = parsed.children().getLast();
        assertFalse(green.hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }
}
