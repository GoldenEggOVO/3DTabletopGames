package dev.tabletop3d.text;

import dev.tabletop3d.rules.RuleMessage;

import net.kyori.adventure.text.Component;

import java.util.*;

/** Converts explicitly identified board symbols and status choices before rendering. */
public final class BoardMessages {
    public static Component render(RuleMessage message) {
        List<Object> parameters = new ArrayList<>();
        message.parameters()
                .forEach(
                        (key, value) -> {
                            parameters.add(key);
                            if (key.equals("piece"))
                                value = Component.text(Language.glyph(String.valueOf(value)));
                            if (value instanceof Boolean confirmed)
                                value =
                                        Language.component(
                                                confirmed
                                                        ? "board.confirmed"
                                                        : "board.unconfirmed");
                            parameters.add(value);
                        });
        return Language.component(message.key(), parameters.toArray());
    }

    private BoardMessages() {}
}
