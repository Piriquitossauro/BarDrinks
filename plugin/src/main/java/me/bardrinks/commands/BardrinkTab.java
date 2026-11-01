package me.bardrinks.commands;

import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BardrinkTab implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bardrinks.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = new ArrayList<>(Arrays.asList("menu", "set", "vomitar", "cerveja", "verde", "laranja", "vermelho"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
            for (String opt : options) {
                if (opt.toLowerCase().startsWith(input)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            String first = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (first.equals("set") || first.equals("vomitar")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }

            if (DrinkType.fromId(first) != null) {
                return Arrays.asList("1", "2", "4", "8", "16");
            }

            for (DrinkType type : DrinkType.values()) {
                if (type.getId().startsWith(input)) {
                    completions.add(type.getId());
                }
            }
            return completions;
        }

        if (args.length == 3) {
            String first = args[0].toLowerCase();
            if (first.equals("set")) {
                return Arrays.asList("0", "20", "50", "70", "100");
            }
            return Arrays.asList("1", "2", "4", "8", "16");
        }

        return Collections.emptyList();
    }
}
