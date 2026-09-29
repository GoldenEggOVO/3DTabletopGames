package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Blood-battle fan values and exhaustive-draw readiness, independent of turn state and payments. */
public final class SichuanRules {
    public record Context(int missingSuit,boolean tsumo,boolean kongBloom,boolean robKong,boolean kongDiscard,
                          boolean lastTile,boolean heavenly,boolean earthly) {}
    public record Score(int fan,int payment,List<String> patterns){public Score{patterns=List.copyOf(patterns);}}
    private final int base,maxFan;
    private final String selfDraw;
    public SichuanRules(Map<String,String> options) {
        base=Integer.parseInt(option(options,"base-points","100"));maxFan=Integer.parseInt(option(options,"max-fan","4"));selfDraw=option(options,"self-draw-mode","ADD_BASE");
        if(base<1||base>100000||maxFan<0||maxFan>10||!List.of("ADD_BASE","ADD_FAN").contains(selfDraw))throw new IllegalArgumentException("Invalid Sichuan scoring options");
    }
    public int base(){return base;}
    public int cappedPayment(){return base*(1<<maxFan);}
    public Optional<Score> score(List<Tiles.Tile> concealed,List<Meld> melds,Context context) {
        int[] counts=Tiles.counts(concealed),total=counts.clone();
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())total[tile.type()]++;
        int suits=0,roots=0;
        for(int type=0;type<34;type++)if(total[type]>0) {
            if(type>=27||type/9==context.missingSuit()||total[type]>4)return Optional.empty();suits|=1<<(type/9);if(total[type]==4)roots++;
        }
        if(context.missingSuit()<0||Integer.bitCount(suits)>2)return Optional.empty();
        List<HandSolver.Shape> shapes=HandSolver.solve(counts,0,4-melds.size());
        boolean pairs=melds.isEmpty()&&HandSolver.sevenPairs(counts,0,true);if(shapes.isEmpty()&&!pairs)return Optional.empty();
        int shapeFan=pairs?2:0;List<String> best=new ArrayList<>(List.of(pairs?"SEVEN_PAIRS":"STANDARD"));
        for(HandSolver.Shape shape:shapes) {
            List<HandSolver.Group> groups=new ArrayList<>(shape.groups());for(Meld meld:melds)groups.add(new HandSolver.Group(meld.kind()==Meld.Kind.SEQUENCE,meld.type(),0));
            int fan=0;List<String> patterns=new ArrayList<>();
            if(groups.stream().noneMatch(HandSolver.Group::sequence)){fan=1;patterns.add("ALL_TRIPLETS");}
            boolean terminals=Tiles.terminalOrHonor(shape.pair())&&groups.stream().allMatch(g->g.sequence()?g.type()%9==0||g.type()%9==6:Tiles.terminalOrHonor(g.type()));
            if(terminals){fan=Math.max(fan,2);patterns.add("ALL_TERMINALS_IN_GROUPS");}
            boolean jiang=true;for(int type=0;type<27;type++)if(total[type]>0&&type%9!=1&&type%9!=4&&type%9!=7)jiang=false;
            if(jiang&&groups.stream().noneMatch(HandSolver.Group::sequence)){fan=3;patterns.add("JIANG_TRIPLETS");}
            if(melds.size()==4){fan=Math.max(fan,2);patterns.add("EXPOSED_SINGLE_WAIT");}
            if(fan>shapeFan){shapeFan=fan;best=patterns;}
        }
        int fan=shapeFan;
        if(Integer.bitCount(suits)==1){fan+=2;best.add("ONE_SUIT");}
        if(roots>0){fan+=roots;best.add("ROOTS_"+roots);}
        if(context.kongBloom()){fan++;best.add("KONG_BLOOM");}if(context.robKong()){fan++;best.add("ROB_KONG");}
        if(context.kongDiscard()){fan++;best.add("KONG_DISCARD");}if(context.lastTile()){fan++;best.add("LAST_TILE");}
        if(context.heavenly()||context.earthly()){fan+=5;best.add(context.heavenly()?"HEAVENLY_WIN":"EARTHLY_WIN");}
        if(context.tsumo()&&selfDraw.equals("ADD_FAN")){fan++;best.add("SELF_DRAW_FAN");}
        fan=Math.min(maxFan,fan);int payment=base*(1<<fan);
        if(context.tsumo()&&selfDraw.equals("ADD_BASE")){payment+=base;best.add("SELF_DRAW_BASE");}
        return Optional.of(new Score(fan,payment,best));
    }
    public int readyValue(List<Tiles.Tile> concealed,List<Meld> melds,int missingSuit) {
        int best=0;int[] counts=Tiles.counts(concealed);
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())counts[tile.type()]++;
        for(int type=0;type<27;type++)if(type/9!=missingSuit&&counts[type]<4) {
            List<Tiles.Tile> candidate=new ArrayList<>(concealed);candidate.add(new Tiles.Tile("ready-probe",type,false));
            var score=score(candidate,melds,new Context(missingSuit,false,false,false,false,false,false,false));
            if(score.isPresent())best=Math.max(best,score.get().payment());
        }
        return best;
    }
    private static String option(Map<String,String> options,String key,String fallback){return options.getOrDefault("sichuan."+key,options.getOrDefault(key,fallback));}
}
