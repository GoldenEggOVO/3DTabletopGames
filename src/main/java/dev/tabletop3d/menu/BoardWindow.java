package dev.tabletop3d.menu;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.ui.MessageText;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

public final class BoardWindow {
    record Control(Component caption, int width, String action) {}

    record Menu(
            Component title,
            Component description,
            int descriptionWidth,
            int columns,
            List<Control> headers,
            List<Control> controls,
            Control exit,
            List<GameMenus.Button> buttons) {}

    private final Tabletop3D plugin;

    public BoardWindow(Tabletop3D plugin) {
        this.plugin = plugin;
    }

    Menu render(
            String page,
            Component title,
            Component description,
            List<GameMenus.Button> buttons,
            UUID token) {
        return layout(page, title, description, buttons, token);
    }

    static Menu layout(
            String page,
            Component title,
            Component description,
            List<GameMenus.Button> supplied,
            UUID token) {
        List<String> order =
                switch (page) {
                    case "catalog" -> List.of("connectfour");
                    case "room" -> List.of("ready", "bots", "play", "entry", "rematch", "leave");
                    case "setup" ->
                            List.of(
                                    "start",
                                    "mode",
                                    "capacity",
                                    "board",
                                    "rule-profile",
                                    "details",
                                    "entry");
                    default -> List.of("entry");
                };
        List<GameMenus.Button> buttons = new ArrayList<>(supplied);
        buttons.sort(Comparator.comparingInt(button -> buttonOrder(button.id(), order)));
        List<Control> headers = new ArrayList<>();
        List<Control> controls = new ArrayList<>();
        Control exit = null;
        for (int index = 0; index < buttons.size(); index++) {
            GameMenus.Button button = buttons.get(index);
            Component caption = caption(page, button);
            String action = "3dtabletop:" + token + " " + index;
            if (button.id().equals("close")) {
                exit = new Control(caption, 230, action);
            } else if (page.equals("catalog")
                    && List.of("resource-pack", "resume", "rooms").contains(button.id())) {
                headers.add(
                        new Control(
                                caption.clickEvent(
                                        ClickEvent.runCommand("/3dtabletop click " + action)),
                                350,
                                action));
            } else {
                int width =
                        switch (page) {
                            case "catalog" -> 174;
                            default -> 330;
                        };
                controls.add(new Control(caption, width, action));
            }
        }
        return new Menu(
                MessageText.render("<white>{title}", "title", title),
                MessageText.render("<white>{description}", "description", description),
                370,
                page.equals("catalog") ? 2 : 1,
                List.copyOf(headers),
                List.copyOf(controls),
                exit,
                List.copyOf(buttons));
    }

    private static int buttonOrder(String id, List<String> order) {
        return switch (id) {
            case "resource-pack" -> -2;
            case "resume" -> -1;
            case "back" -> order.size() + 1;
            case "main" -> order.size() + 2;
            case "close" -> order.size() + 3;
            default -> order.contains(id) ? order.indexOf(id) : order.size();
        };
    }

    private static Component caption(String page, GameMenus.Button button) {
        String style =
                switch (button.id()) {
                    case "close" -> "<dark_gray>[ <red>{label} <dark_gray>]";
                    case "main", "resume" -> "<yellow>{label}";
                    case "back" -> "<yellow>{label}";
                    case "rooms" -> page.equals("catalog") ? "<aqua>{label}" : "<white>{label}";
                    case "ready", "play" ->
                            page.equals("room") ? "<green><bold>{label}</bold>" : "<white>{label}";
                    case "bots" -> page.equals("room") ? "<aqua>{label}" : "<white>{label}";
                    case "leave" -> page.equals("room") ? "<gray>{label}" : "<white>{label}";
                    case "start" ->
                            page.equals("setup") ? "<green><bold>{label}</bold>" : "<white>{label}";
                    case "mode", "capacity", "board", "rule-profile" ->
                            page.equals("setup") ? "<aqua>{label}" : "<white>{label}";
                    case "details" -> page.equals("setup") ? "<yellow>{label}" : "<white>{label}";
                    default -> "<white>{label}";
                };
        String label =
                button.id().equals("close") || button.id().equals("back")
                        ? button.label()
                        : button.label().replaceAll("(?i)[§&][0-9A-FK-OR]", "");
        Component content = button.component() != null ? button.component() : text(label);
        return MessageText.render(style, "label", content);
    }

    boolean open(Player player, Menu menu) {
        List<DialogBody> body = new ArrayList<>();
        if (!MessageText.plain(menu.description()).isBlank()) {
            body.add(DialogBody.plainMessage(menu.description(), menu.descriptionWidth()));
        }
        for (Control header : menu.headers()) {
            body.add(DialogBody.plainMessage(header.caption(), header.width()));
        }
        List<ActionButton> buttons =
                menu.controls().stream().map(control -> button(player, control)).toList();
        var exit = menu.exit() == null ? null : button(player, menu.exit());
        var base =
                DialogBase.create(
                        menu.title(),
                        null,
                        true,
                        false,
                        DialogBase.DialogAfterAction.CLOSE,
                        body,
                        List.of());
        player.showDialog(
                Dialog.create(
                        factory ->
                                factory.empty()
                                        .base(base)
                                        .type(
                                                DialogType.multiAction(
                                                        buttons, exit, menu.columns()))));
        return true;
    }

    private ActionButton button(Player player, Control control) {
        var action =
                DialogAction.customClick(
                        (response, audience) -> {
                            if (!(audience instanceof Player actor)
                                    || !actor.getUniqueId().equals(player.getUniqueId())
                                    || !plugin.isEnabled()) {
                                return;
                            }
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (plugin.isEnabled() && actor.isOnline()) {
                                                    plugin.menus.handle(actor, control.action());
                                                }
                                            });
                        },
                        ClickCallback.Options.builder()
                                .uses(1)
                                .lifetime(Duration.ofMinutes(2))
                                .build());
        return ActionButton.create(control.caption(), null, control.width(), action);
    }

    static Component text(String value) {
        return MessageText.render(value);
    }
}
