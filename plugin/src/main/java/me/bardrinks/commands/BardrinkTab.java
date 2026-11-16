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

        // Auto-complete para o comando /bar
        if (command.getName().equalsIgnoreCase("bar")) {
            if (!sender.hasPermission("bardrinks.admin")) {
                return Collections.emptyList();
            }

            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                for (String opt : Arrays.asList("add", "remove", "list")) {
                    if (opt.startsWith(input)) {
                        completions.add(opt);
                    }
                }
                return completions;
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                String input = args[1].toLowerCase();
                if (sub.equals("add")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(input)) {
                            completions.add(p.getName());
                        }
                    }
                } else if (sub.equals("remove") || sub.equals("remover")) {
                    for (UUID uuid : BarDrinks.get().getRankingManager().getBarmans()) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        if (op.getName() != null && op.getName().toLowerCase().startsWith(input)) {
                            completions.add(op.getName());
                        }
                    }
                }
                return completions;
            }
            return Collections.emptyList();
        }

        // Auto-complete para /bardrink (disponível para OPs e para quem foi adicionado com /bar add)
        if (!BarDrinks.get().isBarman(sender)) {
            return Collections.emptyList();
        }

        boolean isAdmin = sender.hasPermission("bardrinks.admin") || sender.isOp();
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // Barmans comuns veem apenas ban, desban e list
            List<String> options = new ArrayList<>(Arrays.asList("ban", "desban", "list"));
            if (isAdmin) {
                options.addAll(Arrays.asList(
                        "menu", "loja", "set", "vomitar", "apagao",
                        "cerveja", "baiser", "imperio", "spoll",
                        "verde", "laranja", "vermelho", "barril", "mangueira", "cadeira", "mesa"
                ));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    options.add(p.getName());
                }
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

            if (first.equals("ban")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player sp && sp.getUniqueId().equals(p.getUniqueId())) continue;
                    if (BarDrinks.get().isBarman(p)) continue;
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }

            if (!isAdmin) {
                return Collections.emptyList();
            }

            if (first.equals("set") || first.equals("vomitar") || first.equals("apagao")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(input)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }

            if (DrinkType.fromId(first) != null || first.equals("barril") || first.equals("mangueira")
                    || first.equals("cadeira") || first.equals("mesa")) {
                return Arrays.asList("1", "2", "4", "8", "16");
            }

            List<String> items = Arrays.asList(
                    "cerveja", "baiser", "imperio", "spoll",
                    "verde", "laranja", "vermelho", "barril", "mangueira", "cadeira", "mesa"
            );
            for (String it : items) {
                if (it.startsWith(input)) {
                    completions.add(it);
                }
            }
            return completions;
        }

        if (args.length == 3 && isAdmin) {
            String first = args[0].toLowerCase();
            if (first.equals("set")) {
                return Arrays.asList("0", "20", "50", "70", "100");
            }
            return Arrays.asList("1", "2", "4", "8", "16");
        }

        return Collections.emptyList();
    }
}
