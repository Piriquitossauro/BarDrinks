package me.bardrinks.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

public class BardrinkTab implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        
        // Se o player NÃO tiver permissão de admin, retorna uma lista vazia (não sugere nada)
        if (!sender.hasPermission("bardrinks.admin")) {
            return java.util.Collections.emptyList();
        }

        if (args.length == 2) {
            return Arrays.asList("cerveja", "verde", "laranja", "vermelho");
        }

        return null;
    }
}
