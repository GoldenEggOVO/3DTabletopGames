package dev.tabletop3d.rules.mahjong;

import dev.tabletop3d.rules.Cell;
import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.RuleViolation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure physical-tile game. Responses are serialized in priority order through currentPlayer. */
public final class MahjongGame implements HandGame {
    private enum Phase {
        EXCHANGE,
        MISSING,
        TURN,
        RON,
        CALL,
        CHI,
        ROUND_END,
        FINISHED
    }

    private enum Offer {
        DISCARD,
        ADDED_KONG,
        CLOSED_KONG
    }

    private final String profile;
    private final Map<String, String> options;
    private final GuangdongRules rules;
    private final TaiwanRules taiwan;
    private final SichuanRules sichuan;
    private final boolean riichiProfile;
    private final RiichiScore.Options riichiOptions;
    private Wall wall;
    private final long seed;
    private final int roundLimit;
    private final List<List<Tiles.Tile>> hands = new ArrayList<>(),
            rivers = new ArrayList<>(),
            flowers = new ArrayList<>();
    private final List<List<Meld>> melds = new ArrayList<>();
    private final List<List<Tiles.Tile>> exchange = new ArrayList<>();
    private final List<Integer> responders = new ArrayList<>(), winners = new ArrayList<>();
    private final List<Integer> finishedSeats = new ArrayList<>();
    private final List<KongPayment> kongPayments = new ArrayList<>();
    private List<KongPayment> lastKong = List.of();

    private static final class KongPayment {
        final int from, to, amount;
        boolean refundable = true;

        KongPayment(int from, int to, int amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }
    }

    private final int[] scores = new int[4],
            handStartScores = new int[4],
            missingSuit = new int[4],
            passedRonFan = new int[4];
    private final boolean[] riichi = new boolean[4],
            doubleRiichi = new boolean[4],
            ippatsu = new boolean[4],
            temporaryFuriten = new boolean[4],
            riichiFuriten = new boolean[4];
    private final int[] draws = new int[4], discarded = new int[4];
    private final String[] riichiTiles = new String[4];
    private final List<java.util.Set<Integer>> ownDiscards = new ArrayList<>();
    private java.util.Set<Integer> forbiddenDiscards = java.util.Set.of();
    private int pendingRiichi = -1, riichiSticks, honba;
    private Phase phase = Phase.TURN;
    private Offer offer;
    private Tiles.Tile offered;
    private int current,
            source,
            responseIndex,
            dealer,
            dealerStreak,
            dealerAdvances,
            roundSerial,
            discardCount;
    private Tiles.Tile drawnTile;
    private Tiles.Tile winningTileInstance;
    private boolean anyCalls, kongDraw, discardAfterKong;
    private int preparationSeat, exchangeOffset;
    private String settlement = "";
    private String lastWin = "", winningPatterns = "", winningTile = "";
    private int winningHan, winningFu;
    private String result = "ongoing", lastAction = "Dealer drew a tile";
    private String lastDiscardTile = "";
    private int lastDiscardBy = -1;

    public MahjongGame(int players, long seed, Map<String, String> options) {
        if (players != 4) throw new IllegalArgumentException("Mahjong requires four players");
        this.options = Map.copyOf(options);
        this.seed = seed;
        profile = options.getOrDefault("profile", "riichi");
        if (!List.of("guangdong", "taiwan", "sichuan", "riichi").contains(profile))
            throw new RuleViolation(
                    "error.mahjong.profile",
                    "Unknown mahjong profile: " + profile,
                    "profile",
                    profile);
        rules = profile.equals("guangdong") ? new GuangdongRules(options) : null;
        taiwan = profile.equals("taiwan") ? new TaiwanRules(options) : null;
        sichuan = profile.equals("sichuan") ? new SichuanRules(options) : null;
        riichiProfile = profile.equals("riichi");
        riichiOptions =
                new RiichiScore.Options(
                        flag("open-tanyao", true),
                        flag("kiriage-mangan", false),
                        flag("counted-yakuman", true),
                        flag("double-yakuman", false));
        roundLimit =
                Integer.parseInt(
                        options.getOrDefault("rounds", profile.equals("guangdong") ? "1" : "4"));
        if (roundLimit != 1 && roundLimit != 4 && roundLimit != 8)
            throw new IllegalArgumentException("Mahjong rounds must be 1, 4 or 8");
        int starting = Integer.parseInt(options.getOrDefault("starting-points", "25000"));
        if (starting < 1 || starting > 1_000_000)
            throw new IllegalArgumentException("Invalid mahjong starting points");
        for (int seat = 0; seat < 4; seat++) {
            hands.add(new ArrayList<>());
            rivers.add(new ArrayList<>());
            flowers.add(new ArrayList<>());
            melds.add(new ArrayList<>());
            exchange.add(new ArrayList<>());
            ownDiscards.add(new java.util.HashSet<>());
            scores[seat] = starting;
        }
        startHand();
    }

    private int tileCount() {
        return sichuan != null ? 108 : taiwan != null ? 144 : 136;
    }

