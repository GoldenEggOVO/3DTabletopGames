package dev.tabletop3d;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowMenuTest {
    @Test void everyMenuUsesTheSameDedicatedCloseButtonAndPreservesBackStyle() throws Exception {
        for(String page:GameMenuLayouts.PAGES){
            var config=new YamlConfiguration();
            try(var input=WindowMenuTest.class.getResourceAsStream("/menus/"+page+".yml")){
                assertNotNull(input,page);config.loadFromString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            }
            config.set("Bottom.exit.tooltip",List.of("Old page-specific close hint"));
            String backStyle=config.getString("Bottom.buttons.back.text");
            UUID token=UUID.randomUUID();
            var rendered=GameMenuLayouts.render(config,"Title","",List.of(
                new GameMenus.Button("back","Back",()->{}),new GameMenus.Button("close","Close Menu",()->{})),token);
            assertEquals("<dark_gray>[ <red>Close Menu <dark_gray>]",rendered.config().getString("Bottom.exit.text"),page);
            assertEquals(230,rendered.config().getInt("Bottom.exit.width"),page);
            assertFalse(rendered.config().contains("Bottom.exit.tooltip"),page);
            assertEquals(List.of("3dtabletop:"+token+" 1"),rendered.config().getStringList("Bottom.exit.actions"),page);
            assertEquals(backStyle.replace("@label@","Back"),rendered.config().getString("Bottom.buttons.slot0.text"),page);
        }
    }
    @Test void closeInvalidatesTheMenuSessionWithoutLeavingTheRoom() throws Exception {
        var f=new MenuFlowTest.Fixture();Room room=f.addRoom(0);room.join(f.player.getUniqueId(),"Owner");
        f.menus.room(f.player,room);
        f.buttons.stream().filter(button->button.id().equals("close")).findFirst().orElseThrow().action().run();
        var field=GameMenus.class.getDeclaredField("sessions");field.setAccessible(true);
        assertFalse(((Map<?,?>)field.get(f.menus)).containsKey(f.player.getUniqueId()));
        assertSame(room,f.plugin.room(f.player));verify(f.plugin,never()).leave(f.player);
    }
    @Test void roomMenusDoNotTranslateOrParseHumanNames() {
        var plugin=mock(Tabletop3D.class);when(plugin.allowed(any())).thenReturn(true);
        var player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());when(player.getWorld()).thenReturn(world);
        var room=new Room(UUID.randomUUID(),"chess",2,0,0);
        String name="<red>准备&c陪练";room.join(id,name);
        // The mocked plugin has no initialized fields; provide the live room registry.
        try {var field=Tabletop3D.class.getDeclaredField("rooms");field.setAccessible(true);field.set(plugin,new LinkedHashMap<>(Map.of(room.id,room)));}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        var view=mock(BoardWindow.class);
        when(view.render(anyString(),any(net.kyori.adventure.text.Component.class),any(net.kyori.adventure.text.Component.class),anyList(),any())).thenAnswer(invocation ->
            GameMenuLayouts.render(new YamlConfiguration(),(net.kyori.adventure.text.Component)invocation.getArgument(1),(net.kyori.adventure.text.Component)invocation.getArgument(2),invocation.getArgument(3),invocation.getArgument(4)));
        when(view.open(eq(player),any(),anyString())).thenReturn(true);
        new GameMenus(plugin,view).room(player,room);
        var body=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
        verify(view).render(eq("room"),any(),body.capture(),anyList(),any());
        assertTrue(dev.tabletop3d.ui.MessageText.plain(body.getValue()).contains(name),dev.tabletop3d.ui.MessageText.plain(body.getValue()));
    }
    @Test void namedTitleAndBodyKeepTheirOwnLanguageAndStyles() {
        var config=new YamlConfiguration();config.set("Title","<white>@title@");
        config.set("Body.content.text","<gray>@description@");
        var title=dev.tabletop3d.ui.MessageText.render("<red>棋盘游戏</red>");
        var body=dev.tabletop3d.ui.MessageText.render("<gold>确认离开</gold>");
        var rendered=GameMenuLayouts.render(config,title,body,List.of(),UUID.randomUUID());
        var actualTitle=(net.kyori.adventure.text.Component)rendered.config().get("Title-component");
        var actualBody=(net.kyori.adventure.text.Component)rendered.config().get("Body.content.component");
        assertEquals("棋盘游戏",dev.tabletop3d.ui.MessageText.plain(actualTitle));
        assertEquals("确认离开",dev.tabletop3d.ui.MessageText.plain(actualBody));
        assertTrue(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(actualTitle).contains("§c棋盘游戏"));
    }
    @Test void namedButtonValuesStayLiteralThroughLegacyAndMiniMessageLayouts() {
        for(String style:List.of("&a@label@","<green>@label@</green>")) {
            var config=new YamlConfiguration();config.set("Bottom.buttons.entry.text",style);
            String name="<red>玩家&c";
            var rendered=GameMenuLayouts.render(config,"Title","",List.of(new GameMenus.Button("entry",
                dev.tabletop3d.ui.MessageText.render("Next: {name}","name",name),()->{})),UUID.randomUUID());
            var component=(net.kyori.adventure.text.Component)rendered.config().get("Bottom.buttons.slot0.component");
            assertEquals("Next: "+name,dev.tabletop3d.ui.MessageText.plain(component));
        }
    }
    @Test void defaultDialogTextIsEnglish() {
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(BoardWindow.text("棋盘游戏"));
        assertEquals("3D Tabletop Games", title);
    }
    @Test void confirmLeaveButtonUsesEnglishAfterLayoutRemovesSourceColor() {
        var config = new YamlConfiguration();
        config.set("Bottom.buttons.entry.text", "&f@label@");
        var rendered = GameMenuLayouts.render(config, "Leave Room", "", List.of(
            new GameMenus.Button("§c确认离开", () -> {})), UUID.randomUUID());
        String label = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(BoardWindow.text(rendered.config().getString("Bottom.buttons.slot0.text")));
        assertEquals("Confirm Leave", label);
    }
    @Test void mainCatalogUsesBrandedTitle() {
        var plugin = mock(Tabletop3D.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        when(view.render(anyString(), any(net.kyori.adventure.text.Component.class), any(net.kyori.adventure.text.Component.class), anyList(), any())).thenAnswer(invocation ->
            GameMenuLayouts.render(new YamlConfiguration(), (net.kyori.adventure.text.Component)invocation.getArgument(1), net.kyori.adventure.text.Component.empty(), invocation.getArgument(3), invocation.getArgument(4)));
        when(view.open(eq(player), any(), eq("catalog"))).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        menus.main(player);
        verify(view).render(eq("catalog"), eq(net.kyori.adventure.text.Component.text("3D Tabletop Games")), eq(net.kyori.adventure.text.Component.empty()), anyList(), any());
        menus.close();
    }
    @Test void layoutPreservesStyleButReplacesActionsWithOwnedCallbacks() {
        var config = new YamlConfiguration();
        config.set("Title", "@title@");
        config.set("Bottom.buttons.connectfour.width", 222);
        config.set("Bottom.buttons.connectfour.actions", List.of("command: op bad"));
        config.set("Bottom.buttons.mahjong.text", "麻将");
        config.set("Events.Open", List.of("command: old"));
        UUID token = UUID.randomUUID();
        var rendered = GameMenuLayouts.render(config, "棋盘游戏", "", List.of(
            new GameMenus.Button("connectfour", "四子棋", () -> {})), token);
        assertEquals(222, rendered.config().getInt("Bottom.buttons.slot0.width"));
        assertEquals(List.of("3dtabletop:" + token + " 0"), rendered.config().getStringList("Bottom.buttons.slot0.actions"));
        assertFalse(rendered.config().contains("Bottom.buttons.mahjong"));
        assertFalse(rendered.config().contains("Events"));
    }

    @Test void windowUsesRenderedOrderAndDoesNotAlsoSendChatButtons() {
        var plugin = mock(Tabletop3D.class);
        when(plugin.allowed(any())).thenReturn(true);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        var view = mock(BoardWindow.class);
        var clicked = new AtomicInteger();
        when(view.render(anyString(), any(net.kyori.adventure.text.Component.class), any(net.kyori.adventure.text.Component.class), anyList(), any())).thenAnswer(invocation -> {
            var config = new YamlConfiguration();
            config.set("Bottom.buttons.second.text", "@label@");
            return GameMenuLayouts.render(config, "Test", "", invocation.getArgument(3), invocation.getArgument(4));
        });
        when(view.open(eq(player), any(), anyString())).thenReturn(true);
        var menus = new GameMenus(plugin, view);
        menus.show(player, "Test", "", List.of(
            new GameMenus.Button("first", "First", () -> clicked.set(1)),
            new GameMenus.Button("second", "Second", () -> clicked.set(2))), null);
        var rendered = org.mockito.ArgumentCaptor.forClass(YamlConfiguration.class);
        verify(view).open(eq(player), rendered.capture(), eq("dialog"));
        String action = rendered.getValue().getStringList("Bottom.buttons.slot0.actions").getFirst();
        menus.handle(player, action);
        assertEquals(2, clicked.get());
        clicked.set(0);
        menus.handle(player, action);
        assertEquals(0, clicked.get());
        verify(player, never()).sendMessage(anyString());
        menus.close();
    }
}
