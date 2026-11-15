package me.bardrinks.commands;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class BardrinkTab implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bardrinks.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = new ArrayList<>(Arrays.asList(
                    "menu", "loja", "ban", "desban", "list", "set", "vomitar", "apagao",
                    "cerveja", "verde", "laranja", "vermelho", "barril", "mangueira"
            ));
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

            if (first.equals("desban")) {
                for (UUID uuid : BarDrinks.get().getRankingManager().getBannedPlayers()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    if (op.getName() != null && op.getName().toLowerCase().startsWith(input)) {
                        completions.add(op.getName());
                    }
                }
                return completions;
            }

            if (first.equals("ban") || first.equals("set") || first.equals("vomitar") || first.equals("apagao")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }

            if (DrinkType.fromId(first) != null || first.equals("barril") || first.equals("mangueira")) {
                return Arrays.asList("1", "2", "4");
            }

            List<String> items = Arrays.asList("cerveja", "verde", "laranja", "vermelho", "barril", "mangueira");
            for (String it : items) {
                if (it.startsWith(input)) {
                    completions.add(it);
                }
            }
            return completions;
        }

        if (args.length == 3) {
            String first = args[0].toLowerCase();
            if (first.equals("set")) {
                return Arrays.asList("0", "20", "50", "70", "100");
            }
            return Arrays.asList("1", "2", "4");
        }

        return Collections.emptyList();
    }
}