    private void startHand() {
        wall =
                new Wall(
                        Tiles.set(
                                tileCount(),
                                riichiProfile
                                        ? Integer.parseInt(option("red-five-count", "3"))
                                        : 0),
                        seed + roundSerial * 0x9E3779B97F4A7C15L,
                        riichiProfile);
        lastDiscardTile = "";
        lastDiscardBy = -1;
        java.util.Arrays.fill(riichiTiles, "");
        for (int seat = 0; seat < 4; seat++) {
            hands.get(seat).clear();
            rivers.get(seat).clear();
            flowers.get(seat).clear();
            melds.get(seat).clear();
            exchange.get(seat).clear();
            handStartScores[seat] = scores[seat];
            missingSuit[seat] = -1;
            passedRonFan[seat] = -1;
        }
        winners.clear();
        responders.clear();
        offered = null;
        anyCalls = false;
        kongDraw = false;
        discardCount = 0;
        phase = Phase.TURN;
        current = dealer;
        finishedSeats.clear();
        kongPayments.clear();
        lastKong = List.of();
        settlement = "";
        lastWin = "";
        winningPatterns = "";
        discardAfterKong = false;
        preparationSeat = 0;
        pendingRiichi = -1;
        for (int seat = 0; seat < 4; seat++) {
            riichi[seat] = false;
            doubleRiichi[seat] = false;
            ippatsu[seat] = false;
            temporaryFuriten[seat] = false;
            riichiFuriten[seat] = false;
            draws[seat] = 0;
            discarded[seat] = 0;
            ownDiscards.get(seat).clear();
        }
        winningHan = 0;
        winningFu = 0;
        winningTile = "";
        winningTileInstance = null;
        forbiddenDiscards = java.util.Set.of();
        for (int tile = 0; tile < (taiwan != null ? 16 : 13); tile++)
            for (int offset = 0; offset < 4; offset++) take((dealer + offset) % 4, false);
        drawnTile = take(dealer, false);
        draws[dealer]++;
        lastAction = "Dealer drew a tile";
        if (sichuan != null) {
            String direction = option("exchange-direction", "RANDOM");
            exchangeOffset =
                    switch (direction) {
                        case "CLOCKWISE" -> 1;
                        case "ACROSS" -> 2;
                        case "COUNTERCLOCKWISE" -> 3;
                        case "RANDOM" -> new java.util.Random(seed + roundSerial).nextInt(3) + 1;
                        default -> throw new IllegalArgumentException("Invalid exchange direction");
                    };
            phase = Phase.EXCHANGE;
            lastAction = "Choose three tiles of one suit";
        }
    }

    private Tiles.Tile take(int seat, boolean replacement) {
        Tiles.Tile tile = replacement ? wall.replacement() : wall.draw();
        while (tile != null && tile.flower()) {
            flowers.get(seat).add(tile);
            tile = wall.replacement();
        }
        if (tile != null) hands.get(seat).add(tile);
        return tile;
    }

    @Override
    public String id() {
        return "mahjong";
    }

    @Override
    public int playerCount() {
        return 4;
    }

    @Override
    public int currentPlayer() {
        return current;
    }

    @Override
    public boolean finished() {
        return phase == Phase.FINISHED;
    }

    @Override
    public String outcome() {
        return result;
    }

    @Override
    public int deckSize() {
        return wall.remaining();
    }

    @Override
    public int handSize(int seat) {
        return hands.get(seat).size();
    }

    @Override
    public List<Piece> hand(int seat) {
        return hands.get(seat).stream()
                .sorted(
                        Comparator.comparing(
                                        (Tiles.Tile tile) ->
                                                phase == Phase.TURN
                                                        && seat == current
                                                        && tile.equals(drawnTile))
                                .thenComparingInt(Tiles.Tile::type)
                                .thenComparing(Tiles.Tile::id))
                .map(MahjongGame::piece)
                .toList();
    }

    @Override
    public List<Piece> discards(int seat) {
        return rivers.get(seat).stream().map(MahjongGame::piece).toList();
    }

    public String riichiTile(int seat) {
        return riichiTiles[seat];
    }

    public List<Meld> melds(int seat) {
        return List.copyOf(melds.get(seat));
    }

    public List<Piece> flowers(int seat) {
        return flowers.get(seat).stream().map(MahjongGame::piece).toList();
    }

    public boolean concealedMeldFacesVisible() {
        return riichiProfile;
    }

    @Override
    public List<Piece> exposed(int seat) {
        List<Piece> result = new ArrayList<>();
        for (int m = 0; m < melds.get(seat).size(); m++) {
            Meld meld = melds.get(seat).get(m);
            for (int i = 0; i < meld.tiles().size(); i++)
                result.add(
                        meld.open() || riichiProfile
                                ? piece(meld.tiles().get(i))
                                : new Piece("masked-" + seat + "-" + m + "-" + i, "back"));
        }
        flowers.get(seat).forEach(t -> result.add(piece(t)));
        return List.copyOf(result);
    }

    private static Piece piece(Tiles.Tile tile) {
        return new Piece(tile.id(), tile.face());
    }

    public boolean riichiDeclared(int seat) {
        return riichiProfile && seat >= 0 && seat < riichi.length && riichi[seat];
    }

    public String riichiDrawDiscard(int seat) {
        return riichiDeclared(seat) ? drawDiscard(seat) : null;
    }

    public String drawDiscard(int seat) {
        return phase == Phase.TURN && seat == current && drawnTile != null
                ? "discard:" + drawnTile.id()
                : null;
    }

    /** Reveal only settled riichi winners, including the claimed winning tile on ron. */
    public List<Piece> revealedHand(int seat) {
        if (!riichiProfile || !winners.contains(seat)
                || phase != Phase.ROUND_END && phase != Phase.FINISHED) return List.of();
        List<Piece> result = new ArrayList<>(hand(seat));
        if (winningTileInstance != null
                && result.stream().noneMatch(tile -> tile.id().equals(winningTileInstance.id())))
            result.add(piece(winningTileInstance));
        return List.copyOf(result);
    }

    public String riichiAutoDiscard(int seat) {
        String discard = riichiDrawDiscard(seat);
        return discard != null && winning(seat, drawnTile, true) == null ? discard : null;
    }

    /** Active player's physical hand tiles blocked specifically by kuikae after a call. */
    public boolean kuikaeForbidden(int seat, String tileId) {
        return phase == Phase.TURN
                && seat == current
                && !forbiddenDiscards.isEmpty()
                && hands.get(seat).stream()
                        .anyMatch(
                                tile ->
                                        tile.id().equals(tileId)
                                                && forbiddenDiscards.contains(tile.type()));
    }

