package me.bardrinks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler; // Novo
import org.bukkit.event.Listener; // Novo
import org.bukkit.event.player.PlayerItemConsumeEvent; // Novo
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BarDrinks extends JavaPlugin implements TabCompleter, Listener { // Adicionado Listener

    @Override
    public void onEnable() {
        // Registra os comandos e o TabCompleter
        getCommand("bardrink").setTabCompleter(this);
        
        // Registra os eventos (necessário para fazer o frasco sumir)
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("BarDrinks iniciado!");
    }

    // --- EVENTO PARA SUMIR COM O FRASCO ---
    @EventHandler
    public void aoBeber(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                int cmd = meta.getCustomModelData();
                
                // Se for ID entre 1001 e 1004 (suas bebidas)
                if (cmd >= 1001 && cmd <= 1004) {
                    // Agendamos para remover o frasco logo após o consumo terminar
                    Bukkit.getScheduler().runTask(this, () -> {
                        ItemStack itemNaMao = player.getInventory().getItemInMainHand();
                        ItemStack itemOffHand = player.getInventory().getItemInOffHand();

                        // Se o que sobrou na mão principal foi um frasco, removemos
                        if (itemNaMao.getType() == Material.GLASS_BOTTLE) {
                            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                        } 
                        // Se ele estava bebendo com a mão esquerda
                        else if (itemOffHand.getType() == Material.GLASS_BOTTLE) {
                            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                        }
                    });
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bardrink")) {
            if (args.length == 1) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                return StringUtil.copyPartialMatches(args[0], players, new ArrayList<>());
            } else if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], Arrays.asList("cerveja", "verde", "laranja", "vermelho"), new ArrayList<>());
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bebuns")) {
            sender.sendMessage("§6Ranking de Bebuns ainda vazio!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("bardrink")) {
            if (args.length < 2) {
                sender.sendMessage("§cUse: /bardrink <player> <bebida>");
                return true;
            }

            Player alvo = Bukkit.getPlayer(args[0]);
            if (alvo == null) {
                sender.sendMessage("§cPlayer não encontrado.");
                return true;
            }

            String bebida = args[1].toLowerCase();
            int model = 0;
            String nome = "";

            switch (bebida) {
                case "cerveja": model = 1001; nome = "§6Cerveja do Anão"; break;
                case "verde": model = 1002; nome = "§aDoce de Criança"; break;
                case "laranja": model = 1003; nome = "§eAbraço de Menina"; break;
                case "vermelho": model = 1004; nome = "§cRubra Noturna"; break; //
                default:
                    sender.sendMessage("§cBebida inválida.");
                    return true;
            }

            ItemStack drink = new ItemStack(Material.POTION);
            PotionMeta meta = (PotionMeta) drink.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(nome);
                meta.setCustomModelData(model);
                meta.setBasePotionType(PotionType.WATER);
                drink.setItemMeta(meta);
            }

            alvo.getInventory().addItem(drink);
            sender.sendMessage("§aBebida entregue!");
            return true;
        }
        return false;
    }
}
