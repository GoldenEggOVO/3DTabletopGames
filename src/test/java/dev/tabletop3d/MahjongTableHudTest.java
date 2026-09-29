package dev.tabletop3d;

import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.ui.MessageText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MahjongTableHudTest {
    @BeforeEach void setup(){MockBukkit.mock();}
    @AfterEach void cleanup(){MockBukkit.unmock();}

    @Test void actualWallCountAndPublicDiscardUpdateWithoutReadingHands() {
        Fixture f=new Fixture();
        for(int seat=0;seat<4;seat++)assertEquals("69",MessageText.plain(f.texts.get(f.count(seat))));
        when(f.game.deckSize()).thenReturn(68);
        when(f.game.publicInfo()).thenReturn(Map.of("round","5","lastDiscardTile","m9","lastDiscardBy","2"));
        f.room.revision++;f.hud.tick(1_000);
        for(int seat=0;seat<4;seat++)assertEquals("68",MessageText.plain(f.texts.get(f.count(seat))));
        assertTrue(f.status().contains("South 1"));
        assertEquals("Player 2 discarded 9 Characters",MessageText.plain(f.texts.get(f.discard())));
        verify(f.game,never()).hand(anyInt());verify(f.game,never()).handSize(anyInt());
        verify(f.game,never()).legalActions(anyInt());
        assertEquals(23,f.spawned.size());
    }

    @Test void countdownUsesElapsedTimeCeilingAndReusesDisplays() {
        Fixture f=new Fixture();f.room.changed=1_000;
        f.hud.tick(1_000);assertTrue(f.status().contains("5s"));
        f.hud.tick(2_001);assertTrue(f.status().contains("4s"));
        clearInvocations(f.statusEntity());
        f.hud.tick(2_100);verify(f.statusEntity(),never()).text(any(Component.class));
        f.hud.tick(5_001);assertTrue(f.status().contains("1s"));
        f.hud.tick(9_000);assertTrue(f.status().contains("0s"));
        assertEquals(23,f.spawned.size());
    }

    @Test void pauseBusyAndUndoSuppressCountdownAndBusyKeepsSafeSnapshot() {
        Fixture f=new Fixture();f.room.changed=1_000;
        f.room.phase=Room.Phase.PAUSED;f.hud.tick(2_000);assertTrue(f.status().contains("Paused"));
        f.room.phase=Room.Phase.PLAYING;f.room.busy=true;f.room.revision++;
        clearInvocations(f.game);f.hud.tick(2_000);
        assertTrue(f.status().contains("Paused"));verifyNoInteractions(f.game);
        f.room.busy=false;f.room.undo=new RoundActions.Undo(UUID.randomUUID(),0,10_000,Set.of());
        f.hud.tick(2_000);assertTrue(f.status().contains("Paused"));
        f.room.undo=null;f.hud.tick(2_000);assertTrue(f.status().contains("4s"));
        f.room.phase=Room.Phase.FINISHED;f.hud.tick(2_000);assertTrue(f.status().contains("Round finished"));
    }

    @Test void sticksAppearOnlyForDeclaredSeatsAndCarriedDepositsRemainText() {
        Fixture f=new Fixture();
        for(int seat=0;seat<4;seat++)assertEquals(0,f.transforms.get(f.stick(seat)).getScale().length());
        when(f.game.publicInfo()).thenReturn(Map.of("riichi.1","true","riichiSticks","3"));
        f.room.revision++;f.hud.tick(1_000);
        assertTrue(f.transforms.get(f.stick(1)).getScale().length()>0);
        for(int seat:new int[]{0,2,3})assertEquals(0,f.transforms.get(f.stick(seat)).getScale().length());
        assertTrue(f.status().contains("Riichi sticks: 3"));
        when(f.game.publicInfo()).thenReturn(Map.of("round","2","riichiSticks","3"));
        f.room.revision++;f.hud.tick(1_000);
        assertEquals(0,f.transforms.get(f.stick(1)).getScale().length());
        assertTrue(f.status().contains("Riichi sticks: 3"));
    }

    @Test void cleanupRemovesEveryEntityOnceAndTickAfterCloseDoesNothing() {
        Fixture f=new Fixture();
        for(Entity entity:f.spawned) {
            verify(entity,never()).setVisibleByDefault(false);
            verify(entity.getPersistentDataContainer()).set(any(),eq(org.bukkit.persistence.PersistentDataType.STRING),eq(f.room.id+"|@board"));
        }
        f.hud.close();f.hud.close();clearInvocations(f.game);f.hud.tick(1_000);
        assertTrue(f.hud.entities.isEmpty());verifyNoInteractions(f.game);
        for(Entity entity:f.spawned)verify(entity).remove();
    }

    @Test void lobbyAndStartingClearFloatingStatusAndResumeExistingLabels() {
        Fixture f=new Fixture();
        for(Room.Phase phase:List.of(Room.Phase.LOBBY,Room.Phase.STARTING)) {
            f.room.phase=phase;f.hud.tick(1_000);
            assertEquals("",f.status());assertEquals("",MessageText.plain(f.texts.get(f.discard())));
            assertEquals("69",MessageText.plain(f.texts.get(f.count(0))));
        }
        f.room.phase=Room.Phase.PLAYING;f.hud.tick(1_000);
        assertTrue(f.status().contains("East 1"));
        assertFalse(MessageText.plain(f.texts.get(f.discard())).isEmpty());
        assertEquals(23,f.spawned.size());
    }

    @Test void enlargedCountsFitCenterPadAndWindsSitToEachSeatsRight() {
        Fixture f=new Fixture();
        assertEquals(.6f,f.transforms.get(f.spawned.getFirst()).getScale().x,1e-6);
        assertTrue(f.transforms.get(f.count(0)).getScale().x>.2f);
        double[][] corners={{.95,.82},{.82,-.95},{-.95,-.82},{-.82,.95}};
        for(int seat=0;seat<4;seat++) {
            Location at=f.locations.get(f.wind(seat));
            assertEquals(corners[seat][0],at.getX(),1e-6);
            assertEquals(corners[seat][1],at.getZ(),1e-6);
            verify(f.wind(seat)).setRotation((float)(-90*seat),-90f);
        }
    }

    @Test void seatWindsFollowDealerAndScoresUsePublicSnapshot() {
        Fixture f=new Fixture();
        when(f.game.publicInfo()).thenReturn(Map.of("dealer","2","score.0","24000","score.1","30000","score.2","-1000","score.3","47000"));
        f.room.revision++;f.hud.tick(1_000);
        String[] winds={"西","北","東","南"},scores={"24000","30000","-1000","47000"};
        for(int seat=0;seat<4;seat++) {
            assertEquals(winds[seat],MessageText.plain(f.texts.get(f.wind(seat))));
            assertEquals(seat==2?NamedTextColor.RED:NamedTextColor.WHITE,f.texts.get(f.wind(seat)).color());
            assertEquals(scores[seat],MessageText.plain(f.texts.get(f.score(seat))));
        }
        f.room.busy=true;f.room.revision++;
        when(f.game.publicInfo()).thenReturn(Map.of("dealer","0","score.0","999"));
        clearInvocations(f.game);f.hud.tick(2_000);
        assertEquals("24000",MessageText.plain(f.texts.get(f.score(0))));
        assertEquals("東",MessageText.plain(f.texts.get(f.wind(2))));
        verifyNoInteractions(f.game);
    }

    @Test void fixedScoresFaceTableFromBehindEachSeatWithNativeBackfaceCulling() {
        Fixture f=new Fixture();
        for(int seat=0;seat<4;seat++) {
            verify(f.score(seat)).setBillboard(Display.Billboard.FIXED);
            verify(f.score(seat)).setRotation(180f-90f*seat,0f);
            verify(f.score(seat)).setSeeThrough(false);
            Location at=f.locations.get(f.score(seat));
            assertEquals(2.8,Math.hypot(at.getX(),at.getZ()),1e-6);
            assertEquals(82.4,at.getY(),1e-6);
            double yaw=Math.toRadians(at.getYaw());
            assertTrue(Math.sin(yaw)*at.getX()-Math.cos(yaw)*at.getZ()>0);
        }
    }

    @Test void regionalProfilesUseRoundNumberAndFinishedRiichiDoesNotShowNextWind() {
        Fixture f=new Fixture();
        when(f.game.publicInfo()).thenReturn(Map.of("profile","sichuan","round","5"));
        f.room.revision++;f.hud.tick(1_000);
        assertTrue(f.status().contains("Round 5"));assertFalse(f.status().contains("South"));
        when(f.game.publicInfo()).thenReturn(Map.of("profile","riichi","round","5","rounds","4"));
        f.room.phase=Room.Phase.FINISHED;f.room.revision++;f.hud.tick(1_000);
        assertTrue(f.status().contains("East 4"));assertFalse(f.status().contains("South"));
    }

    private static final class Fixture {
        final Tabletop3D plugin=mock(Tabletop3D.class);
        final World world=mock(World.class);
        final HandGame game=mock(HandGame.class);
        final Room room=new Room(UUID.randomUUID(),"mahjong",4,0,0);
        final List<Entity> spawned=new ArrayList<>();
        final Map<TextDisplay,Component> texts=new HashMap<>();
        final Map<Display,Transformation> transforms=new HashMap<>();
        final Map<Entity,Location> locations=new HashMap<>();
        final MahjongTableHud hud;
        Fixture() {
            when(world.spawn(any(Location.class),any(Class.class),any(Consumer.class))).thenAnswer(inv->{
                Entity entity=mock((Class<? extends Entity>)inv.getArgument(1));spawned.add(entity);
                locations.put(entity,((Location)inv.getArgument(0)).clone());
                when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
                doAnswer(a->{transforms.put((Display)entity,a.getArgument(0));return null;})
                    .when((Display)entity).setTransformation(any(Transformation.class));
                if(entity instanceof TextDisplay text)doAnswer(a->{texts.put(text,a.getArgument(0));return null;}).when(text).text(any(Component.class));
                ((Consumer<Entity>)inv.getArgument(2)).accept(entity);return entity;
            });
            for(int seat=0;seat<4;seat++)room.join(UUID.randomUUID(),"Player "+seat);
            room.board=game;room.phase=Room.Phase.PLAYING;
            when(game.publicInfo()).thenReturn(Map.of("round","1"));
            when(game.deckSize()).thenReturn(69);when(game.currentPlayer()).thenReturn(0);
            when(plugin.turnWaitMillis(room)).thenReturn(5_000L);
            hud=new MahjongTableHud(plugin,room,new Location(world,0,81,0),new NamespacedKey("test","table"));
        }
        TextDisplay count(int seat){return (TextDisplay)spawned.get(1+seat*3);}
        BlockDisplay stick(int seat){return (BlockDisplay)spawned.get(2+seat*3);}
        TextDisplay statusEntity(){return (TextDisplay)spawned.get(13);}
        TextDisplay discard(){return (TextDisplay)spawned.get(14);}
        TextDisplay wind(int seat){return (TextDisplay)spawned.get(15+seat*2);}
        TextDisplay score(int seat){return (TextDisplay)spawned.get(16+seat*2);}
        String status(){return MessageText.plain(texts.get(statusEntity()));}
    }
}
