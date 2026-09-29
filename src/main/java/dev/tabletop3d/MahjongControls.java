package dev.tabletop3d;

import java.util.*;

/** Groups the current seat's legal calls without choosing a tile combination for them. */
final class MahjongControls {
    private MahjongControls() {}
    static Map<String,List<String>> groups(List<String> legal) {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(String group:List.of("ron","tsumo","kan","pon","chi","riichi","pass","next-hand","exchange-confirm","missing-m","missing-p","missing-s")){
            List<String> choices=legal.stream().filter(action->{
                if(group.startsWith("missing-"))return action.equals("missing:"+group.substring(8));
                String verb=action.split(":",2)[0];return group.equals("kan")?verb.startsWith("kan-"):verb.equals(group);
            }).toList();
            if(!choices.isEmpty())result.put(group,choices);
        }
        // Declining an optional self-turn call leaves the turn available for a discard.
        if(!result.isEmpty()&&!result.containsKey("pass")&&legal.stream().anyMatch(a->a.startsWith("discard:")))
            result.put("dismiss",List.of());
        return Collections.unmodifiableMap(result);
    }
}
