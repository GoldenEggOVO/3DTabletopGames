package dev.tabletop3d.rules.mahjong;

import java.util.*;

/**
 * Original evaluator for the EMA 2025 hand patterns, with explicit room overrides.
 * Double-wind pairs are two fu; distinct natural yakuman do not stack. Renhou is not enabled.
 * Payments exclude counters, riichi deposits and responsibility payments, which belong to the round.
 * @see <a href="https://mahjong-europe.org/portal/images/docs/Riichi-rules-2025-EN.pdf">EMA 2025, chapter 4</a>
 */
public final class RiichiScore {
    public record Options(boolean openTanyao,boolean kiriage,boolean countedYakuman,boolean doubleYakuman) {
        public static final Options DEFAULT=new Options(true,false,true,false);
    }
    public record Flags(boolean tsumo,boolean riichi,boolean doubleRiichi,boolean ippatsu,boolean rinshan,
                        boolean chankan,boolean haitei,boolean houtei,boolean tenhou,boolean chiihou) {}
    public record Context(Tiles.Tile winningTile,int seatWind,int roundWind,Flags flags,
                          List<Tiles.Tile> doraIndicators,List<Tiles.Tile> uraIndicators,Options options) {
        public Context {
            if(seatWind<27||seatWind>30||roundWind<27||roundWind>30)throw new IllegalArgumentException("Riichi wind");
            doraIndicators=List.copyOf(doraIndicators);uraIndicators=List.copyOf(uraIndicators);
        }
    }
    public record Yaku(String name,int han,int yakuman) {}
    public record Score(int han,int fu,int yakuman,int basePoints,List<Yaku> yaku) {
        public Score {yaku=List.copyOf(yaku);}
        public List<String> yakuNames(){return yaku.stream().map(Yaku::name).toList();}
        public int ronPayment(boolean dealer){return rounded(basePoints*(dealer?6:4));}
        public int tsumoDealerPayment(){return rounded(basePoints*2);}
        public int tsumoChildPayment(boolean dealerWinner){return rounded(basePoints*(dealerWinner?2:1));}
    }
    private record Group(boolean sequence,int type,boolean open,boolean quad) {}
    private RiichiScore() {}
    private static int rounded(int points){return (points+99)/100*100;}
    public static Optional<Score> score(List<Tiles.Tile> concealed,List<Meld> melds,Context context){
        if(melds.size()>4||concealed.size()!=(4-melds.size())*3+2||!concealed.contains(context.winningTile()))return Optional.empty();
        List<Tiles.Tile> all=new ArrayList<>(concealed);for(Meld meld:melds)all.addAll(meld.tiles());
        Set<String> ids=new HashSet<>();int[] counts=new int[34];
        for(Tiles.Tile tile:all)if(tile.flower()||!ids.add(tile.id())||++counts[tile.type()]>4)return Optional.empty();
        int[] hidden=Tiles.counts(concealed);boolean closed=melds.stream().noneMatch(Meld::open);List<Score> candidates=new ArrayList<>();
        if(melds.isEmpty()&&HandSolver.orphans(hidden,0)){
            List<Yaku> yaku=yakuman(counts,List.of(),-1,false,true,context);
            boolean thirteen=hidden[context.winningTile().type()]==2;
            yaku.add(new Yaku(thirteen?"KOKUSHI_MUSOU_13_WAIT":"KOKUSHI_MUSOU",0,thirteen&&context.options().doubleYakuman()?2:1));
            candidates.add(naturalScore(yaku));
        }
        if(melds.isEmpty()&&HandSolver.sevenPairs(hidden,0,false)){
            Score candidate=evaluate(all,counts,List.of(),-1,2,true,true,true,true,context);if(candidate!=null)candidates.add(candidate);
        }
        for(HandSolver.Shape shape:HandSolver.solve(hidden,0,4-melds.size())){
            for(int winning=-1;winning<shape.groups().size();winning++){
                int type=context.winningTile().type();
                if(winning<0?shape.pair()!=type:!contains(shape.groups().get(winning),type))continue;
                List<Group> groups=new ArrayList<>();
                for(int i=0;i<shape.groups().size();i++){
                    var group=shape.groups().get(i);groups.add(new Group(group.sequence(),group.type(),!context.flags().tsumo()&&i==winning&&!group.sequence(),false));
                }
                for(Meld meld:melds)groups.add(new Group(meld.kind()==Meld.Kind.SEQUENCE,meld.type(),meld.open(),meld.kind()==Meld.Kind.QUAD));
                int wait=winning<0?2:waitFu(shape.groups().get(winning),type);
                Score candidate=evaluate(all,counts,groups,shape.pair(),wait,winning<0,false,melds.isEmpty(),closed,context);
                if(candidate!=null)candidates.add(candidate);
            }
        }
        return candidates.stream().max(Comparator.comparingInt(Score::basePoints).thenComparingInt(Score::yakuman).thenComparingInt(Score::han).thenComparingInt(Score::fu));
    }
    private static boolean contains(HandSolver.Group group,int type){return group.sequence()?type>=group.type()&&type<=group.type()+2:group.type()==type;}
    private static int waitFu(HandSolver.Group group,int type){
        if(!group.sequence())return 0;int start=group.type();
        return type==start+1||start%9==0&&type==start+2||start%9==6&&type==start?2:0;
    }
    private static Score evaluate(List<Tiles.Tile> tiles,int[] counts,List<Group> groups,int pair,int wait,boolean pairWait,boolean sevenPairs,boolean noMelds,boolean closed,Context c){
        List<Yaku> natural=yakuman(counts,groups,pair,pairWait,noMelds,c);if(!natural.isEmpty())return naturalScore(natural);
        List<Yaku> yaku=new ArrayList<>();Flags f=c.flags();boolean riichi=closed&&(f.riichi()||f.doubleRiichi());
        if(riichi)add(yaku,f.doubleRiichi()?"DOUBLE_RIICHI":"RIICHI",f.doubleRiichi()?2:1);
        if(riichi&&f.ippatsu())add(yaku,"IPPATSU",1);
        if(closed&&f.tsumo())add(yaku,"MENZEN_TSUMO",1);
        if(all(counts,type->!Tiles.terminalOrHonor(type))&&(closed||c.options().openTanyao()))add(yaku,"TANYAO",1);
        if(f.tsumo()&&f.rinshan()&&groups.stream().anyMatch(Group::quad))add(yaku,"RINSHAN_KAIHOU",1);
        if(!f.tsumo()&&f.chankan())add(yaku,"CHANKAN",1);
        if(f.tsumo()&&f.haitei()&&!f.rinshan())add(yaku,"HAITEI_RAOYUE",1);
        if(!f.tsumo()&&f.houtei()&&!f.chankan())add(yaku,"HOUTEI_RAOYU",1);
        int suit=suit(counts);boolean honors=hasHonors(counts);
        if(suit>=0)add(yaku,honors?"HONITSU":"CHINITSU",honors?(closed?3:2):(closed?6:5));
        if(all(counts,Tiles::terminalOrHonor))add(yaku,"HONROUTOU",2);
        boolean pinfu=false;
        if(sevenPairs)add(yaku,"CHIITOITSU",2);
        else{
            int[] sequences=new int[27];boolean[] triplets=new boolean[34];int tripletCount=0,concealedCount=0,quads=0;
            for(Group group:groups)if(group.sequence())sequences[group.type()]++;else{
                triplets[group.type()]=true;tripletCount++;if(!group.open())concealedCount++;if(group.quad())quads++;
            }
            pinfu=closed&&tripletCount==0&&pairFu(pair,c)==0&&wait==0;
            if(pinfu)add(yaku,"PINFU",1);
            if(closed){int repeated=Arrays.stream(sequences).map(count->count/2).sum();if(repeated>=2)add(yaku,"RYANPEIKOU",3);else if(repeated==1)add(yaku,"IIPEIKOU",1);}
            for(int number=0;number<7;number++)if(sequences[number]>0&&sequences[number+9]>0&&sequences[number+18]>0){add(yaku,"SANSHOKU_DOUJUN",closed?2:1);break;}
            for(int start=0;start<27;start+=9)if(sequences[start]>0&&sequences[start+3]>0&&sequences[start+6]>0){add(yaku,"ITTSUU",closed?2:1);break;}
            for(int number=0;number<9;number++)if(triplets[number]&&triplets[number+9]&&triplets[number+18]){add(yaku,"SANSHOKU_DOUKOU",2);break;}
            if(tripletCount==4)add(yaku,"TOITOI",2);if(concealedCount>=3)add(yaku,"SANANKOU",2);if(quads==3)add(yaku,"SANKANTSU",2);
            int dragons=0;for(int type=31;type<34;type++)if(triplets[type])dragons++;
            int valued=dragons+(triplets[c.seatWind()]?1:0)+(triplets[c.roundWind()]?1:0);if(valued>0)add(yaku,"YAKUHAI",valued);
            if(dragons==2&&pair>=31)add(yaku,"SHOUSANGEN",2);
            if(tripletCount<4&&Tiles.terminalOrHonor(pair)&&groups.stream().allMatch(g->g.sequence()?g.type()%9==0||g.type()%9==6:Tiles.terminalOrHonor(g.type())))
                add(yaku,honors?"CHANTA":"JUNCHAN",honors?(closed?2:1):(closed?3:2));
        }
        if(yaku.isEmpty())return null;
        int dora=bonus(tiles,c.doraIndicators()),ura=riichi?bonus(tiles,c.uraIndicators()):0,red=(int)tiles.stream().filter(Tiles.Tile::red).count();
        if(dora>0)add(yaku,"DORA",dora);if(ura>0)add(yaku,"URA_DORA",ura);if(red>0)add(yaku,"AKA_DORA",red);
        int han=yaku.stream().mapToInt(Yaku::han).sum();int fu=sevenPairs?25:fu(groups,pair,wait,closed,pinfu,c);
        return new Score(han,fu,0,basePoints(han,fu,c.options()),yaku);
    }
    private static List<Yaku> yakuman(int[] counts,List<Group> groups,int pair,boolean pairWait,boolean noMelds,Context c){
        List<Yaku> yaku=new ArrayList<>();boolean twice=c.options().doubleYakuman();
        if(noMelds&&c.flags().tsumo()){
            if(c.flags().tenhou()&&c.seatWind()==27)yaku.add(new Yaku("TENHOU",0,1));
            if(c.flags().chiihou()&&c.seatWind()!=27)yaku.add(new Yaku("CHIIHOU",0,1));
        }
        if(all(counts,type->type>=27))yaku.add(new Yaku("TSUUIISOU",0,1));
        if(all(counts,type->type<27&&(type%9==0||type%9==8)))yaku.add(new Yaku("CHINROUTOU",0,1));
        if(all(counts,type->type==19||type==20||type==21||type==23||type==25||type==32))yaku.add(new Yaku("RYUUIISOU",0,1));
        int suit=suit(counts);
        if(noMelds&&suit>=0&&!hasHonors(counts)&&nineGates(counts,suit)){
            int[] before=counts.clone();before[c.winningTile().type()]--;boolean pure=true;
            for(int rank=0;rank<9;rank++)pure&=before[suit*9+rank]==(rank==0||rank==8?3:1);
            yaku.add(new Yaku(pure?"JUNSEI_CHUUREN_POUTOU":"CHUUREN_POUTOU",0,pure&&twice?2:1));
        }
        if(groups.size()==4){
            int hidden=0,quads=0,dragons=0,winds=0;
            for(Group g:groups)if(!g.sequence()){
                if(!g.open())hidden++;if(g.quad())quads++;if(g.type()>=31)dragons++;else if(g.type()>=27)winds++;
            }
            if(hidden==4)yaku.add(new Yaku(pairWait?"SUUANKOU_TANKI":"SUUANKOU",0,pairWait&&twice?2:1));
            if(quads==4)yaku.add(new Yaku("SUUKANTSU",0,1));
            if(dragons==3)yaku.add(new Yaku("DAISANGEN",0,1));
            if(winds==3&&pair>=27&&pair<=30)yaku.add(new Yaku("SHOUSUUSHII",0,1));
            if(winds==4)yaku.add(new Yaku("DAISUUSHII",0,twice?2:1));
        }
        return yaku;
    }
    private static boolean nineGates(int[] counts,int suit){
        if(counts[suit*9]<3||counts[suit*9+8]<3)return false;
        for(int rank=1;rank<8;rank++)if(counts[suit*9+rank]==0)return false;return true;
    }
    private static boolean all(int[] counts,java.util.function.IntPredicate predicate){for(int type=0;type<34;type++)if(counts[type]>0&&!predicate.test(type))return false;return true;}
    private static boolean hasHonors(int[] counts){for(int type=27;type<34;type++)if(counts[type]>0)return true;return false;}
    private static int suit(int[] counts){
        int suit=-1;for(int type=0;type<27;type++)if(counts[type]>0){if(suit>=0&&suit!=type/9)return -1;suit=type/9;}return suit;
    }
    private static void add(List<Yaku> yaku,String name,int han){yaku.add(new Yaku(name,han,0));}
    private static Score naturalScore(List<Yaku> yaku){int multiple=yaku.stream().mapToInt(Yaku::yakuman).max().orElseThrow();return new Score(0,0,multiple,8000*multiple,yaku);}
    private static int pairFu(int pair,Context c){return pair>=31||pair==c.seatWind()||pair==c.roundWind()?2:0;}
    private static int fu(List<Group> groups,int pair,int wait,boolean closed,boolean pinfu,Context c){
        if(pinfu)return c.flags().tsumo()?20:30;int fu=20+pairFu(pair,c)+wait+(c.flags().tsumo()?2:closed?10:0);
        for(Group g:groups)if(!g.sequence())fu+=(g.open()?2:4)*(g.quad()?4:1)*(Tiles.terminalOrHonor(g.type())?2:1);
        return Math.max(30,(fu+9)/10*10);
    }
    private static int bonus(List<Tiles.Tile> tiles,List<Tiles.Tile> indicators){
        int count=0;for(Tiles.Tile indicator:indicators){int type=Tiles.next(indicator.type());for(Tiles.Tile tile:tiles)if(tile.type()==type)count++;}return count;
    }
    private static int basePoints(int han,int fu,Options options){
        if(han>=13&&options.countedYakuman())return 8000;if(han>=11)return 6000;if(han>=8)return 4000;if(han>=6)return 3000;if(han>=5)return 2000;
        if(options.kiriage()&&(han==4&&fu==30||han==3&&fu==60))return 2000;return Math.min(2000,fu*(1<<(han+2)));
    }
}
