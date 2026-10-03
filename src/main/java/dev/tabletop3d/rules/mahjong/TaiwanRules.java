package dev.tabletop3d.rules.mahjong;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Sixteen-tile additive-tai house rules; scoring values are explicit room options. */
public final class TaiwanRules {
    public record Context(int winningType,int seatWind,boolean tsumo,boolean kongBloom,boolean robKong,
                          boolean lastTile,boolean heavenly,boolean earthly,List<Tiles.Tile> flowers) {
        public Context{flowers=List.copyOf(flowers);}
    }
    public record Score(int tai,List<String> patterns){public Score{patterns=List.copyOf(patterns);}}
    private final Map<String,String> options;
    private final int base,perTai,cap,minimum,dealerTai,streakTai;
    public TaiwanRules(Map<String,String> options) {
        this.options=Map.copyOf(options);base=number("base-points",100);perTai=number("points-per-tai",100);
        cap=number("max-tai",16);minimum=number("minimum-tai",0);dealerTai=number("dealer-tai",1);streakTai=number("dealer-streak-tai",2);
        if(cap<minimum)throw new IllegalArgumentException("Taiwan maximum tai is below its minimum");
    }
    public Optional<Score> score(List<Tiles.Tile> concealed,List<Meld> melds,Context context) {
        int[] counts=Tiles.counts(concealed),total=counts.clone();
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())total[tile.type()]++;
        for(int count:total)if(count>4)return Optional.empty();
        List<HandSolver.Shape> shapes=HandSolver.solve(counts,0,5-melds.size());if(shapes.isEmpty())return Optional.empty();
        boolean closed=melds.stream().noneMatch(Meld::open),honors=false;int suits=0;
        for(int type=0;type<34;type++)if(total[type]>0){if(type>=27)honors=true;else suits|=1<<(type/9);}
        int[] waiting=counts.clone();waiting[context.winningType()]--;
        boolean singleWait=HandSolver.waits(waiting,-1,5-melds.size(),false,false,false).size()==1;
        Score best=null;
        for(HandSolver.Shape shape:shapes) {
            Map<String,Integer> patterns=new LinkedHashMap<>();
            List<HandSolver.Group> groups=new ArrayList<>(shape.groups());
            for(Meld meld:melds)groups.add(new HandSolver.Group(meld.kind()==Meld.Kind.SEQUENCE,meld.type(),0));
            List<Integer> triplets=groups.stream().filter(g->!g.sequence()).map(HandSolver.Group::type).toList();
            int dragons=(int)triplets.stream().filter(t->t>=31).count(),winds=(int)triplets.stream().filter(t->t>=27&&t<=30).count();
            boolean bigDragons=dragons==3,smallDragons=dragons==2&&shape.pair()>=31;
            boolean bigWinds=winds==4,smallWinds=winds==3&&shape.pair()>=27&&shape.pair()<=30;
            add(patterns,"TSUMO",context.tsumo(),1);add(patterns,"CLOSED_HAND",closed,1);add(patterns,"FULLY_CONCEALED",closed&&context.tsumo(),1);
            if(!bigDragons&&!smallDragons&&dragons>0)patterns.put("DRAGON_PUNG",number("pattern-tai.DRAGON_PUNG",1)*dragons);
            add(patterns,"SEAT_WIND_PUNG",!bigWinds&&!smallWinds&&triplets.contains(context.seatWind()),1);
            int matching=(int)context.flowers().stream().filter(t->(t.type()-34)%4==context.seatWind()-27).count();
            if(matching>0)patterns.put("MATCHING_FLOWER",number("pattern-tai.MATCHING_FLOWER",1)*matching);
            int flowerKongs=0;for(int first:new int[]{34,38}){int start=first;if(context.flowers().stream().filter(t->t.type()>=start&&t.type()<start+4).map(Tiles.Tile::type).distinct().count()==4)flowerKongs++;}
            if(flowerKongs>0)patterns.put("FLOWER_KONG",number("pattern-tai.FLOWER_KONG",2)*flowerKongs);
            add(patterns,"SINGLE_WAIT",singleWait,1);add(patterns,"KONG_BLOOM",context.kongBloom(),1);add(patterns,"ROB_KONG",context.robKong(),1);
            add(patterns,"LAST_TILE",context.lastTile(),1);
            boolean twoSided=shape.groups().stream().anyMatch(g->g.sequence()&&(context.winningType()==g.type()&&g.type()%9!=6
                ||context.winningType()==g.type()+2&&g.type()%9!=0));
            add(patterns,"PING_HU",!context.tsumo()&&!honors&&context.flowers().isEmpty()&&triplets.isEmpty()&&twoSided,2);
            add(patterns,"FULLY_EXPOSED",!context.tsumo()&&melds.size()==5&&!closed&&melds.stream().allMatch(Meld::open),2);
            add(patterns,"ALL_TRIPLETS",triplets.size()==5,4);
            add(patterns,"MIXED_ONE_SUIT",Integer.bitCount(suits)==1&&honors,4);add(patterns,"CLEAN_ONE_SUIT",Integer.bitCount(suits)==1&&!honors,12);
            add(patterns,"SMALL_THREE_DRAGONS",smallDragons,4);add(patterns,"BIG_THREE_DRAGONS",bigDragons,8);
            add(patterns,"SMALL_FOUR_WINDS",smallWinds,8);add(patterns,"BIG_FOUR_WINDS",bigWinds,16);add(patterns,"ALL_HONORS",suits==0,16);
            add(patterns,"HEAVENLY_WIN",context.heavenly(),16);add(patterns,"EARTHLY_WIN",context.earthly(),16);
            int tai=Math.min(cap,patterns.values().stream().mapToInt(Integer::intValue).sum());
            if(tai>=minimum&&(best==null||tai>best.tai()))best=new Score(tai,patterns.keySet().stream().filter(key->patterns.get(key)>0).toList());
        }
        return Optional.ofNullable(best);
    }
    public int payment(Score score,boolean dealerInvolved,int streak){return base+perTai*(score.tai()+(dealerInvolved?dealerTai+streakTai*streak:0));}
    private void add(Map<String,Integer> values,String key,boolean condition,int points){if(condition)values.put(key,number("pattern-tai."+key,points));}
    private int number(String key,int fallback) {
        int value=Integer.parseInt(options.getOrDefault(key,Integer.toString(fallback)));
        if(value<0||value>1_000_000)throw new IllegalArgumentException("Invalid Taiwan score option: "+key);return value;
    }
}
