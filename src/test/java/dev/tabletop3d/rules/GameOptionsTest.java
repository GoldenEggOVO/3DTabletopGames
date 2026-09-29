package dev.tabletop3d.rules;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameOptionsTest {
    @Test void profileSwitchResetsRegionalRulesAndKeepsCommonMatchSettings(){
        Map<String,String> settings=Map.of("profile","guangdong","seven-pairs","false","rounds","8","starting-points","30000");
        var profile=GameOptions.forGame("mahjong",settings).getFirst();
        Map<String,String> changed=GameOptions.change("mahjong",settings,profile);
        assertEquals(Map.of("profile","fuzhou","rounds","8","starting-points","30000"),changed);
        assertFalse(GameOptions.forGame("mahjong",changed).stream().anyMatch(o->o.key().equals("seven-pairs")));
        assertThrows(UnsupportedOperationException.class,()->changed.put("profile","riichi"));
    }
    @Test void defaultsAreCanonicalAndCrossRegionOptionsCannotSilentlyAffectAnotherRuleset(){
        assertEquals(Map.of(),GameOptions.validate("mahjong",Map.of("profile","riichi","red-five-count","3")));
        assertEquals(Map.of("profile","guangdong"),GameOptions.validate("mahjong",Map.of("profile","guangdong","rounds","1")));
        assertThrows(IllegalArgumentException.class,()->GameOptions.validate("mahjong",Map.of("profile","taiwan","red-five-count","3")));
        assertThrows(IllegalArgumentException.class,()->GameOptions.validate("mahjong",Map.of("profile","unknown")));
        assertThrows(IllegalArgumentException.class,()->GameOptions.validate("mahjong",Map.of("starting-points","-1")));
        assertThrows(IllegalArgumentException.class,()->GameOptions.validate("mahjong",Map.of("profile","guangdong","standard-win","false","seven-pairs","false","thirteen-orphans","false")));
    }
    @Test void everyMenuOptionCanBeCycledAndSerializedWithoutChangingItsValue(){
        for(String profile:List.of("riichi","guangdong","fuzhou","sichuan","qinhuangdao","taiwan")){
            Map<String,String> initial=Map.of("profile",profile);
            for(var option:GameOptions.forGame("mahjong",initial)){
                Map<String,String> settings=initial;
                for(int cycle=0;cycle<option.values().size();cycle++){
                    String next=option.next(settings);settings=GameOptions.change("mahjong",settings,option);
                    assertEquals(next,option.value(settings));
                    assertEquals(settings,GameOptions.validate("mahjong",settings));
                }
            }
        }
    }
}
