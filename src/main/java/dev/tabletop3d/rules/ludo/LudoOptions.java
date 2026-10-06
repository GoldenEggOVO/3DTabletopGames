package dev.tabletop3d.rules.ludo;

public record LudoOptions(boolean autoFirst,boolean blocking,boolean exactFinish,boolean allPlaces,int startingRolls) {
    public static final LudoOptions DEFAULT=new LudoOptions(true,false,true,false);
    public LudoOptions(boolean autoFirst,boolean blocking,boolean exactFinish,boolean allPlaces){this(autoFirst,blocking,exactFinish,allPlaces,1);}
    public LudoOptions { if(startingRolls!=1&&startingRolls!=3)throw new IllegalArgumentException("Starting rolls must be one or three"); }
}
