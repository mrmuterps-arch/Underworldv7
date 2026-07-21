package ru.mandarin.chainbind.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.mandarin.chainbind.BindManager;
import ru.mandarin.chainbind.ChainBindPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChainBindCommand implements CommandExecutor, TabCompleter {

    private final ChainBindPlugin plugin;
    private final BindManager bindManager;

    public ChainBindCommand(ChainBindPlugin plugin, BindManager bindManager) {
        this.plugin = plugin;
        this.bindManager = bindManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chainbind.admin")) {
            sender.sendMessage(bindManager.msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(bindManager.msg("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                sender.sendMessage(bindManager.msg("reload-success"));
                return true;
            }
            case "release" -> {
                if (args.length < 2) {
                    sender.sendMessage(bindManager.msg("usage"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок не найден или не в сети.");
                    return true;
                }
                boolean wasBound = bindManager.release(target.getUniqueId());
                if (wasBound) {
                    bindManager.playReleaseEffects(target);
                    target.sendMessage(bindManager.msg("released-by-shears"));
                    sender.sendMessage(bindManager.msg("admin-released", "%target%", target.getName()));
                } else {
                    sender.sendMessage(bindManager.msg("admin-target-not-bound"));
                }
                return true;
            }
            default -> {
                sender.sendMessage(bindManager.msg("usage"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("release", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("release")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names.stream()
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
