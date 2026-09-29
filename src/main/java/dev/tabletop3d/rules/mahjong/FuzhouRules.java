package dev.tabletop3d.rules.mahjong;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Independent eight-flower, sixteen-tile Fuzhou house profile. Honors remain ordinary tiles;
 * white dragon does not impersonate the gold indicator. Flowers plus gold, kongs and dealer
 * streak are added to the base before ONE winning multiplier is applied; special wins do not stack.
 * The engine authorizes the opening rob-gold window and applies payments to the responsible seats.
 * Public regional context: https://www.laiyouxi.com/news/145.html (its honor-as-flower variant differs).
 * Numeric defaults are room settings, not a claim of compatibility with another implementation.
 */
public final class FuzhouRules {
    public record Context(int wildcardType,int winningType,boolean tsumo,boolean robGold,int dealerStreak,List<Tiles.Tile> flowers) {
        public Context {
            flowers=List.copyOf(flowers);
            if(wildcardType<0||wildcardType>=34||winningType< -1||winningType>=34||dealerStreak<0
                ||flowers.stream().anyMatch(t->!t.flower())||flowers.stream().map(Tiles.Tile::type).distinct().count()!=flowers.size())
                throw new IllegalArgumentException("Invalid Fuzhou scoring context");
        }
    }
    public record Score(int flowers,int multiplier,int payment,List<String> patterns) {
        public Score{patterns=List.copyOf(patterns);}
    }
    private final int point,base,openKan,closedKan,streak,ron,tsumo,threeGold,robGold,goldPair;
    private final boolean allowThreeGold,allowRobGold,allowGoldPair;
    public FuzhouRules(Map<String,String> options) {
        point=number(options,"point-per-flower",100,1);base=number(options,"base-flowers",3,0);
        openKan=number(options,"open-kan-flowers",1,0);closedKan=number(options,"closed-kan-flowers",2,0);
        streak=number(options,"dealer-streak-flowers",2,0);ron=number(options,"ron-multiplier",1,1);tsumo=number(options,"tsumo-multiplier",2,1);
        threeGold=number(options,"san-jin-dao-multiplier",40,1);robGold=number(options,"qiang-jin-multiplier",40,1);goldPair=number(options,"jin-que-multiplier",60,1);
        allowThreeGold=flag(options,"allow-san-jin-dao",true);allowRobGold=flag(options,"allow-qiang-jin",true);allowGoldPair=flag(options,"allow-jin-que",true);
    }
    public boolean allowSanJinDao(){return allowThreeGold;}
    public boolean allowQiangJin(){return allowRobGold;}
    public boolean allowJinQue(){return allowGoldPair;}
    public Optional<Score> score(List<Tiles.Tile> concealed,List<Meld> melds,Context context) {
        if(melds.size()>5||!physical(concealed,melds))return Optional.empty();
        int[] counts=Tiles.counts(concealed);int gold=counts[context.wildcardType()];counts[context.wildcardType()]=0;
        int flowerUnits=Math.addExact(base,Math.addExact(gold+context.flowers().size(),Math.multiplyExact(streak,context.dealerStreak())));
        for(Meld meld:melds)if(meld.kind()==Meld.Kind.QUAD)flowerUnits=Math.addExact(flowerUnits,meld.open()?openKan:closedKan);
        int normalSize=17-3*melds.size();
        if(context.robGold()) {
            if(!allowRobGold||!melds.isEmpty()||context.winningType()!=context.wildcardType()||gold==0)return Optional.empty();
            return HandSolver.solve(counts,gold,5).isEmpty()?Optional.empty():Optional.of(result(flowerUnits,robGold,"QIANG_JIN"));
        }
        Score best=null;
        if(allowThreeGold&&context.tsumo()&&gold==3&&(concealed.size()==normalSize||concealed.size()==normalSize-1))
            best=result(flowerUnits,threeGold,"SAN_JIN_DAO");
        if(context.winningType()<0||concealed.stream().noneMatch(t->t.type()==context.winningType()))return Optional.ofNullable(best);
        List<HandSolver.Shape> shapes=HandSolver.solve(counts,gold,5-melds.size());
        if(!shapes.isEmpty()) {
            Score ordinary=result(flowerUnits,context.tsumo()?tsumo:ron,context.tsumo()?"TSUMO":"RON");
            if(best==null||ordinary.payment()>best.payment())best=ordinary;
            if(allowGoldPair&&shapes.stream().anyMatch(s->s.pairWildcards()==2)) {
                Score golden=result(flowerUnits,goldPair,"JIN_QUE");if(best==null||golden.payment()>best.payment())best=golden;
            }
        }
        return Optional.ofNullable(best);
    }
    private Score result(int flowers,int multiplier,String pattern){return new Score(flowers,multiplier,Math.multiplyExact(Math.multiplyExact(point,flowers),multiplier),List.of(pattern));}
    private static boolean physical(List<Tiles.Tile> concealed,List<Meld> melds) {
        int[] counts=new int[34];Set<String> ids=new HashSet<>();
        for(Tiles.Tile tile:concealed)if(tile.flower()||!ids.add(tile.id())||++counts[tile.type()]>4)return false;
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())if(!ids.add(tile.id())||++counts[tile.type()]>4)return false;
        return true;
    }
    private static String option(Map<String,String> options,String key,String fallback){return options.getOrDefault("fuzhou."+key,options.getOrDefault(key,fallback));}
    private static int number(Map<String,String> options,String key,int fallback,int minimum) {
        int value=Integer.parseInt(option(options,key,Integer.toString(fallback)));
        if(value<minimum||value>1_000_000)throw new IllegalArgumentException("Invalid Fuzhou score option: "+key);return value;
    }
    private static boolean flag(Map<String,String> options,String key,boolean fallback) {
        return switch(option(options,key,Boolean.toString(fallback))){case "true"->true;case "false"->false;default->throw new IllegalArgumentException("Invalid Fuzhou flag: "+key);};
    }
}
