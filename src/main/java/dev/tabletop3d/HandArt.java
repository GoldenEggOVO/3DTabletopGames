package dev.tabletop3d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** Original, font-independent artwork for native display pixels. */
final class HandArt {
    private static final Color CREAM = new Color(0xfff7dc), EDGE = new Color(0xd6c7a8);
    private static final Color RED = new Color(0xc72f43), BLUE = new Color(0x255eac);
    private static final Color YELLOW = new Color(0xe6ad25), PURPLE = new Color(0x8643b6);
    private static final Color GREEN = new Color(0x187447), INK = new Color(0x23354b);
    private static final String[] DIGITS = {
            "01110/11011/11011/11011/11011/11011/01110",
            "00100/01100/00100/00100/00100/00100/01110",
            "01110/10001/00001/00010/00100/01000/11111",
            "11110/00001/00001/01110/00001/00001/11110",
            "00010/00110/01010/10010/11111/00010/00010",
            "11111/10000/10000/11110/00001/00001/11110",
            "01110/10000/10000/11110/10001/10001/01110",
            "11111/00001/00010/00100/01000/01000/01000",
            "01110/10001/10001/01110/10001/10001/01110",
            "01110/10001/10001/01111/00001/00001/01110"
    };

    private HandArt() {}

    static BufferedImage draw(boolean mahjong, String face) {
        BufferedImage image = new BufferedImage(32, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(EDGE); g.fillRect(0, 0, 32, 48);
        g.setColor(CREAM); g.fillRoundRect(1, 1, 30, 46, 5, 5);
        if (face.isEmpty() || face.equals("back")) back(g);
        else if (mahjong) tile(g, face);
        else card(g, face);
        g.dispose();
        return image;
    }

    private static void back(Graphics2D g) {
        g.setColor(GREEN); g.fillRoundRect(3, 3, 26, 42, 4, 4);
        g.setColor(new Color(0x439365));
        for (int y = 6; y < 44; y += 6) {
            for (int x = 5; x < 29; x += 6) g.fillRect(x, y, 2, 2);
        }
        g.setColor(CREAM); g.drawRect(5, 5, 21, 37);
        polygon(g, new int[]{16, 24, 16, 8}, new int[]{14, 24, 34, 24});
        g.setColor(GREEN); polygon(g, new int[]{16, 21, 16, 11}, new int[]{18, 24, 30, 24});
    }

    private static void card(Graphics2D g, String face) {
        boolean wild = face.equals("wild");
        Color color = wild ? INK : switch (face.charAt(0)) {
            case 'r' -> RED; case 'b' -> BLUE; case 'y' -> YELLOW; case 'p' -> PURPLE; default -> INK;
        };
        g.setColor(color); g.fillRoundRect(3, 3, 26, 42, 4, 4);
        g.setColor(CREAM); g.setStroke(new BasicStroke(1));
        if (wild) {
            Color[] colors = {RED, BLUE, YELLOW, PURPLE};
            for (int i = 0; i < 4; i++) {
                g.setColor(colors[i]); g.fillArc(8, 14, 16, 20, i * 90, 90);
            }
            g.setColor(CREAM); g.drawOval(8, 14, 16, 20);
            g.drawLine(16, 14, 16, 34); g.drawLine(8, 24, 24, 24);
            g.fillRect(5, 6, 3, 3); g.fillRect(24, 39, 3, 3);
            return;
        }
        String value = face.substring(1);
        g.setColor(CREAM);
        if (value.length() == 1 && Character.isDigit(value.charAt(0))) {
            int number = value.charAt(0) - '0';
            g.drawOval(6, 10, 20, 28);
            bitmap(g, DIGITS[number], 10, 15, 13, 18);
            bitmap(g, DIGITS[number], 5, 5, 5, 7);
            Graphics2D rotated = (Graphics2D) g.create();
            rotated.rotate(Math.PI, 16, 24);
            bitmap(rotated, DIGITS[number], 5, 5, 5, 7);
            rotated.dispose();
        } else if (value.equals("Reverse")) {
            reverse(g, 8, 14);
            corner(g, "R");
        } else if (value.equals("Skip")) {
            g.setStroke(new BasicStroke(3)); g.drawOval(10, 17, 12, 14);
            g.drawLine(10, 31, 23, 17);
            corner(g, "S");
        } else if (value.equals("Draw2") || value.equals("Draw3")) {
            int count = value.equals("Draw2") ? 2 : 3;
            g.fillRect(10, 14, 1, 5); g.fillRect(8, 16, 5, 1);
            bitmap(g, DIGITS[count], 16, 13, 5, 7);
            for (int i = count - 1; i >= 0; i--) {
                int x = 9 + i * 3, y = 27 - i * 3;
                g.setColor(color); g.fillRect(x, y, 9, 12);
                g.setColor(CREAM); g.setStroke(new BasicStroke(1)); g.drawRect(x, y, 8, 11);
            }
            corner(g, "+" + count);
        }
    }

    private static void corner(Graphics2D g, String symbol) {
        for (int i = 0; i < 2; i++) {
            Graphics2D c = (Graphics2D) g.create();
            if (i == 1) c.rotate(Math.PI, 16, 24);
            c.setStroke(new BasicStroke(1)); c.setColor(CREAM);
            if (symbol.equals("R")) {
                c.drawLine(5, 7, 10, 7); c.drawLine(5, 10, 10, 10);
                c.drawLine(8, 5, 10, 7); c.drawLine(5, 10, 7, 12);
            } else if (symbol.equals("S")) {
                c.drawOval(5, 5, 5, 6); c.drawLine(5, 11, 10, 5);
            } else {
                c.drawLine(5, 7, 7, 7); c.drawLine(6, 6, 6, 8);
                bitmap(c, DIGITS[symbol.charAt(1) - '0'], 9, 5, 4, 6);
            }
            c.dispose();
        }
    }

    private static void reverse(Graphics2D g, int x, int y) {
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x + 1, y + 10); arrow.lineTo(x + 1, y + 5);
        arrow.quadTo(x + 1, y + 1, x + 6, y + 1); arrow.lineTo(x + 10, y + 1);
        g.draw(arrow);
        polygon(g, new int[]{x + 9, x + 15, x + 9}, new int[]{y - 3, y + 1, y + 5});
        arrow.reset(); arrow.moveTo(x + 15, y + 10); arrow.lineTo(x + 15, y + 15);
        arrow.quadTo(x + 15, y + 19, x + 10, y + 19); arrow.lineTo(x + 6, y + 19);
        g.draw(arrow);
        polygon(g, new int[]{x + 7, x + 1, x + 7}, new int[]{y + 15, y + 19, y + 23});
    }

