package me.bardrinks;

import me.bardrinks.commands.BardrinkTab;
import me.bardrinks.listeners.ChatListener;
import me.bardrinks.listeners.DrinkListener;
import me.bardrinks.managers.BossBarManager;
import me.bardrinks.managers.DrunkManager;
import me.bardrinks.managers.EffectManager;
import me.bardrinks.managers.RankingManager;
import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BarDrinks extends JavaPlugin {

    private static BarDrinks instance;
    private DrunkManager drunkManager;
    private BossBarManager bossBarManager;
    private EffectManager effectManager;
    private RankingManager rankingManager;

    public static BarDrinks get() { return instance; }
    public DrunkManager getDrunkManager() { return drunkManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public EffectManager getEffectManager() { return effectManager; }
    public RankingManager getRankingManager() { return rankingManager; }

    @Override
    public void onEnable() {
        instance = this;

        drunkManager = new DrunkManager();
        bossBarManager = new BossBarManager();
        effectManager = new EffectManager();
        rankingManager = new RankingManager(this);

        Bukkit.getPluginManager().registerEvents(new DrinkListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(), this);

        if (getCommand("bardrink") != null) {
            getCommand("bardrink").setTabCompleter(new BardrinkTab());
        }

        // Restaura BossBars caso o servidor tenha dado /reload com jogadores online
        for (Player p : Bukkit.getOnlinePlayers()) {
            double birita = drunkManager.getBiritaExact(p.getUniqueId());
            bossBarManager.handlePlayerJoin(p, birita);
        }

        startBarTickSystem();
        startFastEffectSystem();
        startPeriodicEffectSystem();

        getLogger().info("BarDrinks iniciado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (rankingManager != null) {
            rankingManager.saveData();
        }
        if (effectManager != null) {
            effectManager.cleanupAll();
        }
        if (bossBarManager != null) {
            bossBarManager.removeAll();
        }
        getLogger().info("BarDrinks desligado!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // --- COMANDO /BEBUNS ---
        if (command.getName().equalsIgnoreCase("bebuns")) {
            if (!sender.hasPermission("bardrinks.player")) {
                sender.sendMessage("§cVocê não tem permissão para ver o ranking!");
                return true;
            }

            List<Map.Entry<UUID, Integer>> top = rankingManager.getTop();
            sender.sendMessage("§6§l🍺 Top Bebuns do Servidor:");
            if (top.isEmpty()) {
                sender.sendMessage("§7Ninguém bebeu nada ainda!");
                return true;
            }

            int pos = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                String nome = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                int qtd = entry.getValue();
                sender.sendMessage("§e" + pos + "º §f" + (nome != null ? nome : "Desconhecido") + " §7- §6" + qtd + " §7bebidas");
                pos++;
                if (pos > 10) break;
            }
            return true;
        }

        // --- COMANDO /BARDRINK ---
        if (command.getName().equalsIgnoreCase("bardrink")) {
            if (!sender.hasPermission("bardrinks.admin")) {
                sender.sendMessage("§cSomente barmans podem usar esse comando!");
                return true;
            }

            // Sem argumentos ou "/bardrink menu" -> Abre o Menu GUI de bebidas
            if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("menu"))) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cUse: /bardrink <player> <cerveja|verde|laranja|vermelho> [quantidade]");
                    return true;
                }
                openDrinksMenu(p);
                return true;
            }

            // Subcomando: /bardrink set <player> <0-100>
            if (args[0].equalsIgnoreCase("set")) {
                if (args.length < 3) {
                    sender.sendMessage("§cUse: /bardrink set <player> <0-100>");
                    return true;
                }
                Player alvo = Bukkit.getPlayer(args[1]);
                if (alvo == null) {
                    sender.sendMessage("§cPlayer não encontrado.");
                    return true;
                }
                double pct;
                try {
                    pct = Double.parseDouble(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§cPorcentagem inválida! Digite um número de 0 a 100.");
                    return true;
                }
                pct = Math.max(0.0, Math.min(100.0, pct));
                drunkManager.setBiritaInstant(alvo.getUniqueId(), pct);
                rankingManager.saveDataAsync();
                sender.sendMessage("§aBiritômetro de §f" + alvo.getName() + " §adefinido para §e" + Math.round(pct) + "%§a!");
                return true;
            }

            // Subcomando: /bardrink vomitar [player]
            if (args[0].equalsIgnoreCase("vomitar")) {
                Player alvo;
                if (args.length >= 2) {
                    alvo = Bukkit.getPlayer(args[1]);
                } else if (sender instanceof Player p) {
                    alvo = p;
                } else {
                    sender.sendMessage("§cUse: /bardrink vomitar <player>");
                    return true;
                }
                if (alvo == null) {
                    sender.sendMessage("§cPlayer não encontrado.");
                    return true;
                }
                effectManager.triggerVomit(alvo);
                sender.sendMessage("§aEfeito de vômito acionado em §f" + alvo.getName() + "§a!");
                return true;
            }

            // Caso 1: /bardrink <bebida> [quantidade] (entrega para si mesmo)
            DrinkType selfDrink = DrinkType.fromId(args[0]);
            if (selfDrink != null) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cNo console, especifique o player: /bardrink <player> <bebida> [quantidade]");
                    return true;
                }
                int qtd = 1;
                if (args.length >= 2) {
                    try {
                        qtd = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {}
                }
                p.getInventory().addItem(selfDrink.createItem(qtd));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage("§aVocê recebeu §f" + qtd + "x " + selfDrink.getDisplayName() + "§a!");
                return true;
            }

            // Caso 2: /bardrink <player> <bebida> [quantidade]
            if (args.length < 2) {
                sender.sendMessage("§eUso do BarDrinks:");
                sender.sendMessage(" §6/bardrink §7- Abre o menu interativo de bebidas");
                sender.sendMessage(" §6/bardrink <bebida> [qtd] §7- Pega uma bebida para você");
                sender.sendMessage(" §6/bardrink <player> <bebida> [qtd] §7- Entrega bebida a um jogador");
                sender.sendMessage(" §6/bardrink set <player> <0-100> §7- Define a % do Biritômetro");
                sender.sendMessage(" §6/bardrink vomitar [player] §7- Força o efeito de vômito");
                return true;
            }

            Player alvo = Bukkit.getPlayer(args[0]);
            if (alvo == null) {
                sender.sendMessage("§cPlayer '" + args[0] + "' não encontrado.");
                return true;
            }

            DrinkType type = DrinkType.fromId(args[1]);
            if (type == null) {
                sender.sendMessage("§cBebida inválida! Opções: §ecerveja, verde, laranja, vermelho§c.");
                return true;
            }

            int qtd = 1;
            if (args.length >= 3) {
                try {
                    qtd = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                } catch (NumberFormatException ignored) {}
            }

            alvo.getInventory().addItem(type.createItem(qtd));
            alvo.playSound(alvo.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            sender.sendMessage("§aEntregue §f" + qtd + "x " + type.getDisplayName() + " §apara §f" + alvo.getName() + "§a!");
            if (!alvo.equals(sender)) {
                alvo.sendMessage("§aO barman te entregou §f" + qtd + "x " + type.getDisplayName() + "§a!");
            }
            return true;
        }

        return false;
    }

    private void openDrinksMenu(Player p) {
        Inventory gui = Bukkit.createInventory(null, 27, DrinkListener.GUI_TITLE);

        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName(" ");
            glass.setItemMeta(gMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        int[] slots = {10, 12, 14, 16};
        DrinkType[] types = DrinkType.values();
        for (int i = 0; i < types.length && i < slots.length; i++) {
            ItemStack item = types[i].createItem(1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.add("");
                lore.add("§eClique Esquerdo: §fPegar 1x");
                lore.add("§eShift + Clique: §fPegar 16x");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(slots[i], item);
        }

        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.9f, 1.1f);
    }

    /**
     * Loop de 1 tick (20x por segundo) para animar a barra subindo/descendo em 1,5s,
     * respeitar os 20s de espera e parar 5s em cada marco.
     */
    private void startBarTickSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                drunkManager.tickPlayer(p);
            }
        }, 1L, 1L);
    }

    /**
     * Loop rápido de 2 ticks (10x por segundo) para:
     * - Balanço suave no chão (sem flutuar no ar)
     * - Partículas de bêbado no Bebo 2 e 3 (sem efeito de poção LUCK)
     * - Partículas de vômito e afastar mobs pacíficos por 10s
     */
    private void startFastEffectSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                double birita = drunkManager.getBiritaExact(p.getUniqueId());
                effectManager.tickFastEffects(p, birita);
            }
        }, 2L, 2L);
    }

    /**
     * Loop de 40 ticks (a cada 2 segundos) para:
     * - Náusea no Bebo 3
     * - Chance de 1 em 50 de vomitar a cada 2 segundos
     * - Chance de sentar ou deitar involuntariamente no Bebo 3
     */
    private void startPeriodicEffectSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                double birita = drunkManager.getBiritaExact(p.getUniqueId());
                effectManager.tickPeriodicEffects(p, birita);
            }
        }, 40L, 40L);
    }
}
