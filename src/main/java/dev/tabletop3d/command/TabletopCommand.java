package dev.tabletop3d.command;

import com.google.gson.JsonPrimitive;

import dev.tabletop3d.Tabletop3D;
import dev.tabletop3d.room.Room;
import dev.tabletop3d.rules.HandGame;
import dev.tabletop3d.rules.RuleViolation;
import dev.tabletop3d.text.Language;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TabletopCommand implements Listener, CommandExecutor, TabCompleter {
    private final Tabletop3D plugin;

    public TabletopCommand(Tabletop3D plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload-language")) {
            if (sender instanceof Player player
                    && (!plugin.allowed(player) || !player.hasPermission("3dtabletop.admin"))) {
                plugin.tell(player, Language.component("chat.permission"));
                return true;
            }
            try {
                var config = new org.bukkit.configuration.file.YamlConfiguration();
                config.load(new File(plugin.getDataFolder(), "config.yml"));
                boolean changed =
                        Language.reload(
                                plugin.getDataFolder().toPath().resolve("languages"),
                                config.getString("language", "en_US"),
                                plugin.getLogger()::warning);
                if (changed && plugin.menus != null) plugin.menus.languageChanged();
                sender.sendMessage(
                        Language.component(changed ? "language.reloaded" : "language.failed"));
            } catch (Exception ex) {
                plugin.getLogger().warning("Cannot reload languages: " + ex.getMessage());
                sender.sendMessage(Language.component("language.failed"));
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Language.component("chat.console"));
            if (args.length > 0 && args[0].equals("status"))
                sender.sendMessage(
                        Language.component(
                                "chat.status",
                                "rooms",
                                plugin.rooms.size()));
            return true;
        }
        if (!plugin.allowed(p)) {
            plugin.tell(p, Language.component("chat.permission"));
            return true;
        }
        try {
            String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "menu" -> plugin.menus.main(p);
                case "click" -> {
                    if (args.length == 3) plugin.menus.handle(p, args[1] + " " + args[2]);
                }
                case "move" -> {
                    if (args.length == 2) {
                        Room r = requireRoom(p);
                        plugin.action(p, r, r.revision, new JsonPrimitive(args[1]));
                    }
                }
                case "resume" -> {
                    Room r = plugin.room(p);
                    if (r == null) {
                        plugin.menus.main(p);
                    } else plugin.resume(p, r);
                }
                case "create" -> {
                    if (args.length < 2) plugin.menus.main(p);
                    else {
                        String game = args[1].toLowerCase(Locale.ROOT);
                        plugin.create(
                                p,
                                game,
                                args.length > 2
                                        ? Integer.parseInt(args[2])
                                        : Tabletop3D.defaultCapacity(game));
                    }
                }
                case "join" -> {
                    if (args.length < 2) plugin.menus.main(p);
                    else plugin.join(p, find(args[1]));
                }
                case "ready" -> {
                    Room r = requireRoom(p);
                    plugin.ready(p, r);
                }
                case "bots" -> {
                    Room r = requireRoom(p);
                    plugin.startWithBots(p, r);
                }
                case "leave" -> {
                    plugin.menus.confirmLeave(p);
                }
                case "rematch" -> plugin.rematch(p, requireRoom(p));
                case "rules" -> {
                    Room r = plugin.room(p);
                    plugin.menus.rules(
                            p,
                            args.length > 1 && Tabletop3D.GAMES.contains(args[1])
                                    ? args[1]
                                    : r == null ? "gomoku" : r.kind);
                }
                default -> plugin.menus.main(p);
            }
        } catch (IllegalArgumentException ex) {
            plugin.tell(p, Language.error(ex));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1
                && "reload-language".startsWith(args[0].toLowerCase(Locale.ROOT))
                && (!(sender instanceof Player player)
                        || plugin.allowed(player) && player.hasPermission("3dtabletop.admin"))) {
            var suggestions =
                    new ArrayList<>(
                            sender instanceof Player player
                                    ? CommandSuggestions.complete(
                                            args, plugin.rooms.values(), plugin.room(player), false, true)
                                    : CommandSuggestions.complete(
                                            args, plugin.rooms.values(), null, true, true));
            suggestions.add("reload-language");
            return suggestions;
        }
        if (sender instanceof Player player) {
            Room own = plugin.room(player);
            if (args.length == 2
                    && args[0].equalsIgnoreCase("move")
                    && own != null
                    && own.board instanceof HandGame
                    && own.seat(player.getUniqueId()) != own.turn()) return List.of();
            return CommandSuggestions.complete(args, plugin.rooms.values(), own, false, plugin.allowed(player));
        }
        return CommandSuggestions.complete(args, plugin.rooms.values(), null, true, true);
    }

    @EventHandler
    public void commandSuggestions(PlayerCommandSendEvent event) {
        CommandSuggestions.hideDuplicateRoot(event.getCommands());
    }

    private Room requireRoom(Player p) {
        Room r = plugin.room(p);
        if (r == null) throw new RuleViolation("error.you-have-not-joined-a-room", "You have not joined a room");
        return r;
    }

    private Room find(String text) {
        return plugin.rooms.values().stream()
                .filter(r -> r.id.toString().startsWith(text))
                .findFirst()
                .orElseThrow(() -> new RuleViolation("error.room-closed", "Room closed"));
    }

}
