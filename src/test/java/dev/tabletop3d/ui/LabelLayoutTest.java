package dev.tabletop3d.ui;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LabelLayoutTest {
    @Test void longNamesFitWithoutDroppingStatusAndInstructions() {
        Component text=Component.text("Table\n"+"PlayerName".repeat(40)+"\nRoster\nOpen seats\nAim\nSneak");
        var fit=LabelLayout.fit(text,2.8f,.48f);
        String plain=MessageText.plain(fit.text());
        assertEquals(6,plain.lines().count());
        assertTrue(plain.endsWith("Sneak"));
        assertTrue(fit.scale()>0);
        assertTrue(LabelLayout.width(fit.text())*.025*fit.scale()<=2.8001);
    }
}
