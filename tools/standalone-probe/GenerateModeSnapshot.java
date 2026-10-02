import dev.tabletop3d.internal.gson.*;
import dev.tabletop3d.rules.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Extend a synthetic legacy snapshot with rule variants; never reads a server data directory. */
public final class GenerateModeSnapshot {
    private static JsonArray rooms;
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: GenerateModeSnapshot <synthetic-input.json> <new-output.json>");
        Path input=Path.of(args[0]).toAbsolutePath(),output=Path.of(args[1]).toAbsolutePath();
        if(input.equals(output)||Files.exists(output))throw new IllegalArgumentException("Output must be a new fixture file");
        JsonObject root=JsonParser.parseString(Files.readString(input)).getAsJsonObject();rooms=root.getAsJsonArray("rooms");
        if(rooms.size()!=12||root.getAsJsonObject("returns").size()!=0)throw new IllegalArgumentException("Expected the synthetic 12-game legacy fixture");
        for(JsonElement element:rooms)for(JsonElement seat:element.getAsJsonObject().getAsJsonArray("seats"))
            if(!seat.getAsJsonObject().get("bot").getAsBoolean())throw new IllegalArgumentException("Human seats are not fixture data");
        add("ludo",4,Map.of("start","three","blocking","on","goal","over","finish","all"));
        add("gomoku",2,Map.of("double-three","forbid","double-four","forbid","overline","forbid"));
        add("checkers",3,Map.of("jump-own","forbid","other-camps","forbid","finish","all"));
        add("chess",2,Map.of("first","opponent"));
        add("color-eight",4,Map.of());add("color-eight",5,Map.of());
        for(String profile:List.of("riichi","guangdong","sichuan","taiwan")){
            Map<String,String> options=new LinkedHashMap<>(Map.of("profile",profile,"rounds","1"));
            options.putAll(switch(profile){
                case "riichi"->Map.of("red-five-count","4","open-tanyao","false");
                case "guangdong"->Map.of("ron-payment","2000","seven-pairs","false");
                case "sichuan"->Map.of("exchange-direction","ACROSS","max-fan","5");
                default->Map.of("minimum-tai","2","max-tai","32");
            });add("mahjong",4,options);
        }
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
        System.out.println("MODE_SNAPSHOT_CREATED rooms="+rooms.size()+" output="+output);
    }
    private static void add(String kind,int capacity,Map<String,String> supplied){
        Map<String,String> options=GameOptions.validate(kind,supplied);int index=rooms.size();long seed=9000L+index;
        UUID id=UUID.nameUUIDFromBytes(("modes-fixture:"+index).getBytes(StandardCharsets.UTF_8));BoardGame game=GameFactory.create(kind,capacity,seed,options);
        JsonArray history=new JsonArray();Random random=new Random(seed);
        for(int step=0;step<40&&!game.finished();step++){
            int seat=game.currentPlayer();List<String> legal=game.legalActions(seat);if(legal.isEmpty())throw new IllegalStateException(kind+" stalled");
            String action=null;for(String priority:List.of("ron","tsumo","exchange-confirm","declare"))if(legal.contains(priority)){action=priority;break;}
            if(action==null){List<String> candidates=legal.stream().filter(a->!a.startsWith("exchange-remove:")).toList();action=candidates.get(random.nextInt(candidates.size()));}
            game.apply(seat,action);JsonObject event=new JsonObject();event.addProperty("seat",seat);event.addProperty("action",action);history.add(event);
        }
        JsonObject room=new JsonObject();room.addProperty("id",id.toString());room.addProperty("kind",kind);room.addProperty("capacity",capacity);
        room.addProperty("seed",seed);room.addProperty("table",index);room.addProperty("rulesVersion",kind.equals("color-eight")?2:1);room.add("options",new Gson().toJsonTree(options));
        room.addProperty("sideTray",kind.equals("ludo"));room.addProperty("anchorWorld",rooms.get(0).getAsJsonObject().get("anchorWorld").getAsString());
        room.addProperty("anchorX",(index%4)*12);room.addProperty("anchorY",83);room.addProperty("anchorZ",(index/4)*12);
        room.addProperty("phase",game.finished()?"FINISHED":"PLAYING");room.addProperty("completed",game.finished());room.addProperty("result",game.finished()?game.outcome():"");
        room.addProperty("revision",history.size());room.add("history",history);JsonArray seats=new JsonArray();
        for(int i=0;i<capacity;i++){JsonObject seat=new JsonObject();seat.addProperty("id",UUID.nameUUIDFromBytes((id+":"+i).getBytes(StandardCharsets.UTF_8)).toString());seat.addProperty("name","Fixture Bot "+(i+1));seat.addProperty("bot",true);seats.add(seat);}
        room.addProperty("owner",seats.get(0).getAsJsonObject().get("id").getAsString());
        if(options.getOrDefault("first","host").equals("opponent")){JsonElement first=seats.get(0);seats.set(0,seats.get(1));seats.set(1,first);}
        room.add("seats",seats);rooms.add(room);
    }
}
