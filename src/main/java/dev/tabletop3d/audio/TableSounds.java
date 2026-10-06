package dev.tabletop3d.audio;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.resource.TabletopPack;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.doudizhu.DoudizhuCombination;

import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Short native cues emitted by committed actions, never by rendering or replay. */
public final class TableSounds {
    public record Cue(Sound sound, float volume, float pitch, String resource, int delayTicks) {
        public Cue(Sound sound, float volume, float pitch) { this(sound, volume, pitch, null, 0); }
        Cue(Sound sound, float volume, float pitch, String resource) { this(sound, volume, pitch, resource, 0); }
    }

    public static final Cue WOOD = new Cue(Sound.BLOCK_WOOD_HIT, .28f, 1.15f, "board.wood");
    static final Cue XIANGQI = new Cue(Sound.BLOCK_BAMBOO_WOOD_HIT, .28f, .9f, "board.wood");
    static final Cue STONE = new Cue(Sound.BLOCK_STONE_HIT, .25f, 1.45f, "board.stone");
    static final Cue HOP = new Cue(Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, .2f, 1.4f, "board.wood");
    static final Cue DROP = new Cue(Sound.BLOCK_DECORATED_POT_PLACE, .28f, 1.5f, "board.drop");
    static final Cue FLIP = new Cue(Sound.BLOCK_BONE_BLOCK_PLACE, .25f, 1.5f, "board.flip");
    static final Cue PAWN = new Cue(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, .25f, 1.3f, "board.wood");
    static final Cue ROLL = new Cue(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, .3f, 1.5f, "dice.roll");
    static final Cue SINGLE_ROLL = new Cue(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, .3f, 1.5f, "dice.single");
    static final Cue HOLD = new Cue(Sound.BLOCK_NOTE_BLOCK_HAT, .15f, 1.8f, "dice.hold");
    static final Cue CAPTURE = new Cue(Sound.BLOCK_WOOD_BREAK, .28f, 1.5f, "board.capture");
    static final Cue HOME = new Cue(Sound.BLOCK_NOTE_BLOCK_PLING, .25f, 1.8f, "table.home");
    static final Cue SELECT = new Cue(Sound.BLOCK_NOTE_BLOCK_HAT, .15f, 1.8f, "table.select");
    static final Cue PASS = new Cue(Sound.BLOCK_NOTE_BLOCK_BASS, .2f, 1.2f, "table.pass");
    static final Cue CONFIRM = new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME, .2f, 1.2f, "table.confirm");
    public static final Cue START = new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME, .3f, 1.5f, "table.start");
    public static final Cue WIN = new Cue(Sound.ENTITY_PLAYER_LEVELUP, .3f, 1.3f, "table.win");
    public static final Cue DRAW = new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME, .25f, .8f, "table.draw");
    static final Cue CARD = new Cue(Sound.ITEM_BOOK_PAGE_TURN, .25f, 1.4f, "cards.play");
    static final Cue CARD_DRAW = new Cue(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, .3f, 1.5f, "cards.draw");
    static final Cue CHIPS = new Cue(Sound.BLOCK_CHAIN_PLACE, .18f, 1.2f, "chips.bet");
    static final Cue TILE = new Cue(Sound.BLOCK_BONE_BLOCK_HIT, .24f, 1.5f);
    static final Cue TILE_DRAW = new Cue(Sound.BLOCK_BAMBOO_WOOD_HIT, .16f, 1.7f);
    static final Cue TILE_DISCARD = new Cue(Sound.BLOCK_BONE_BLOCK_PLACE, .28f, 1.35f);
    static final Cue TILE_CHI = new Cue(Sound.BLOCK_BONE_BLOCK_HIT, .26f, 1.8f);
    static final Cue TILE_PON = new Cue(Sound.BLOCK_BONE_BLOCK_PLACE, .32f, 1.05f);
    static final Cue TILE_KAN = new Cue(Sound.BLOCK_BONE_BLOCK_PLACE, .36f, .75f);
    static final Cue RIICHI = new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME, .3f, 1.65f);
    static final Cue TILE_DORA = new Cue(Sound.BLOCK_NOTE_BLOCK_CHIME, .24f, 1.4f);
    static final Cue COUNTDOWN = new Cue(Sound.BLOCK_NOTE_BLOCK_HAT, .15f, 1.2f);
    static final Cue MAHJONG_WIN = new Cue(Sound.BLOCK_NOTE_BLOCK_BELL, .36f, 1.5f);

    record MahjongState(Map<String, String> info, int exposedTiles) {}

    public static MahjongState mahjongState(HandGame game) {
        int exposed = 0;
        for (int seat = 0; seat < game.playerCount(); seat++)
            for (var tile : game.exposed(seat)) if (!tile.face().startsWith("f")) exposed++;
        return new MahjongState(game.publicInfo(), exposed);
    }

    public static List<Cue> mahjong(String action, MahjongState before, MahjongState after) {
        var cues = new ArrayList<Cue>();
        var previous = before.info();
        var current = after.info();
        if (action.equals("next-hand")) return List.of(START);
        boolean win =
                !current.get("lastWin").isEmpty()
                        && !current.get("lastWin").equals(previous.get("lastWin"));
        boolean draw =
                current.get("phase").equals("TURN")
                        && Integer.parseInt(current.get("wall"))
                                < Integer.parseInt(previous.get("wall"));
        // A kong can finish on another player's pass after a rob-kong response.
        boolean kong =
                after.exposedTiles() > before.exposedTiles()
                        && (action.startsWith("kan-") || previous.get("phase").equals("RON"));
        if (action.startsWith("discard:") || action.startsWith("riichi:")) cues.add(TILE_DISCARD);
        if (action.startsWith("riichi:")) cues.add(RIICHI);
        if (kong) cues.add(TILE_KAN);
        else if (action.startsWith("kan-")) cues.add(SELECT);
        else if (action.startsWith("chi:")) cues.add(TILE_CHI);
        else if (action.startsWith("pon:")) cues.add(TILE_PON);
        else if (action.equals("pass") && !win) cues.add(PASS);
        else if (action.equals("ron") && !win) cues.add(CONFIRM);
        else if (action.startsWith("exchange:") || action.startsWith("missing:")) cues.add(TILE);
        if (win) cues.add(MAHJONG_WIN);
        if (!win
                && (current.get("phase").equals("ROUND_END")
                        || current.get("phase").equals("FINISHED"))
                && !current.get("phase").equals(previous.get("phase"))) cues.add(DRAW);
        if (!current.getOrDefault("doraIds", "").equals(previous.getOrDefault("doraIds", ""))
                && !previous.getOrDefault("doraIds", "").isEmpty()) cues.add(TILE_DORA);
        if (draw) cues.add(TILE_DRAW);
        return List.copyOf(cues);
    }

    public static List<Cue> cards(String kind, String action, Map<String, String> before,
            Map<String, String> after, boolean canBeat) {
        return switch (kind) {
            case "doudizhu" -> doudizhu(action, before, after, canBeat);
            case "liars-bar" -> liarsBar(action, before, after);
            default -> {
                if (action.equals("fold")) yield List.of(PASS);
                if (action.equals("check")) yield List.of(SELECT);
                yield List.of(CARD, CHIPS);
            }
        };
    }

    private static List<Cue> doudizhu(String action, Map<String, String> before,
            Map<String, String> after, boolean canBeat) {
        if (action.equals("pass"))
            return List.of(new Cue(PASS.sound(), .28f, canBeat ? 1.2f : .8f));
        if (action.startsWith("bid:")) {
            if (action.equals("bid:0")) return List.of(PASS);
            return List.of(new Cue(CONFIRM.sound(), .3f, before.get("bid").equals("0") ? 1.3f : 1.5f));
        }
        var type = DoudizhuCombination.Type.valueOf(after.get("combination"));
        Sound sound = switch (type) {
            case BOMB -> Sound.ENTITY_GENERIC_EXPLODE;
            case ROCKET -> Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST;
            case STRAIGHT, PAIR_STRAIGHT, TRIPLE_STRAIGHT -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE;
            case AIRPLANE_SINGLE, AIRPLANE_PAIR -> Sound.ENTITY_FIREWORK_ROCKET_LAUNCH;
            default -> Sound.BLOCK_NOTE_BLOCK_PLING;
        };
        return List.of(new Cue(sound, .3f, .65f + type.ordinal() * .075f),
                new Cue(CARD.sound(), .16f, 1.4f, CARD.resource(), 2));
    }

    private static List<Cue> liarsBar(String action, Map<String, String> before,
            Map<String, String> after) {
        if (action.equals("challenge")) {
            boolean eliminated = before.entrySet().stream().anyMatch(entry ->
                    entry.getKey().startsWith("alive.") && entry.getValue().equals("true")
                            && after.get(entry.getKey()).equals("false"));
            return List.of(new Cue(Sound.BLOCK_NOTE_BLOCK_BELL, .32f, .65f),
                    new Cue(eliminated ? Sound.ENTITY_GENERIC_EXPLODE : Sound.BLOCK_LEVER_CLICK,
                            .35f, eliminated ? 1.2f : .75f, null, 30));
        }
        return List.of(CARD, new Cue(Sound.BLOCK_NOTE_BLOCK_BASS, .16f, .75f));
    }

    public static Cue move(String kind, int seat, String action, List<Cell> before, List<Cell> after) {
        if (kind.equals("color-eight")) return switch (action.split(":", 2)[0]) {
            case "draw" -> CARD_DRAW;
            case "pass" -> PASS;
            case "choose" -> CONFIRM;
            default -> CARD;
        };
        if (kind.equals("yacht") && action.startsWith("score:")) return switch (action.substring(6)) {
            case "yacht" -> HOME;
            case "small-straight", "large-straight" -> HOP;
            default -> CONFIRM;
        };
        if (Set.of("doudizhu", "liars-bar", "texas-holdem").contains(kind)) {
            if (action.startsWith("play:")) return CARD;
            if (action.equals("pass") || action.equals("fold")) return PASS;
            return CONFIRM;
        }
        if (kind.equals("mahjong")) {
            if (action.startsWith("discard:")) return TILE_DISCARD;
            if (action.equals("pass")) return PASS;
            return TILE;
        }
        if (action.equals("roll")) return kind.equals("ludo") ? SINGLE_ROLL : ROLL;
        if (action.equals("pass")) return PASS;
        if (action.startsWith("hold:")) return HOLD;
        if (action.startsWith("dead:")) return SELECT;
        if (action.equals("accept") || action.equals("resume") || action.startsWith("score:"))
            return CONFIRM;
        if (kind.equals("reversi")) return FLIP;
        if (opponents(kind, seat, before) > opponents(kind, seat, after)) return CAPTURE;
        if (kind.equals("ludo") && action.matches("move:\\d+:go\\d+"))
            return HOME;
        return switch (kind) {
            case "xiangqi" -> XIANGQI;
            case "gomoku", "go", "go9", "go13" -> STONE;
            case "checkers" -> HOP;
            case "connectfour" -> DROP;
            case "ludo" -> PAWN;
            default -> WOOD;
        };
    }

    private static int opponents(String kind, int seat, List<Cell> cells) {
        boolean race = kind.equals("ludo");
        int count = 0;
        for (Cell cell : cells)
            if (cell.owner() >= 0 && cell.owner() != seat) {
                if (!race) count++;
                else if (!cell.id().startsWith("ba"))
                    count += (int) cell.piece().chars().filter(c -> c >= '1' && c <= '4').count();
            }
        return count;
    }

    private static float volume(Tabletop3D plugin, Cue cue) {
        var config = plugin.getConfig();
        if (!config.getBoolean("sounds.enabled", true)) return 0;
        double scale = config.getDouble("sounds.volume", 1);
        return Double.isFinite(scale) ? cue.volume() * (float) Math.clamp(scale, 0, 1) : 0;
    }

    private static String packedSound(Cue cue) {
        if (cue.equals(SELECT)) return "select";
        if (cue.equals(TILE_DISCARD)) return "discard";
        if (cue.equals(TILE_DRAW)) return "draw";
        if (cue.equals(TILE_CHI) || cue.equals(TILE_PON)) return "meld";
        if (cue.equals(TILE_KAN)) return "kan";
        if (cue.equals(RIICHI)) return "riichi";
        if (cue.equals(TILE_DORA)) return "dora";
        if (cue.equals(MAHJONG_WIN)) return "win";
        if (cue.equals(COUNTDOWN)) return "countdown";
        return null;
    }

    public static void play(Tabletop3D plugin, Location at, Cue cue) {
        play(plugin, at, cue, "");
    }

    public static void play(Tabletop3D plugin, Location at, Cue cue, String kind) {
        if (cue.delayTicks() > 0) {
            var immediate = new Cue(cue.sound(), cue.volume(), cue.pitch(), cue.resource(), 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> play(plugin, at, immediate, kind), cue.delayTicks());
            return;
        }
        float volume = volume(plugin, cue);
        if (volume <= 0) return;
        if ((!kind.equals("mahjong") && cue.resource() == null)
                || plugin.pack == null
                || plugin.pack.mode == TabletopPack.Mode.VANILLA) {
            at.getWorld().playSound(at, cue.sound(), SoundCategory.BLOCKS, volume, cue.pitch());
            return;
        }
        for (Player player : at.getWorld().getPlayers())
            if (plugin.allowed(player) && player.getLocation().distanceSquared(at) <= 24 * 24)
                personal(plugin, player, at, cue, kind);
    }

    private static void personal(
            Tabletop3D plugin, Player player, Location at, Cue cue, String kind) {
        float volume = volume(plugin, cue);
        if (volume <= 0) return;
        String custom = null;
        if (plugin.pack != null && plugin.pack.packed(player)) {
            custom = cue.resource();
            if (kind.equals("mahjong")) {
                String mahjongSound = packedSound(cue);
                if (mahjongSound != null) custom = "mahjong." + mahjongSound;
            }
        }
        if (custom != null)
            player.playSound(at, "tabletop3d:" + custom, SoundCategory.BLOCKS, volume, 1f);
        else player.playSound(at, cue.sound(), SoundCategory.BLOCKS, volume, cue.pitch());
    }

    public static void select(Tabletop3D plugin, Player player) {
        Room room = plugin.room(player);
        personal(plugin, player, player.getLocation(), SELECT, room == null ? "" : room.kind);
    }

    public static void countdown(Tabletop3D plugin, Room room, Location at, long now) {
        if (!room.kind.equals("mahjong")
                || room.restoring
                || room.phase != Room.Phase.PLAYING
                || room.busy) return;
        int turn = room.turn();
        if (turn < 0 || turn >= room.seats.size()) return;
        Room.Seat seat = room.seats.get(turn);
        if (seat.bot()) return;
        long seconds =
                (Math.max(0, plugin.turnWaitMillis(room) - Math.max(0, now - room.changed)) + 999)
                        / 1000;
        if (seconds < 1 || seconds > 5) return;
        if (room.countdownChanged == room.changed && room.countdownSecond == seconds) return;
        room.countdownChanged = room.changed;
        room.countdownSecond = (int) seconds;
        Player player = Bukkit.getPlayer(seat.id());
        if (player != null
                && plugin.allowed(player)
                && player.getWorld().equals(at.getWorld())
                && player.getLocation().distanceSquared(at) <= 64)
            personal(plugin, player, at, COUNTDOWN, room.kind);
    }

}