    /**
     * Checks the observer's complete hand for missing yaku on the current draw or pending public
     * discard.
     */
    public boolean noYaku(int seat, String tileId) {
        if (!riichiProfile || seat < 0 || seat >= 4 || tileId == null) return false;
        boolean tsumo = phase == Phase.TURN && seat == current;
        Tiles.Tile tile =
                tsumo
                        ? drawnTile
                        : seat != source
                                        && offer == Offer.DISCARD
                                        && (phase == Phase.RON
                                                || phase == Phase.CALL
                                                || phase == Phase.CHI)
                                ? offered
                                : null;
        if (tile == null || !tile.id().equals(tileId)) return false;
        List<Tiles.Tile> candidate = new ArrayList<>(hands.get(seat));
        if (!tsumo) candidate.add(tile);
        if (!candidate.contains(tile) || riichiValue(seat, tile, tsumo).isPresent()) return false;
        int[] counts = Tiles.counts(candidate);
        return !HandSolver.solve(counts, 0, 4 - melds.get(seat).size()).isEmpty()
                || melds.get(seat).isEmpty()
                        && (HandSolver.sevenPairs(counts, 0, false)
                                || HandSolver.orphans(counts, 0));
    }

    @Override
    public List<String> legalActions(int seat) {
        if (finished() || seat != current) return List.of();
        if (phase == Phase.EXCHANGE) return exchangeActions(seat);
        if (phase == Phase.MISSING) return List.of("missing:m", "missing:p", "missing:s");
        if (phase == Phase.ROUND_END) return List.of("next-hand");
        if (phase == Phase.RON)
            return sichuan != null && flag("forced-win-last-four", true) && wall.remaining() <= 4
                    ? List.of("ron")
                    : List.of("ron", "pass");
        if (phase == Phase.CALL) {
            List<String> actions = callActions(seat);
            actions.add("pass");
            return List.copyOf(actions);
        }
        if (phase == Phase.CHI) {
            List<String> actions = chiActions(seat);
            actions.add("pass");
            return List.copyOf(actions);
        }
        List<String> actions = new ArrayList<>();
        if (sichuan != null && hasMissing(seat))
            return hand(seat).stream()
                    .filter(t -> Tiles.type(t.face()) / 9 == missingSuit[seat])
                    .map(t -> "discard:" + t.id())
                    .toList();
        if (winning(seat, drawnTile, true) != null) actions.add("tsumo");
        if (sichuan != null
                && !actions.isEmpty()
                && flag("forced-win-last-four", true)
                && wall.remaining() <= 4) return List.copyOf(actions);
        if (wall.canReplace() && (!riichiProfile || drawnTile != null)) {
            Map<Integer, List<Tiles.Tile>> matching = byType(seat);
            for (List<Tiles.Tile> group : matching.values())
                if (group.size() == 4
                        && (!riichi[seat] || riichiKongAllowed(seat, group.getFirst().type())))
                    actions.add("kan-closed:" + group.getFirst().id());
            for (Meld meld : melds.get(seat))
                if (!riichi[seat] && meld.kind() == Meld.Kind.TRIPLET && meld.open())
                    for (Tiles.Tile tile : matching.getOrDefault(meld.type(), List.of()))
                        if (sichuan == null || tile.equals(drawnTile))
                            actions.add("kan-added:" + tile.id());
        }
        if (riichiProfile
                && !riichi[seat]
                && scores[seat] >= 1000
                && wall.remaining() >= 1
                && melds.get(seat).stream().noneMatch(Meld::open))
            for (Tiles.Tile tile : hands.get(seat)) {
                List<Tiles.Tile> candidate = new ArrayList<>(hands.get(seat));
                candidate.remove(tile);
                if (!waits(seat, candidate, melds.get(seat).size()).isEmpty())
                    actions.add("riichi:" + tile.id());
            }
        for (Piece tile : hand(seat))
            if (!forbiddenDiscards.contains(Tiles.type(tile.face()))
                    && (!riichi[seat] || drawnTile != null && tile.id().equals(drawnTile.id())))
                actions.add("discard:" + tile.id());
        return List.copyOf(actions);
    }

    @Override
    public void apply(int seat, String action) {
        if (action == null || !legalActions(seat).contains(action))
            throw new RuleViolation("error.mahjong.action", "Invalid mahjong action");
        if (phase == Phase.EXCHANGE) {
            chooseExchange(seat, action);
            return;
        }
        if (phase == Phase.MISSING) {
            missingSuit[seat] = "mps".indexOf(action.charAt(action.length() - 1));
            if (++preparationSeat < 4) current = (dealer + preparationSeat) % 4;
            else {
                phase = Phase.TURN;
                current = dealer;
                drawnTile = hands.get(dealer).getLast();
            }
            return;
        }
        if (phase == Phase.ROUND_END) {
            roundSerial++;
            startHand();
            return;
        }
        if (phase == Phase.RON) {
            if (action.equals("ron")) {
                winners.add(seat);
                if (taiwan != null && option("ron-mode", "NEAREST_ONLY").equals("NEAREST_ONLY")) {
                    finishRon();
                    return;
                }
            } else if (sichuan != null && flag("over-water-ron", true))
                passedRonFan[seat] = sichuanValue(seat, offered, false).orElseThrow().fan();
            else if (riichiProfile) {
                temporaryFuriten[seat] = true;
                if (riichi[seat]) riichiFuriten[seat] = true;
            }
            if (++responseIndex < responders.size()) current = responders.get(responseIndex);
            else if (!winners.isEmpty()) finishRon();
            else afterRon();
            return;
        }
        if (phase == Phase.CALL || phase == Phase.CHI) {
            if (action.equals("pass")) {
                if (phase == Phase.CHI) advance();
                else if (++responseIndex < responders.size())
                    current = responders.get(responseIndex);
                else afterCalls();
            } else claim(seat, action);
            return;
        }
        if (action.equals("tsumo")) {
            winners.clear();
            winners.add(seat);
            winningTile = drawnTile.face();
            winningTileInstance = drawnTile;
            for (int other = 0; other < 4; other++)
                if (other != seat && !finishedSeats.contains(other))
                    pay(other, seat, payment(seat, other, drawnTile, true));
            finishWin("Self draw");
            return;
        }
        Tiles.Tile tile = find(seat, action.substring(action.indexOf(':') + 1));
        if (action.startsWith("discard:") || action.startsWith("riichi:")) {
            if (riichi[seat]) ippatsu[seat] = false;
            if (action.startsWith("riichi:")) {
                riichi[seat] = true;
                doubleRiichi[seat] = discarded[seat] == 0 && !anyCalls;
                ippatsu[seat] = true;
                pendingRiichi = seat;
            }
            ownDiscards.get(seat).add(tile.type());
            discarded[seat]++;
            if (riichi[seat] && riichiTiles[seat].isEmpty()) riichiTiles[seat] = tile.id();
            forbiddenDiscards = java.util.Set.of();
            hands.get(seat).remove(tile);
            rivers.get(seat).add(tile);
            discardCount++;
            discardAfterKong = kongDraw;
            lastAction = "Player " + (seat + 1) + " discarded " + tile.face();
            lastDiscardTile = tile.face();
            lastDiscardBy = seat;
            offer(seat, tile, Offer.DISCARD);
        } else
            offer(
                    seat,
                    tile,
                    action.startsWith("kan-added:") ? Offer.ADDED_KONG : Offer.CLOSED_KONG);
    }

