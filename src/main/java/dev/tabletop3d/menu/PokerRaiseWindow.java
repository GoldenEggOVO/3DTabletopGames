package dev.tabletop3d.menu;

import com.google.gson.JsonPrimitive;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.texasholdem.TexasHoldemGame;
import dev.tabletop3d.text.Language;

import net.kyori.adventure.text.event.ClickCallback;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

/** A raise form captures one room revision and accepts one response from its seated owner. */
final class PokerRaiseWindow {
    private PokerRaiseWindow() {}

    static void open(Tabletop3D plugin, Player player, Room room, long revision) {
        int seat = room.seat(player.getUniqueId());
        if (seat < 0
                || !room.kind.equals("texas-holdem")
                || !room.board.legalActions(seat).stream().anyMatch(a -> a.startsWith("raise:")))
            return;
        var info = room.board.publicInfo();
        var action =
                DialogAction.customClick(
                        (response, audience) -> {
                            if (!(audience instanceof Player actor)
                                    || !actor.getUniqueId().equals(player.getUniqueId())
                                    || !plugin.isEnabled()) return;
                            String total = response.getText("street_total");
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (!plugin.isEnabled()
                                                        || !actor.isOnline()
                                                        || !plugin.allowed(actor)) return;
                                                try {
                                                    if (plugin.rooms.get(room.id) != room)
                                                        throw new IllegalArgumentException(
                                                                "Room closed");
                                                    room.requireAction(
                                                            actor.getUniqueId(), revision);
                                                    int value = Integer.parseInt(total);
                                                    ((TexasHoldemGame) room.board)
                                                            .validateRaise(seat, value);
                                                    plugin.apply(
                                                            room,
                                                            seat,
                                                            new JsonPrimitive("raise:" + value),
                                                            null);
                                                } catch (IllegalArgumentException error) {
                                                    actor.sendActionBar(Language.error(error));
                                                }
                                            });
                        },
                        ClickCallback.Options.builder()
                                .uses(1)
                                .lifetime(Duration.ofMinutes(2))
                                .build());
        var input =
                DialogInput.text(
                        "street_total",
                        300,
                        Language.component("cards.poker.raise-total"),
                        true,
                        info.get("minimumRaise"),
                        6,
                        null);
        var base =
                DialogBase.create(
                        Language.component("cards.control.raise"),
                        null,
                        true,
                        false,
                        DialogBase.DialogAfterAction.CLOSE,
                        List.of(
                                DialogBody.plainMessage(
                                        Language.component(
                                                "cards.poker.raise-help",
                                                "minimum",
                                                info.get("minimumRaise"),
                                                "already",
                                                info.get("bet." + seat),
                                                "chips",
                                                info.get("chips." + seat)),
                                        340)),
                        List.of(input));
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(base)
                                        .type(
                                                DialogType.multiAction(
                                                        List.of(
                                                                ActionButton.create(
                                                                        Language.component(
                                                                                "cards.control.raise"),
                                                                        null,
                                                                        160,
                                                                        action)),
                                                        ActionButton.create(
                                                                Language.component("menu.close"),
                                                                null,
                                                                160,
                                                                null),
                                                        1))));
    }
}
