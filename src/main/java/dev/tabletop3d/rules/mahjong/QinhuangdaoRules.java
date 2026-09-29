package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Independent Qinhuangdao house profile. Ordinary patterns multiply up to the room cap. Natural
 * four-of-a-kind groups determine the luxury-seven-pairs tier; gold substitutes do not create one.
 * Heavenly/earthly wins use their own base (times 2 per natural seven-pairs quad), without ordinary
 * bonuses. Four concealed gold tiles can win immediately by self draw for the WILDCARD_KONG base.
 * Gold-single-wait requires a preexisting gold singleton; gold-winning-tile is its gold-on-gold form.
 * Catch-five means a natural 4m/5m/6m group completed by the actual 5m. One winning tile cannot
 * complete both the pair and that sequence; alternative assignments are scored separately.
 * These are explicit house
 * choices, not inferred compatibility with a binary. The engine authorizes all timing flags.
 * Public scoring reference: https://www.xinyueyouxi.com/game/197-5 .
 */
public final class QinhuangdaoRules {
    public enum RonMode { NEAREST_ONLY,MULTIPLE,TSUMO_ONLY }
    public enum WildcardMode { NEXT_OF_INDICATOR,FIXED }
    public record Context(int wildcardType,int winningType,boolean tsumo,boolean kongBloom,boolean robKong,
                          boolean lastTile,boolean heavenly,boolean earthly,boolean wildcardKong) {
        public Context {
            if(wildcardType<0||wildcardType>=34||winningType< -1||winningType>=34)
                throw new IllegalArgumentException("Invalid Qinhuangdao scoring context");
        }
    }
    public record Score(int multiplier,int payment,List<String> patterns){public Score{patterns=List.copyOf(patterns);}}
    private final Map<String,String> options;
    private final int tileCount,base,cap,fixedWildcard;
    private final boolean chi,pon,openKan,closedKan,addedKan,robAddedKan,discardable,callable,standard,sevenPairs,dealerWin,drawDealer;
    private final RonMode ronMode;
    private final WildcardMode wildcardMode;
    private final Map<String,Integer> multipliers;
    public QinhuangdaoRules(Map<String,String> options) {
        this.options=Map.copyOf(options);
        tileCount=switch(option("tile-set","SUITS_AND_WINDS")){case "108","SUITS","SUITS_ONLY"->108;case "124","SUITS_AND_WINDS"->124;case "136","STANDARD","FULL"->136;default->throw new IllegalArgumentException("Invalid Qinhuangdao tile set");};
        base=number("base-points",100);cap=number("max-multiplier",64);
        if((long)base*cap>Integer.MAX_VALUE)throw new IllegalArgumentException("Qinhuangdao maximum payment exceeds integer range");
        chi=flag("allow-chi",false);pon=flag("allow-pon",true);openKan=flag("allow-open-kan",true);closedKan=flag("allow-closed-kan",true);addedKan=flag("allow-added-kan",true);
        robAddedKan=flag("allow-rob-added-kan",true);discardable=flag("wildcard-discardable",false);callable=flag("wildcard-callable",false);
        standard=flag("standard-win",true);sevenPairs=flag("seven-pairs",true);dealerWin=flag("dealer-win-continues",true);drawDealer=flag("draw-dealer-continues",true);
        if(!standard&&!sevenPairs)throw new IllegalArgumentException("At least one Qinhuangdao winning shape is required");
        ronMode=RonMode.valueOf(option("ron-mode","NEAREST_ONLY"));wildcardMode=WildcardMode.valueOf(option("wildcard-mode","NEXT_OF_INDICATOR"));
        fixedWildcard=Tiles.type(option("fixed-wildcard","M1"));
        if(fixedWildcard>=tileCount/4)throw new IllegalArgumentException("Fixed wildcard is absent from the selected Qinhuangdao tile set");
        Map<String,Integer> values=new LinkedHashMap<>();
        String[] names={"PING_HU","SEVEN_PAIRS","LUXURY_SEVEN_PAIRS","DOUBLE_LUXURY_SEVEN_PAIRS","TRIPLE_LUXURY_SEVEN_PAIRS","ALL_MELDS_EXPOSED","CLOSED_HAND","NO_WILDCARD","ALL_TRIPLETS","WILDCARD_SINGLE_WAIT","WILDCARD_WINNING_TILE","CATCH_FIVE","KONG_BLOOM","LAST_TILE","HEAVENLY_WIN","EARTHLY_WIN","WILDCARD_KONG","ROB_KONG"};
        int[] defaults={1,4,8,16,32,4,2,2,2,2,2,2,2,2,10,10,10,2};
        for(int i=0;i<names.length;i++)values.put(names[i],number("pattern-multipliers."+names[i],defaults[i]));multipliers=Map.copyOf(values);
    }
    public int tileCount(){return tileCount;}
    public int wildcardType(int indicator){if(wildcardMode==WildcardMode.FIXED)return fixedWildcard;if(indicator<0||indicator>=tileCount/4)throw new IllegalArgumentException("Indicator is absent from the selected tile set");return Tiles.next(indicator);}
    public RonMode ronMode(){return ronMode;}
    public boolean allowChi(){return chi;}
    public boolean allowPon(){return pon;}
    public boolean allowOpenKan(){return openKan;}
    public boolean allowClosedKan(){return closedKan;}
    public boolean allowAddedKan(){return addedKan;}
    public boolean allowRobAddedKan(){return robAddedKan;}
    public boolean wildcardDiscardable(){return discardable;}
    public boolean wildcardCallable(){return callable;}
    public boolean dealerWinContinues(){return dealerWin;}
    public boolean drawDealerContinues(){return drawDealer;}
    public Optional<Score> score(List<Tiles.Tile> concealed,List<Meld> melds,Context context) {
        if(melds.size()>4||!physical(concealed,melds)||context.wildcardType()>=tileCount/4)return Optional.empty();
        if(!context.tsumo()&&ronMode==RonMode.TSUMO_ONLY||context.robKong()&&!robAddedKan)return Optional.empty();
        if(context.heavenly()&&(!context.tsumo()||context.earthly()||!melds.isEmpty())||context.earthly()&&!melds.isEmpty()
            ||context.kongBloom()&&!context.tsumo()||context.robKong()&&context.tsumo())return Optional.empty();
        int[] counts=Tiles.counts(concealed);int gold=counts[context.wildcardType()];counts[context.wildcardType()]=0;
        if(context.wildcardKong()) {
            int size=14-3*melds.size();
            return context.tsumo()&&gold==4&&(concealed.size()==size||concealed.size()==size-1)
                ?Optional.of(result(List.of("WILDCARD_KONG"),multipliers.get("WILDCARD_KONG"))):Optional.empty();
        }
        if(context.winningType()<0||concealed.stream().noneMatch(t->t.type()==context.winningType()))return Optional.empty();
        Score best=null;
        if(sevenPairs&&melds.isEmpty()&&HandSolver.sevenPairs(counts,gold,true)) {
            int quads=(int)Arrays.stream(counts).filter(c->c==4).count();
            best=candidate(List.of(pairPattern(quads)),quads,context,melds,gold);
        }
        if(standard)for(HandSolver.Shape shape:HandSolver.solve(counts,gold,4-melds.size())) {
            List<String> patterns=new ArrayList<>();patterns.add("PING_HU");
            if(melds.size()==4&&melds.stream().allMatch(Meld::open))patterns.add("ALL_MELDS_EXPOSED");
            if(shape.groups().stream().noneMatch(HandSolver.Group::sequence)&&melds.stream().noneMatch(m->m.kind()==Meld.Kind.SEQUENCE))patterns.add("ALL_TRIPLETS");
            boolean goldWait=context.winningType()==context.wildcardType()?shape.pairWildcards()==2:shape.pair()==context.winningType()&&shape.pairWildcards()==1;
            List<List<String>> assignments=new ArrayList<>();assignments.add(patterns);
            if(goldWait) {
                List<String> pairWin=new ArrayList<>(patterns);pairWin.add("WILDCARD_SINGLE_WAIT");
                if(context.winningType()==context.wildcardType())pairWin.add("WILDCARD_WINNING_TILE");assignments.add(pairWin);
            }
            if(context.winningType()==4&&context.wildcardType()!=4&&shape.groups().stream().anyMatch(g->g.sequence()&&g.type()==3&&g.wildcards()==0)) {
                List<String> sequenceWin=new ArrayList<>(patterns);sequenceWin.add("CATCH_FIVE");assignments.add(sequenceWin);
            }
            for(List<String> assignment:assignments) {
                Score candidate=candidate(assignment,0,context,melds,gold);if(best==null||candidate.multiplier()>best.multiplier())best=candidate;
            }
        }
        return Optional.ofNullable(best);
    }
    private Score candidate(List<String> shapePatterns,int naturalQuads,Context context,List<Meld> melds,int gold) {
        if(context.heavenly()||context.earthly()) {
            String special=context.heavenly()?"HEAVENLY_WIN":"EARTHLY_WIN";
            List<String> patterns=new ArrayList<>();patterns.add(special);if(naturalQuads>0)patterns.add(pairPattern(naturalQuads));
            return result(patterns,(long)multipliers.get(special)*(1<<naturalQuads));
        }
        List<String> patterns=new ArrayList<>(shapePatterns);
        if(melds.stream().noneMatch(Meld::open))patterns.add("CLOSED_HAND");
        if(gold==0&&melds.stream().flatMap(m->m.tiles().stream()).noneMatch(t->t.type()==context.wildcardType()))patterns.add("NO_WILDCARD");
        if(context.kongBloom())patterns.add("KONG_BLOOM");if(context.robKong())patterns.add("ROB_KONG");if(context.lastTile())patterns.add("LAST_TILE");
        long product=1;for(String pattern:patterns)product=Math.min(cap,product*multipliers.get(pattern));return result(patterns,product);
    }
    private Score result(List<String> patterns,long multiplier){int capped=(int)Math.min(cap,multiplier);return new Score(capped,base*capped,patterns);}
    private static String pairPattern(int quads){return switch(quads){case 0->"SEVEN_PAIRS";case 1->"LUXURY_SEVEN_PAIRS";case 2->"DOUBLE_LUXURY_SEVEN_PAIRS";default->"TRIPLE_LUXURY_SEVEN_PAIRS";};}
    private boolean physical(List<Tiles.Tile> concealed,List<Meld> melds) {
        int[] counts=new int[34];Set<String> ids=new HashSet<>();
        for(Tiles.Tile tile:concealed)if(tile.type()>=tileCount/4||!ids.add(tile.id())||++counts[tile.type()]>4)return false;
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())if(tile.type()>=tileCount/4||!ids.add(tile.id())||++counts[tile.type()]>4)return false;
        return true;
    }
    private String option(String key,String fallback){return options.getOrDefault("qinhuangdao."+key,options.getOrDefault(key,fallback));}
    private boolean flag(String key,boolean fallback){return switch(option(key,Boolean.toString(fallback))){case "true"->true;case "false"->false;default->throw new IllegalArgumentException("Invalid Qinhuangdao flag: "+key);};}
    private int number(String key,int fallback){int value=Integer.parseInt(option(key,Integer.toString(fallback)));if(value<1||value>1_000_000)throw new IllegalArgumentException("Invalid Qinhuangdao score option: "+key);return value;}
}