    private void offer(int seat, Tiles.Tile tile, Offer kind) {
        source = seat;
        offered = tile;
        offer = kind;
        responders.clear();
        responseIndex = 0;
        winners.clear();
        if (kind != Offer.DISCARD) {
            java.util.Arrays.fill(ippatsu, false);
            anyCalls = true;
        }
        for (int offset = 1; offset < 4; offset++) {
            int other = (seat + offset) % 4;
            if (finishedSeats.contains(other)) continue;
            String win = winning(other, tile, false);
            if (taiwan != null
                    && (kind == Offer.CLOSED_KONG
                            || kind == Offer.ADDED_KONG && !flag("allow-rob-added-kan", true)))
                win = null;
            if (win != null
                    && (kind != Offer.CLOSED_KONG
                            || win.equals("THIRTEEN_ORPHANS")
                            || win.contains("KOKUSHI_MUSOU"))) responders.add(other);
        }
        if (responders.isEmpty()) afterRon();
        else {
            phase = Phase.RON;
            current = responders.getFirst();
        }
    }

    private void afterRon() {
        if (offer != Offer.DISCARD) {
            completeKong();
            return;
        }
        if (pendingRiichi >= 0) {
            scores[pendingRiichi] -= 1000;
            riichiSticks++;
            pendingRiichi = -1;
        }
        if (wall.remaining() == 0) {
            advance();
            return;
        }
        responders.clear();
        responseIndex = 0;
        for (int offset = 1; offset < 4; offset++) {
            int seat = (source + offset) % 4;
            if (!finishedSeats.contains(seat) && !callActions(seat).isEmpty()) responders.add(seat);
        }
        if (responders.isEmpty()) afterCalls();
        else {
            phase = Phase.CALL;
            current = responders.getFirst();
        }
    }

    private void afterCalls() {
        int next = (source + 1) % 4;
        if ((riichiProfile || taiwan != null) && !chiActions(next).isEmpty()) {
            phase = Phase.CHI;
            current = next;
        } else advance();
    }

    private List<String> callActions(int seat) {
        if (riichi[seat]) return new ArrayList<>();
        if (sichuan != null && (hasMissing(seat) || offered.type() / 9 == missingSuit[seat]))
            return new ArrayList<>();
        List<Tiles.Tile> matching =
                hands.get(seat).stream().filter(t -> t.type() == offered.type()).toList();
        List<String> result = new ArrayList<>();
        for (int first = 0; first < matching.size(); first++)
            for (int second = first + 1; second < matching.size(); second++)
                if (canDiscardAfterCall(seat, matching.get(first), matching.get(second)))
                    result.add("pon:" + matching.get(first).id() + "," + matching.get(second).id());
        if (matching.size() == 3 && wall.canReplace())
            result.add(
                    "kan-open:"
                            + matching.get(0).id()
                            + ","
                            + matching.get(1).id()
                            + ","
                            + matching.get(2).id());
        return result;
    }

    private List<String> chiActions(int seat) {
        List<String> actions = new ArrayList<>();
        int type = offered.type();
        if (type >= 27 || seat != (source + 1) % 4) return actions;
        if (riichi[seat]) return actions;
        for (int first = Math.max(type / 9 * 9, type - 2);
                first <= type && first % 9 <= 6;
                first++) {
            List<Integer> required = new ArrayList<>();
            for (int next = first; next <= first + 2; next++) if (next != type) required.add(next);
            for (Tiles.Tile a : hands.get(seat))
                if (a.type() == required.get(0))
                    for (Tiles.Tile b : hands.get(seat))
                        if (b.type() == required.get(1) && canDiscardAfterCall(seat, a, b))
                            actions.add("chi:" + a.id() + "," + b.id());
        }
        return actions;
    }

    private boolean canDiscardAfterCall(int seat, Tiles.Tile a, Tiles.Tile b) {
        java.util.Set<Integer> blocked = swapDiscards(a, b);
        return hands.get(seat).stream()
                .anyMatch(t -> !t.equals(a) && !t.equals(b) && !blocked.contains(t.type()));
    }

