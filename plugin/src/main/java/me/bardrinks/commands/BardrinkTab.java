package me.bardrinks.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;

public class BardrinkTab implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 2) {
            return Arrays.asList("cerveja", "verde", "laranja", "vermelho");
        }

        return null;
    }
}
