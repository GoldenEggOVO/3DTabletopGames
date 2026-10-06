package dev.tabletop3d.rules.mahjong;

import java.util.List;
import java.util.Map;

public final class GuangdongRules {
    private final boolean standard,sevenPairs,orphans;
    public final int ronPayment,tsumoPayment;
    public GuangdongRules(Map<String,String> options) {
        standard=flag(options,"standard-win",true);sevenPairs=flag(options,"seven-pairs",true);orphans=flag(options,"thirteen-orphans",true);
        if(!standard&&!sevenPairs&&!orphans)throw new IllegalArgumentException("At least one Guangdong winning shape is required");
        ronPayment=number(options,"ron-payment",1000);tsumoPayment=number(options,"tsumo-payment-per-opponent",500);
    }
    public String win(List<Tiles.Tile> concealed,List<Meld> melds) {
        if(melds.size()>4)return null;
        int[] counts=Tiles.counts(concealed),total=counts.clone();
        for(Meld meld:melds)for(Tiles.Tile tile:meld.tiles())total[tile.type()]++;
        for(int count:total)if(count>4)return null;
        if(melds.isEmpty()&&orphans&&HandSolver.orphans(counts,0))return "THIRTEEN_ORPHANS";
        if(melds.isEmpty()&&sevenPairs&&HandSolver.sevenPairs(counts,0,true))return "SEVEN_PAIRS";
        return standard&&!HandSolver.solve(counts,0,4-melds.size()).isEmpty()?"STANDARD":null;
    }
    private static String option(Map<String,String> options,String key,String fallback){return options.getOrDefault(key,fallback);}
    private static boolean flag(Map<String,String> options,String key,boolean fallback) {
        return switch(option(options,key,Boolean.toString(fallback))){case "true"->true;case "false"->false;default->throw new IllegalArgumentException("Invalid Guangdong flag: "+key);};
    }
    private static int number(Map<String,String> options,String key,int fallback) {
        int value=Integer.parseInt(option(options,key,Integer.toString(fallback)));
        if(value<1||value>1_000_000)throw new IllegalArgumentException("Invalid Guangdong payment: "+key);return value;
    }
}