    private java.util.Set<Integer> swapDiscards(Tiles.Tile a, Tiles.Tile b) {
        if (!riichiProfile) return java.util.Set.of();
        java.util.Set<Integer> blocked = new java.util.HashSet<>();
        blocked.add(offered.type());
        if (a.type() != b.type()) {
            int low = Math.min(a.type(), b.type()), high = Math.max(a.type(), b.type());
            if (offered.type() < low && high % 9 < 8) blocked.add(high + 1);
            if (offered.type() > high && low % 9 > 0) blocked.add(low - 1);
        }
        return blocked;
    }

    private void claim(int seat, String action) {
        boolean kong = action.startsWith("kan-open:");
        List<Tiles.Tile> tiles = new ArrayList<>();
        for (String id : action.substring(action.indexOf(':') + 1).split(",")) {
            Tiles.Tile tile = find(seat, id);
            hands.get(seat).remove(tile);
            tiles.add(tile);
        }
        forbiddenDiscards = kong ? java.util.Set.of() : swapDiscards(tiles.get(0), tiles.get(1));
        rivers.get(source).remove(offered);
        tiles.add(offered);
        melds.get(seat)
                .add(
                        new Meld(
                                kong
                                        ? Meld.Kind.QUAD
                                        : action.startsWith("chi:")
                                                ? Meld.Kind.SEQUENCE
                                                : Meld.Kind.TRIPLET,
                                tiles,
                                true,
                                source));
        if (offered.id().equals(riichiTiles[source])) riichiTiles[source] = "";
        anyCalls = true;
        java.util.Arrays.fill(ippatsu, false);
        drawnTile = null;
        kongDraw = false;
        current = seat;
        phase = Phase.TURN;
        lastAction = "Player " + (seat + 1) + (kong ? " claimed a kong" : " claimed a pon");
        if (kong && sichuan != null) collectKong(seat, source, false);
        else lastKong = List.of();
        if (kong) draw(seat, true);
        offered = null;
    }

    private void completeKong() {
        List<Tiles.Tile> tiles = new ArrayList<>();
        if (offer == Offer.ADDED_KONG) {
            Meld original =
                    melds.get(source).stream()
                            .filter(
                                    m ->
                                            m.kind() == Meld.Kind.TRIPLET
                                                    && m.type() == offered.type())
                            .findFirst()
                            .orElseThrow();
            int index = melds.get(source).indexOf(original);
            tiles.addAll(original.tiles());
            tiles.add(offered);
            hands.get(source).remove(offered);
            melds.get(source)
                    .set(
                            index,
                            new Meld(
                                    Meld.Kind.QUAD,
                                    tiles,
                                    true,
                                    original.fromSeat(),
                                    original.calledTileId(),
                                    offered.id()));
        } else {
            tiles.addAll(
                    hands.get(source).stream().filter(t -> t.type() == offered.type()).toList());
            hands.get(source).removeAll(tiles);
            melds.get(source).add(new Meld(Meld.Kind.QUAD, tiles, false, source));
        }
        anyCalls = true;
        java.util.Arrays.fill(ippatsu, false);
        current = source;
        phase = Phase.TURN;
        lastAction = "Player " + (source + 1) + " declared a kong";
        offered = null;
        draw(current, true);
        if (sichuan != null) collectKong(current, -1, offer == Offer.CLOSED_KONG);
    }

    private void advance() {
        current = nextActive(source);
        phase = Phase.TURN;
        offered = null;
        lastKong = List.of();
        draw(current, false);
    }

    private int nextActive(int seat) {
        do {
            seat = (seat + 1) % 4;
        } while (finishedSeats.contains(seat));
        return seat;
    }

    private void draw(int seat, boolean replacement) {
        drawnTile = take(seat, replacement);
        kongDraw = replacement;
        forbiddenDiscards = java.util.Set.of();
        passedRonFan[seat] = -1;
        temporaryFuriten[seat] = false;
        if (drawnTile != null) draws[seat]++;
        if (drawnTile == null) {
            lastAction = "Wall exhausted";
            if (sichuan != null) settleSichuanDraw();
            finishHand(true);
            return;
        }
        lastAction =
                "Player " + (seat + 1) + (replacement ? " drew a replacement" : " drew a tile");
    }

    private void finishRon() {
        winningTile = offered.face();
        winningTileInstance = offered;
        if (offer != Offer.DISCARD) {
            hands.get(source).remove(offered);
            rivers.get(source).add(offered);
        }
        for (int winner : winners) {
            pay(source, winner, payment(winner, source, offered, false));
            if (riichiProfile) {
                var score = riichiValue(winner, offered, false).orElseThrow();
                int liable = responsibleSeat(winner, score);
                if (liable >= 0 && liable != source)
                    pay(liable, winner, score.ronPayment(winner == dealer) / 2);
            }
        }
        if (sichuan != null
                && offer == Offer.DISCARD
                && discardAfterKong
                && flag("call-transfer", true)
                && !lastKong.isEmpty()) {
            int amount = lastKong.stream().mapToInt(p -> p.amount).sum();
            for (KongPayment entry : lastKong) entry.refundable = false;
            for (int i = 0; i < winners.size(); i++)
                pay(
                        source,
                        winners.get(i),
                        amount / winners.size() + (i < amount % winners.size() ? 1 : 0));
        }
        finishWin(offer == Offer.DISCARD ? "Ron" : "Robbed kong");
    }

    private void finishWin(String label) {
        if (riichiProfile) {
            scores[winners.getFirst()] += riichiSticks * 1000;
            riichiSticks = 0;
            pendingRiichi = -1;
        }
        lastAction = label;
        lastWin = label + ": " + winners.stream().map(s -> "P" + (s + 1)).toList();
        if (sichuan != null) {
            finishedSeats.addAll(winners);
            if (finishedSeats.size() < 3) {
                source = winners.size() == 1 ? winners.getFirst() : source;
                advance();
                return;
            }
        }
        finishHand(false);
    }

