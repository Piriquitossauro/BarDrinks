package me.bardrinks;

import me.bardrinks.commands.BardrinkTab;
import me.bardrinks.listeners.ChatListener;
import me.bardrinks.listeners.DrinkListener;
import me.bardrinks.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import java.util.Map;
import java.util.UUID;

public class BarDrinks extends JavaPlugin {

    private static BarDrinks instance;

    private DrunkManager drunkManager;
    private BossBarManager bossBarManager;
    private EffectManager effectManager;
    private RankingManager rankingManager;

    public static BarDrinks get() {
        return instance;
    }

    public DrunkManager getDrunkManager() {
        return drunkManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    @Override
    public void onEnable() {

        instance = this;

        drunkManager = new DrunkManager();
        bossBarManager = new BossBarManager();
        effectManager = new EffectManager();
        rankingManager = new RankingManager();

        Bukkit.getPluginManager().registerEvents(new DrinkListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(), this);

        getCommand("bardrink").setTabCompleter(new BardrinkTab());

        startSobrietySystem();
        startEffectSystem();

        getLogger().info("BarDrinks iniciado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarDrinks desligado!");
    }

    // COMANDOS
    @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

      if (command.getName().equalsIgnoreCase("bebuns")) {

    sender.sendMessage("§6Top Bebuns do Servidor:");

    int pos = 1;

    for (Map.Entry<java.util.UUID, Integer> entry : rankingManager.getTop()) {

        String nome = Bukkit.getOfflinePlayer(entry.getKey()).getName();
        int qtd = entry.getValue();

        sender.sendMessage("§e" + pos + ". §f" + nome + " §7- " + qtd + " bebidas");

        pos++;

        if (pos > 10) break;
    }

    return true;
}
       
        if (command.getName().equalsIgnoreCase("bardrink")) {

            if (args.length < 2) {
                sender.sendMessage("§cUse: /bardrink <player> <cerveja|verde|laranja|vermelho>");
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
            meta.setBasePotionType(PotionType.WATER);

            drink.setItemMeta(meta);

            alvo.getInventory().addItem(drink);

            sender.sendMessage("§aBebida entregue!");
            return true;
        }

        return false;
    }

    private void startSobrietySystem() {

        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Player p : Bukkit.getOnlinePlayers()) {

                int birita = drunkManager.getBirita(p);

                if (birita <= 0) continue;

                long lastDrink = drunkManager.getLastDrink(p);

                if (System.currentTimeMillis() - lastDrink < 10000)
                    continue;

                drunkManager.removeBirita(p, 10);

                bossBarManager.updateBar(p, drunkManager.getBirita(p));
            }

        }, 200, 200);
    }

    private void startEffectSystem() {

        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Player p : Bukkit.getOnlinePlayers()) {

                int birita = drunkManager.getBirita(p);

                effectManager.applyEffects(p, birita);
            }

        }, 40, 40);
    }
}
