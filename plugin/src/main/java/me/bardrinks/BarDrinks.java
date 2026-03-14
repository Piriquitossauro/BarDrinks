package me.bardrinks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta; // IMPORTANTE: Faltava este
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BarDrinks extends JavaPlugin implements TabCompleter, Listener {

    @Override
    public void onEnable() {
        if (getCommand("bardrink") != null) {
            getCommand("bardrink").setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BarDrinks iniciado com sucesso!");
    }

    @EventHandler
    public void aoBeber(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                int cmd = meta.getCustomModelData();
                
                // Verifica se é uma das suas bebidas (1001-1004)
                if (cmd >= 1001 && cmd <= 1004) {
                    // Esperamos 1 tick (0.05s) para o Minecraft terminar de processar o consumo
                    // e então removemos o frasco de vidro que sobraria na mão.
                    Bukkit.getScheduler().runTask(this, () -> {
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        ItemStack offHand = player.getInventory().getItemInOffHand();

                        if (mainHand.getType() == Material.GLASS_BOTTLE) {
                            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                        } else if (offHand.getType() == Material.GLASS_BOTTLE) {
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
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[0], players, new ArrayList<>());
            } else if (args.length == 2) {
                List<String> bebidas = Arrays.asList("cerveja", "verde", "laranja", "vermelho");
                return StringUtil.copyPartialMatches(args[1], bebidas, new ArrayList<>());
            }
        }
        return new ArrayList<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bebuns")) {
            sender.sendMessage("§6§l[Bar] §fRanking de Bebuns ainda vazio!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("bardrink")) {
            if (args.length < 2) {
                sender.sendMessage("§cUse: /bardrink <player> <bebida>");
                return true;
            }

            Player alvo = Bukkit.getPlayer(args[0]);
            if (alvo == null) {
                sender.sendMessage("§cErro: Jogador offline.");
                return true;
            }

            String escolha = args[1].toLowerCase();
            int model = 0;
            String nomeItem = "";

            switch (escolha) {
                case "cerveja":
                    model = 1001;
                    nomeItem = "§6Cerveja do Anão";
                    break;
                case "verde":
                    model = 1002;
                    nomeItem = "§aDoce de Criança";
                    break;
                case "laranja":
                    model = 1003;
                    nomeItem = "§eAbraço de Menina";
                    break;
                case "vermelho":
                    model = 1004;
                    nomeItem = "§cRubra Noturna";
                    break;
                default:
                    sender.sendMessage("§cBebida não encontrada!");
                    return true;
            }

            ItemStack drink = new ItemStack(Material.POTION);
            PotionMeta pMeta = (PotionMeta) drink.getItemMeta();

            if (pMeta != null) {
                pMeta.setDisplayName(nomeItem);
                pMeta.setCustomModelData(model);
                pMeta.setBasePotionType(PotionType.WATER);
                drink.setItemMeta(pMeta);
            }

            alvo.getInventory().addItem(drink);
            sender.sendMessage("§aSucesso! " + nomeItem + " §aentregue para " + alvo.getName());
            return true;
        }
        return false;
    }
}