    private void finishHand(boolean draw) {
        boolean dealerReady = false;
        if (riichiProfile && draw) {
            List<Integer> ready = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++)
                if (!waits(seat, hands.get(seat), melds.get(seat).size()).isEmpty())
                    ready.add(seat);
            dealerReady = ready.contains(dealer);
            if (!ready.isEmpty() && ready.size() < 4)
                for (int seat = 0; seat < 4; seat++)
                    scores[seat] +=
                            ready.contains(seat) ? 3000 / ready.size() : -3000 / (4 - ready.size());
        }
        List<String> changes = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            int change = scores[seat] - handStartScores[seat];
            changes.add("P" + (seat + 1) + " " + (change >= 0 ? "+" : "") + change);
        }
        settlement = String.join(", ", changes);
        if (roundLimit > 1) {
            boolean continues =
                    riichiProfile
                            ? (draw ? dealerReady : winners.contains(dealer))
                            : sichuan == null && (draw || winners.contains(dealer));
            if (riichiProfile) honba = draw || winners.contains(dealer) ? honba + 1 : 0;
            if (continues) dealerStreak++;
            else {
                dealer = (dealer + 1) % 4;
                dealerStreak = 0;
                dealerAdvances++;
            }
            if (dealerAdvances < roundLimit) {
                phase = Phase.ROUND_END;
                current = dealer;
                return;
            }
        }
        phase = Phase.FINISHED;
        int best = java.util.Arrays.stream(scores).max().orElseThrow();
        List<Integer> leaders = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) if (scores[seat] == best) leaders.add(seat);
        if (riichiProfile && riichiSticks > 0) {
            int share = riichiSticks * 1000 / leaders.size();
            for (int seat : leaders) scores[seat] += share;
            riichiSticks = 0;
            List<String> finalChanges = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                int change = scores[seat] - handStartScores[seat];
                finalChanges.add("P" + (seat + 1) + " " + (change >= 0 ? "+" : "") + change);
            }
            settlement = String.join(", ", finalChanges);
        }
        result =
                leaders.size() == 1
                        ? "winner:" + leaders.getFirst()
                        : "draw:" + (draw ? "wall-exhausted" : "shared-highest-score");
    }

    private String winning(int seat, Tiles.Tile tile, boolean tsumo) {
        if (tile == null) return null;
        List<Tiles.Tile> candidate = new ArrayList<>(hands.get(seat));
        if (!tsumo) candidate.add(tile);
        if (rules != null) return rules.win(candidate, melds.get(seat));
        if (riichiProfile) {
            if (!tsumo && furiten(seat)) return null;
            return riichiValue(seat, tile, tsumo)
                    .map(score -> String.join(",", score.yakuNames()))
                    .orElse(null);
        }
        if (sichuan != null) {
            var value = sichuanValue(seat, tile, tsumo);
            if (value.isEmpty()
                    || !tsumo
                            && flag("over-water-ron", true)
                            && value.get().fan() <= passedRonFan[seat]) return null;
            return "STANDARD";
        }
        return taiwan.score(candidate, melds.get(seat), taiwanContext(seat, tile, tsumo))
                        .isPresent()
                ? "STANDARD"
                : null;
    }

    private TaiwanRules.Context taiwanContext(int seat, Tiles.Tile tile, boolean tsumo) {
        return new TaiwanRules.Context(
                tile.type(),
                27 + (seat - dealer + 4) % 4,
                tsumo,
                tsumo && kongDraw,
                !tsumo && offer != Offer.DISCARD,
                wall.remaining() == 0,
                tsumo && seat == dealer && discardCount == 0 && !anyCalls,
                !tsumo && source == dealer && discardCount == 1 && !anyCalls,
                flowers.get(seat));
    }

    private java.util.Set<Integer> waits(int seat, List<Tiles.Tile> candidate, int meldCount) {
        java.util.Set<Integer> waits =
                new java.util.HashSet<>(
                        HandSolver.waits(
                                Tiles.counts(candidate), -1, 4 - meldCount, true, true, false));
        int[] owned = Tiles.counts(candidate);
        for (Meld meld : melds.get(seat)) for (Tiles.Tile tile : meld.tiles()) owned[tile.type()]++;
        waits.removeIf(type -> owned[type] >= 4);
        return waits;
    }

    private boolean furiten(int seat) {
        if (temporaryFuriten[seat] || riichiFuriten[seat]) return true;
        return waits(seat, hands.get(seat), melds.get(seat).size()).stream()
                .anyMatch(ownDiscards.get(seat)::contains);
    }

    private boolean riichiKongAllowed(int seat, int type) {
        if (drawnTile == null || drawnTile.type() != type) return false;
        List<Tiles.Tile> before = new ArrayList<>(hands.get(seat));
        before.remove(drawnTile);
        java.util.Set<Integer> oldWaits = waits(seat, before, melds.get(seat).size());
        List<Tiles.Tile> after = new ArrayList<>(hands.get(seat));
        after.removeIf(t -> t.type() == type);
        java.util.Set<Integer> newWaits = waits(seat, after, melds.get(seat).size() + 1);
        newWaits.remove(type);
        if (!oldWaits.equals(newWaits)) return false;
        int[] counts = Tiles.counts(before);
        for (int wait : oldWaits) {
            counts[wait]++;
            for (var shape : HandSolver.solve(counts, 0, 4 - melds.get(seat).size()))
                if (shape.groups().stream().noneMatch(g -> !g.sequence() && g.type() == type)) {
                    counts[wait]--;
                    return false;
                }
            counts[wait]--;
        }
        return !oldWaits.isEmpty();
    }

    private java.util.Optional<RiichiScore.Score> riichiValue(
            int seat, Tiles.Tile tile, boolean tsumo) {
        if (tile == null) return java.util.Optional.empty();
        List<Tiles.Tile> candidate = new ArrayList<>(hands.get(seat));
        if (!tsumo) candidate.add(tile);
        var flags =
                new RiichiScore.Flags(
                        tsumo,
                        riichi[seat],
                        doubleRiichi[seat],
                        ippatsu[seat],
                        tsumo && kongDraw,
                        !tsumo && offer != Offer.DISCARD,
                        tsumo && !kongDraw && wall.remaining() == 0,
                        !tsumo && offer == Offer.DISCARD && wall.remaining() == 0,
                        tsumo
                                && seat == dealer
                                && discarded[seat] == 0
                                && draws[seat] == 1
                                && !anyCalls,
                        tsumo
                                && seat != dealer
                                && discarded[seat] == 0
                                && draws[seat] == 1
                                && !anyCalls);
        return RiichiScore.score(
                candidate,
                melds.get(seat),
                new RiichiScore.Context(
                        tile,
                        27 + (seat - dealer + 4) % 4,
                        27 + dealerAdvances / 4,
                        flags,
                        wall.indicators(),
                        riichi[seat] ? wall.uraIndicators() : List.of(),
                        riichiOptions));
    }

    private int payment(int winner, int loser, Tiles.Tile tile, boolean tsumo) {
        if (rules != null) {
            winningPatterns = winning(winner, tile, tsumo);
            return tsumo ? rules.tsumoPayment : rules.ronPayment;
        }
        if (sichuan != null) {
            var score = sichuanValue(winner, tile, tsumo).orElseThrow();
            winningPatterns = String.join(",", score.patterns());
            return score.payment();
        }
        if (riichiProfile) {
            var score = riichiValue(winner, tile, tsumo).orElseThrow();
            winningPatterns = String.join(",", score.yakuNames());
            winningHan = score.han();
            winningFu = score.fu();
            int liable = responsibleSeat(winner, score);
            if (tsumo && liable >= 0)
                return loser == liable ? score.ronPayment(winner == dealer) + 300 * honba : 0;
            if (!tsumo && liable >= 0 && liable != loser)
                return score.ronPayment(winner == dealer) / 2 + 300 * honba;
            return (tsumo
                            ? (loser == dealer
                                    ? score.tsumoDealerPayment()
                                    : score.tsumoChildPayment(winner == dealer))
                            : score.ronPayment(winner == dealer))
                    + (tsumo ? 100 : 300) * honba;
        }
        List<Tiles.Tile> candidate = new ArrayList<>(hands.get(winner));
        if (!tsumo) candidate.add(tile);
        var score =
                taiwan.score(candidate, melds.get(winner), taiwanContext(winner, tile, tsumo))
                        .orElseThrow();
        winningPatterns = String.join(",", score.patterns());
        return taiwan.payment(score, winner == dealer || loser == dealer, dealerStreak);
    }

    private String option(String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }

    private int responsibleSeat(int winner, RiichiScore.Score score) {
        int dragons = 0, winds = 0;
        for (Meld meld : melds.get(winner))
            if (meld.open() && meld.kind() != Meld.Kind.SEQUENCE) {
                if (meld.type() >= 31 && ++dragons == 3 && score.yakuNames().contains("DAISANGEN"))
                    return meld.fromSeat();
                if (meld.type() >= 27
                        && meld.type() <= 30
                        && ++winds == 4
                        && score.yakuNames().contains("DAISUUSHII")) return meld.fromSeat();
            }
        return -1;
    }

    private boolean flag(String key, boolean fallback) {
        return Boolean.parseBoolean(option(key, Boolean.toString(fallback)));
    }

    private List<String> exchangeActions(int seat) {
        List<String> actions = new ArrayList<>();
        List<Tiles.Tile> selected = exchange.get(seat);
        if (selected.size() == 3) actions.add("exchange-confirm");
        if (selected.size() < 3)
            for (Piece piece : hand(seat)) {
                Tiles.Tile tile = find(seat, piece.id());
                int suit = tile.type() / 9;
                if (selected.contains(tile)
                        || !selected.isEmpty() && selected.getFirst().type() / 9 != suit) continue;
                if (hands.get(seat).stream().filter(t -> t.type() / 9 == suit).count() >= 3)
                    actions.add("exchange-add:" + tile.id());
            }
        for (Tiles.Tile tile : selected) actions.add("exchange-remove:" + tile.id());
        return List.copyOf(actions);
    }

    private void chooseExchange(int seat, String action) {
        if (action.startsWith("exchange-add:")) {
            exchange.get(seat).add(find(seat, action.substring(13)));
            return;
        }
        if (action.startsWith("exchange-remove:")) {
            exchange.get(seat).removeIf(tile -> tile.id().equals(action.substring(16)));
            return;
        }
        if (++preparationSeat < 4) {
            current = (dealer + preparationSeat) % 4;
            return;
        }
        for (int from = 0; from < 4; from++) hands.get(from).removeAll(exchange.get(from));
        for (int from = 0; from < 4; from++)
            hands.get((from + exchangeOffset) % 4).addAll(exchange.get(from));
        exchange.forEach(List::clear);
        phase = Phase.MISSING;
        preparationSeat = 0;
        current = dealer;
        lastAction = "Choose a missing suit";
    }

    private boolean hasMissing(int seat) {
        return hands.get(seat).stream().anyMatch(t -> t.type() / 9 == missingSuit[seat])
                || melds.get(seat).stream().anyMatch(m -> m.type() / 9 == missingSuit[seat]);
    }

    private java.util.Optional<SichuanRules.Score> sichuanValue(
            int seat, Tiles.Tile tile, boolean tsumo) {
        List<Tiles.Tile> candidate = new ArrayList<>(hands.get(seat));
        if (!tsumo) candidate.add(tile);
        return sichuan.score(
                candidate,
                melds.get(seat),
                new SichuanRules.Context(
                        missingSuit[seat],
                        tsumo,
                        tsumo && kongDraw,
                        !tsumo && offer != Offer.DISCARD,
                        !tsumo && offer == Offer.DISCARD && discardAfterKong,
                        wall.remaining() == 0,
                        tsumo && seat == dealer && discardCount == 0 && !anyCalls,
                        !tsumo && source == dealer && discardCount == 1 && !anyCalls));
    }

    private void collectKong(int winner, int discarder, boolean closed) {
        List<KongPayment> payments = new ArrayList<>();
        for (int loser = 0; loser < 4; loser++)
            if (loser != winner && !finishedSeats.contains(loser)) {
                int amount = sichuan.base() * (closed || loser == discarder ? 2 : 1);
                pay(loser, winner, amount);
                KongPayment entry = new KongPayment(loser, winner, amount);
                payments.add(entry);
                kongPayments.add(entry);
            }
        lastKong = List.copyOf(payments);
    }

    private void settleSichuanDraw() {
        int[] ready = new int[4];
        boolean[] pigs = new boolean[4];
        for (int seat = 0; seat < 4; seat++)
            if (!finishedSeats.contains(seat)) {
                pigs[seat] = hasMissing(seat);
                if (!pigs[seat])
                    ready[seat] =
                            sichuan.readyValue(hands.get(seat), melds.get(seat), missingSuit[seat]);
            }
        if (flag("refund-kong-on-exhaustion", true))
            for (KongPayment entry : kongPayments)
                if (entry.refundable && !finishedSeats.contains(entry.to) && ready[entry.to] == 0) {
                    pay(entry.to, entry.from, entry.amount);
                    entry.refundable = false;
                }
        for (int loser = 0; loser < 4; loser++)
            if (!finishedSeats.contains(loser)) {
                if (pigs[loser])
                    for (int winner = 0; winner < 4; winner++) {
                        if (winner != loser && !pigs[winner])
                            pay(loser, winner, sichuan.cappedPayment());
                    }
                else if (ready[loser] == 0)
                    for (int winner = 0; winner < 4; winner++)
                        if (!finishedSeats.contains(winner) && ready[winner] > 0)
                            pay(loser, winner, ready[winner]);
            }
    }

    private void pay(int from, int to, int points) {
        scores[from] -= points;
        scores[to] += points;
    }

    private Tiles.Tile find(int seat, String id) {
        return hands.get(seat).stream()
                .filter(tile -> tile.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private Map<Integer, List<Tiles.Tile>> byType(int seat) {
        Map<Integer, List<Tiles.Tile>> result = new LinkedHashMap<>();
        for (Tiles.Tile tile : hands.get(seat))
            result.computeIfAbsent(tile.type(), ignored -> new ArrayList<>()).add(tile);
        return result;
    }

    @Override
    public List<Cell> cells() {
        List<Cell> result = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            List<Piece> discards = discards(seat), exposed = exposed(seat);
            for (int i = 0; i < discards.size(); i++)
                result.add(
                        new Cell(
                                "d" + seat + "_" + i,
                                i % 8,
                                seat * 4 + i / 8,
                                discards.get(i).face(),
                                seat));
            for (int i = 0; i < exposed.size(); i++)
                result.add(
                        new Cell(
                                "e" + seat + "_" + i,
                                10 + i,
                                seat * 4,
                                exposed.get(i).face(),
                                seat));
        }
        return List.copyOf(result);
    }

    @Override
    public Map<String, String> publicInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("profile", profile);
        info.put("rulesVariant", "mahjong-" + profile + "-v1");
        info.put("tileCount", Integer.toString(tileCount()));
        info.put("phase", phase.name());
        info.put("dealer", Integer.toString(dealer));
        info.put("dealerStreak", Integer.toString(dealerStreak));
        info.put("round", Integer.toString(dealerAdvances + 1));
        info.put("rounds", Integer.toString(roundLimit));
        info.put("wall", Integer.toString(wall.remaining()));
        info.put("lastAction", lastAction);
        info.put("lastWin", lastWin);
        info.put("winningPatterns", winningPatterns);
        info.put("winningTile", winningTile);
        info.put("settlement", settlement);
        info.put(
                "winners",
                String.join(
                        ",",
                        (sichuan != null ? finishedSeats : winners)
                                .stream().map(String::valueOf).toList()));
        if (riichiProfile) {
            info.put("han", Integer.toString(winningHan));
            info.put("fu", Integer.toString(winningFu));
        }
        if (riichiProfile) {
            info.put(
                    "dora",
                    String.join(",", wall.indicators().stream().map(Tiles.Tile::face).toList()));
            info.put(
                    "doraIds",
                    String.join(",", wall.indicators().stream().map(Tiles.Tile::id).toList()));
            info.put("honba", Integer.toString(honba));
            info.put("riichiSticks", Integer.toString(riichiSticks));
            for (int seat = 0; seat < 4; seat++)
                info.put("riichi." + seat, Boolean.toString(riichi[seat]));
        }
        if (lastDiscardBy >= 0) {
            info.put("lastDiscardTile", lastDiscardTile);
            info.put("lastDiscardBy", Integer.toString(lastDiscardBy));
        }
        if (offered != null && (phase == Phase.RON || phase == Phase.CALL || phase == Phase.CHI)) {
            info.put("offeredTile", offered.face());
            info.put("offeredId", offered.id());
            info.put("offeredBy", Integer.toString(source));
        }
        for (int seat = 0; seat < 4; seat++) {
            info.put("handSize." + seat, Integer.toString(handSize(seat)));
            info.put("score." + seat, Integer.toString(scores[seat]));
            if (sichuan != null && phase != Phase.MISSING && missingSuit[seat] >= 0)
                info.put(
                        "missing." + seat,
                        "mps".substring(missingSuit[seat], missingSuit[seat] + 1));
        }
        return Map.copyOf(info);
    }
}
