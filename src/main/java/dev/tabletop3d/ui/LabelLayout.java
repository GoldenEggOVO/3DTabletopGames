package dev.tabletop3d.ui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

public final class LabelLayout {
    public record Fit(Component text, float scale) {}
    private record Glyph(int code, Style style) {
        int width() { return advance(code) + (code != '\n' && advance(code) > 0
                && style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE ? 1 : 0); }
        Component component() { return Component.text(new String(Character.toChars(code))).style(style); }
    }
    private LabelLayout() {}

    private static int advance(int c) {
        if (c == '\n') return 0;
        if (c == ' ') return 4;
        if ("il.,:;!|'".indexOf(c) >= 0) return 2;
        if ("[]()It".indexOf(c) >= 0) return 4;
        if (c < 128) return 6;
        int type = Character.getType(c);
        if (type == Character.NON_SPACING_MARK || type == Character.FORMAT) return 0;
        return 9;
    }

    private static void flatten(Component text, Style parent, List<Glyph> result) {
        Style style = text.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        MessageText.plain(text.children(List.of())).codePoints().forEach(c -> result.add(new Glyph(c, style)));
        text.children().forEach(child -> flatten(child, style, result));
    }

    public static int width(String text) { return width(Component.text(text)); }

    public static int width(Component text) {
        var glyphs = new ArrayList<Glyph>();
        flatten(text, Style.empty(), glyphs);
        int line = 0, widest = 0;
        for (var glyph : glyphs) {
            if (glyph.code == '\n') { widest = Math.max(widest, line); line = 0; }
            else line += glyph.width();
        }
        return Math.max(widest, line);
    }

    public static Fit fit(Component text, float width, float height) {
        if (!(width > 0) || !(height > 0)) throw new IllegalArgumentException("Positive label bounds required");
        var glyphs = new ArrayList<Glyph>();
        flatten(text, Style.empty(), glyphs);
        int lines = (int) glyphs.stream().filter(g -> g.code == '\n').count() + 1;
        float minimum = height / (.2f * lines) * .35f;
        float scale = Math.min(height / (.2f * lines), width / (Math.max(1, width(text)) * .025f));
        if (scale >= minimum) return new Fit(text, scale);

        minimum = Math.min(minimum, width / (.025f * 10));
        int budget = (int) (width / (.025f * minimum));
        var result = Component.text();
        int index = 0;
        for (int line = 0; line < lines && index < glyphs.size(); line++) {
            if (line > 0) result.append(Component.newline());
            int end = index;
            while (end < glyphs.size() && glyphs.get(end).code != '\n') end++;
            boolean moreLines = line == lines - 1 && end < glyphs.size();
            int used = 0;
            for (int i = index; i < end; i++) used += glyphs.get(i).width();
            if (used <= budget && !moreLines) {
                for (int i = index; i < end; i++) result.append(glyphs.get(i).component());
            } else {
                used = 0;
                Style last = index < end ? glyphs.get(index).style : Style.empty();
                for (int i = index; i < end; i++) {
                    var glyph = glyphs.get(i);
                    if (used + glyph.width() + 10 > budget) break;
                    result.append(glyph.component());
                    used += glyph.width();
                    last = glyph.style;
                }
                result.append(Component.text("…").style(last));
            }
            index = end + 1;
        }
        return new Fit(result.build().compact(), minimum);
    }
}