    private static void tile(Graphics2D g, String face) {
        char suit = face.charAt(0);
        int number = face.charAt(1) - '0';
        boolean red = number == 0;
        if (red) number = 5;
        if (suit == 'm') {
            g.setColor(red ? RED : INK);
            bitmap(g, glyph("一二三四五六七八九".charAt(number - 1)), 6, 6, 20, 14);
            g.setColor(RED); bitmap(g, glyph('萬'), 5, 23, 22, 21);
        } else if (suit == 'p' || suit == 's') {
            if (number == 1) {
                if (suit == 'p') circle(g, 16, 24, 11, BLUE, RED);
                else bird(g);
            } else {
                int[][] points = positions(number);
                for (int i = 0; i < points.length; i++) {
                    int x = points[i][0], y = points[i][1];
                    if (suit == 'p') {
                        Color ink = red ? RED : number == 5 && i == 4 ? RED : (i % 2 == 0 ? BLUE : GREEN);
                        circle(g, x, y, number >= 7 ? 4 : 5, ink, red ? RED : INK);
                    } else bamboo(g, x, y, red ? RED : number == 7 && i == 0 ? RED : GREEN);
                }
            }
        } else if (suit == 'z') {
            g.setColor(number == 6 ? GREEN : number == 7 ? RED : INK);
            if (number == 5) {
                g.setColor(BLUE); g.setStroke(new BasicStroke(2)); g.drawRect(7, 9, 18, 30);
                g.setStroke(new BasicStroke(1)); g.drawRect(10, 12, 12, 24);
                for (int y = 13; y <= 35; y += 5) {g.drawLine(7, y, 10, y + 2); g.drawLine(22, y, 25, y + 2);}
            } else bitmap(g, glyph("東南西北白發中".charAt(number - 1)), 5, 10, 22, 28);
        } else if (suit == 'f') {
            g.setColor(number <= 4 ? BLUE : RED);
            bitmap(g, glyph("梅蘭竹菊春夏秋冬".charAt(number - 1)), 5, 5, 22, 23);
            g.setColor(GREEN); g.setStroke(new BasicStroke(1));
            g.drawLine(14, 42, 18, 29); g.drawLine(16, 37, 10, 33); g.drawLine(16, 39, 22, 35);
            g.fillOval(8, 31, 5, 3); g.fillOval(20, 33, 5, 3);
            g.setColor(number <= 4 ? PURPLE : YELLOW);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                g.fillOval(16 + (int) (3 * Math.cos(a)), 29 + (int) (3 * Math.sin(a)), 3, 3);
            }
            g.setColor(RED); g.fillRect(17, 30, 2, 2);
        }
    }

    private static int[][] positions(int number) {
        return switch (number) {
            case 2 -> new int[][]{{11, 14}, {21, 34}};
            case 3 -> new int[][]{{9, 12}, {16, 24}, {23, 36}};
            case 4 -> new int[][]{{9, 13}, {23, 13}, {9, 35}, {23, 35}};
            case 5 -> new int[][]{{9, 12}, {23, 12}, {9, 36}, {23, 36}, {16, 24}};
            case 6 -> new int[][]{{9, 11}, {23, 11}, {9, 24}, {23, 24}, {9, 37}, {23, 37}};
            case 7 -> new int[][]{{8, 9}, {16, 15}, {24, 21}, {9, 29}, {23, 29}, {9, 39}, {23, 39}};
            case 8 -> new int[][]{{9, 9}, {23, 9}, {9, 19}, {23, 19}, {9, 29}, {23, 29}, {9, 39}, {23, 39}};
            default -> new int[][]{{8, 11}, {16, 11}, {24, 11}, {8, 24}, {16, 24}, {24, 24}, {8, 37}, {16, 37}, {24, 37}};
        };
    }

    private static void circle(Graphics2D g, int x, int y, int radius, Color outer, Color center) {
        g.setColor(outer); g.fillOval(x - radius, y - radius, radius * 2 + 1, radius * 2 + 1);
        g.setColor(CREAM); g.fillOval(x - radius + 2, y - radius + 2, radius * 2 - 3, radius * 2 - 3);
        g.setColor(center);
        int inner = Math.max(1, radius - 4);
        g.fillOval(x - inner, y - inner, inner * 2 + 1, inner * 2 + 1);
        if (radius > 7) {
            g.setColor(CREAM); g.fillOval(x - 3, y - 3, 7, 7);
            g.setColor(GREEN); g.fillRect(x - 1, y - 1, 3, 3);
        }
    }

    private static void bamboo(Graphics2D g, int x, int y, Color color) {
        g.setColor(color); g.fillRect(x - 1, y - 4, 3, 9);
        g.fillRect(x - 3, y - 4, 7, 2); g.fillRect(x - 3, y + 3, 7, 2);
        g.setColor(CREAM); g.fillRect(x, y - 2, 1, 4);
        g.setColor(color); g.fillRect(x - 2, y, 5, 1);
    }

    private static void bird(Graphics2D g) {
        g.setColor(GREEN); g.fillOval(11, 19, 13, 15); g.fillOval(8, 12, 9, 11);
        polygon(g, new int[]{20, 25, 24, 17}, new int[]{27, 31, 40, 32});
        g.setColor(BLUE); polygon(g, new int[]{12, 22, 18, 12}, new int[]{22, 25, 31, 28});
        g.setColor(CREAM); g.drawLine(13, 24, 19, 27); g.drawLine(13, 27, 17, 29);
        g.fillRect(11, 15, 3, 3); g.setColor(INK); g.fillRect(11, 15, 1, 2);
        g.setColor(RED); polygon(g, new int[]{9, 4, 9}, new int[]{17, 19, 21});
        g.fillRect(11, 10, 4, 3); g.drawLine(14, 32, 12, 38); g.drawLine(17, 32, 16, 38);
        g.setColor(GREEN); g.drawLine(6, 40, 21, 40); g.drawLine(8, 40, 5, 35);
    }

    private static void polygon(Graphics2D g, int[] x, int[] y) {
        g.fillPolygon(x, y, x.length);
    }

    private static void bitmap(Graphics2D g, String bitmap, int x, int y, int width, int height) {
        String[] rows = bitmap.split("/");
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) != '1') continue;
                int left = x + column * width / rows[row].length(), top = y + row * height / rows.length;
                int right = x + (column + 1) * width / rows[row].length(), bottom = y + (row + 1) * height / rows.length;
                g.fillRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
            }
        }
    }

    private static String glyph(char character) {
        return switch (character) {
            case '一' -> "00000000000/00000000000/00000000000/00000000000/00000000000/11111111111/00000000000/00000000000/00000000000/00000000000/00000000000";
            case '二' -> "00000000000/00000000000/01111111110/00000000000/00000000000/00000000000/00000000000/00000000000/11111111111/00000000000/00000000000";
            case '三' -> "00000000000/11111111111/00000000000/00000000000/01111111110/00000000000/00000000000/00000000000/11111111111/00000000000/00000000000";
            case '四' -> "00000000000/11111111111/10010001001/10010001001/10010001001/10100001001/11000001101/10000000001/10000000001/11111111111/00000000000";
            case '五' -> "01111111110/00001000000/00001000000/00001000000/01111111100/00010000100/00010000100/00010000100/00100000100/11111111111/00000000000";
            case '六' -> "00000100000/00000010000/00000000000/11111111111/00000000000/00010001000/00010001000/00100000100/01000000010/10000000001/00000000000";
            case '七' -> "00001000000/00001000000/00001000011/00001111100/11111000000/00001000000/00001000000/00001000001/00001000001/00000111110/00000000000";
            case '八' -> "00000000000/00010010000/00010010000/00010010000/00010001000/00100001000/00100000100/01000000100/01000000010/10000000001/00000000000";
            case '九' -> "00010000000/00010000000/11111111000/00010001000/00010001000/00010001000/00100001000/00100001001/01000001001/10000000110/00000000000";
            case '萬' -> "00100000100/11111111111/00100000100/01111111110/01000100010/01111111110/01000100010/01111111110/00000100000/11111111111/10000100001/10111111101/10000001011";
            case '東' -> "00000100000/11111111111/00000100000/01111111110/01000100010/01111111110/01000100010/01111111110/00011100000/00110110000/01000101000/10000100100/00000100000";
            case '南' -> "00000100000/11111111111/00000100000/11111111111/10010001001/10001010001/10111111101/10000100001/10111111101/10000100001/10000100001/10000100001/10000000011";
            case '西' -> "11111111111/00010001000/00010001000/11111111111/10010001001/10010001001/10100001001/11000001101/10000000001/10000000001/11111111111";
            case '北' -> "00010010000/00010010001/00010010010/11110010100/00010011000/00010010000/00010010000/00010010000/00110010001/11010010001/00010001110";
            case '發' -> "01111011100/00010100010/10101010100/01000101010/10000000001/01110011110/00010010010/01110001010/01000001010/01111000100/00001001010/00001010001/00110000000";
            case '中' -> "00000100000/00000100000/11111111111/10000100001/10000100001/10000100001/10000100001/11111111111/00000100000/00000100000/00000100000/00000100000/00000100000";
            case '梅' -> "00100010000/00100010000/11110111111/00100100000/01101111110/01101001010/10101011010/10111111111/00101001010/00101011010/00101111111/00100000010/00100000100";
            case '蘭' -> "00100000100/11111111111/00100000100/11111011111/10101010101/11111011111/10000000001/10111111101/10010101001/10111111101/10001110001/10010101001/10100100111";
            case '竹' -> "00100001000/00100001000/01111011111/01010101010/10010100010/00010000010/00010000010/00010000010/00010000010/00010000010/00010000010/00010001010/00010000100";
            case '菊' -> "00100000100/11111111111/00100000100/00100000000/01111111111/10001000001/10101010101/10011100001/11111111001/10011100001/10101010001/11001001001/00000000110";
            case '春' -> "00000100000/01111111110/00000100000/01111111110/00001000000/11111111111/00100010000/01000001000/11111111110/01000000010/01111111110/01000000010/01111111110";
            case '夏' -> "11111111111/00000100000/01111111110/01000000010/01111111110/01000000010/01111111110/00100000000/01111111100/10010001000/00001110000/00110001100/11000000011";
            case '秋' -> "00011001000/11100001000/00100001000/00100101010/11110101010/00100101100/01110001000/01110001000/10101010100/10100010100/00100100010/00101000001/00100000000";
            case '冬' -> "00010000000/00011111100/00100001000/01010010000/10001100000/00010110000/01100001100/10000000011/00011000000/00000110000/00000000000/00011000000/00000110000";
            default -> throw new IllegalArgumentException("Unknown tile glyph: " + character);
        };
    }
}
