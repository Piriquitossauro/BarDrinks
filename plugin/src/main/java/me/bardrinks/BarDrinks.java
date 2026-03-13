package me.bardrinks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;

public class BarDrinks extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("BarDrinks iniciado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarDrinks desligado!");
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

                case "cerveja":
                    model = 1001;
                    nome = "§6Cerveja do Anão";
                    break;

                case "verde":
                    model = 1002;
                    nome = "§aDoce de Criança";
                    break;

                case "laranja":
                    model = 1003;
                    nome = "§eAbraço de Menina";
                    break;

                case "vermelho":
                    model = 1004;
                    nome = "§cRubra Noturna";
                    break;

                default:
                    sender.sendMessage("§cBebida inválida.");
                    return true;
            }

            ItemStack drink = new ItemStack(Material.POTION);

            PotionMeta meta = (PotionMeta) drink.getItemMeta();

            meta.setDisplayName(nome);
            meta.setCustomModelData(model);

            // IMPORTANTE: define conteúdo da poção
            meta.setBasePotionType(PotionType.WATER);

            drink.setItemMeta(meta);

            alvo.getInventory().addItem(drink);

            sender.sendMessage("§aBebida entregue!");

            return true;
        }

        return false;
    }
}
