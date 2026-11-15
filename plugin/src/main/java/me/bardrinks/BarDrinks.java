package me.bardrinks;

import me.bardrinks.commands.BardrinkTab;
import me.bardrinks.listeners.ChatListener;
import me.bardrinks.listeners.DrinkListener;
import me.bardrinks.managers.*;
import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BarDrinks extends JavaPlugin {

    private static BarDrinks instance;
    private DrunkManager drunkManager;
    private BossBarManager bossBarManager;
    private EffectManager effectManager;
    private RankingManager rankingManager;
    private BarrelManager barrelManager;
    private BarShopManager barShopManager;
    private EconomiaHook economiaHook;
    private ChatListener chatListener;

    public static BarDrinks get() { return instance; }
    public DrunkManager getDrunkManager() { return drunkManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public EffectManager getEffectManager() { return effectManager; }
    public RankingManager getRankingManager() { return rankingManager; }
    public BarrelManager getBarrelManager() { return barrelManager; }
    public BarShopManager getBarShopManager() { return barShopManager; }
    public EconomiaHook getEconomiaHook() { return economiaHook; }
    public ChatListener getChatListener() { return chatListener; }

    @Override
    public void onEnable() {
        instance = this;

        economiaHook = new EconomiaHook();
        drunkManager = new DrunkManager();
        bossBarManager = new BossBarManager();
        effectManager = new EffectManager();
        rankingManager = new RankingManager(this);
        barrelManager = new BarrelManager(this);
        barShopManager = new BarShopManager(this);
        chatListener = new ChatListener();

        Bukkit.getPluginManager().registerEvents(new DrinkListener(), this);
        Bukkit.getPluginManager().registerEvents(chatListener, this);

        if (getCommand("bardrink") != null) {
            getCommand("bardrink").setTabCompleter(new BardrinkTab());
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            double birita = drunkManager.getBiritaExact(p.getUniqueId());
            bossBarManager.handlePlayerJoin(p, birita);
        }

        startBarTickSystem();
        startFastEffectSystem();
        startPeriodicEffectSystem();
        startBarrelCheckSystem();

        getLogger().info("BarDrinks iniciado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (barrelManager != null) {
            barrelManager.cleanupOnDisable();
        }
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
                    sender.sendMessage("§cUse: /bardrink <player> <cerveja|verde|laranja|vermelho|barril|mangueira> [quantidade]");
                    return true;
                }
                openDrinksMenu(p);
                return true;
            }

            // Atalho "/bardrink loja" -> Abre a Loja do Bar (/loja bar)
            if (args[0].equalsIgnoreCase("loja")) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cApenas jogadores podem abrir a Loja do Bar.");
                    return true;
                }
                barShopManager.openMainBarShop(p);
                return true;
            }

            // Subcomando: /bardrink ban <player>
            if (args[0].equalsIgnoreCase("ban")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUse: /bardrink ban <player>");
                    return true;
                }
                OfflinePlayer target = findOfflineOrOnlinePlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cJogador '" + args[1] + "' não encontrado.");
                    return true;
                }
                if (!rankingManager.banPlayer(target.getUniqueId())) {
                    sender.sendMessage("§eO jogador §f" + target.getName() + " §ejá está na lista de banidos do bar!");
                    return true;
                }
                // Se o player banido estiver online segurando a mangueira do barril, desconecta na hora
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    BarrelManager.PlacedBarrel held = barrelManager.getBarrelHeldBy(onlineTarget.getUniqueId());
                    if (held != null) {
                        barrelManager.disconnectCurrentHolder(held, false);
                    }
                    onlineTarget.sendMessage("§c§l[Bar] §cVocê foi proibido pelo barman de consumir bebidas e usar o barril!");
                }
                sender.sendMessage("§aO jogador §f" + (target.getName() != null ? target.getName() : args[1]) + " §afoi banido do bar!");
                return true;
            }

            // Subcomando: /bardrink desban <player>
            if (args[0].equalsIgnoreCase("desban") || args[0].equalsIgnoreCase("unban")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUse: /bardrink desban <player>");
                    return true;
                }
                OfflinePlayer target = findOfflineOrOnlinePlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cJogador '" + args[1] + "' não encontrado.");
                    return true;
                }
                if (!rankingManager.unbanPlayer(target.getUniqueId())) {
                    sender.sendMessage("§eO jogador §f" + target.getName() + " §enão estava banido do bar.");
                    return true;
                }
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    onlineTarget.sendMessage("§a§l[Bar] §aO barman liberou novamente o seu acesso às bebidas do bar!");
                }
                sender.sendMessage("§aO jogador §f" + (target.getName() != null ? target.getName() : args[1]) + " §afoi desbanido do bar!");
                return true;
            }

            // Subcomando: /bardrink list
            if (args[0].equalsIgnoreCase("list")) {
                Set<UUID> banned = rankingManager.getBannedPlayers();
                sender.sendMessage("§c§l🚫 Jogadores Banidos do Bar (" + banned.size() + "):");
                if (banned.isEmpty()) {
                    sender.sendMessage("§7Nenhum jogador está banido no momento.");
                    return true;
                }
                for (UUID uuid : banned) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = op.getName() != null ? op.getName() : uuid.toString();
                    sender.sendMessage(" §c• §f" + name);
                }
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
                Player alvo = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (alvo == null) {
                    sender.sendMessage("§cPlayer não encontrado.");
                    return true;
                }
                effectManager.triggerVomit(alvo);
                sender.sendMessage("§aEfeito de vômito acionado em §f" + alvo.getName() + "§a!");
                return true;
            }

            // Subcomando: /bardrink apagao [player]
            if (args[0].equalsIgnoreCase("apagao") || args[0].equalsIgnoreCase("apagão")) {
                Player alvo = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
                if (alvo == null) {
                    sender.sendMessage("§cPlayer não encontrado.");
                    return true;
                }
                effectManager.triggerBlackout(alvo);
                sender.sendMessage("§aEfeito de apagão acionado em §f" + alvo.getName() + "§a!");
                return true;
            }

            // Caso 1: /bardrink <bebida|barril|mangueira> [quantidade] (entrega para si mesmo)
            if (isSpecialOrDrinkId(args[0])) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cNo console, especifique o player: /bardrink <player> <item> [quantidade]");
                    return true;
                }
                int qtd = parseAmount(args.length >= 2 ? args[1] : "1");
                giveBarItems(p, p, args[0], qtd);
                return true;
            }

            // Caso 2: /bardrink <player> <bebida|barril|mangueira> [quantidade]
            if (args.length < 2) {
                sender.sendMessage("§eComandos do BarDrinks:");
                sender.sendMessage(" §6/loja bar §7- Abre a Loja do Barman (Bird Coins)");
                sender.sendMessage(" §6/bardrink §7- Abre o menu rápido de itens do Bar");
                sender.sendMessage(" §6/bardrink <player> <item> [qtd] §7- Entrega bebida/barril/mangueira");
                sender.sendMessage(" §6/bardrink ban <player> §7- Proíbe um jogador de beber/usar o barril");
                sender.sendMessage(" §6/bardrink desban <player> §7- Libera um jogador banido");
                sender.sendMessage(" §6/bardrink list §7- Lista todos os jogadores banidos do bar");
                sender.sendMessage(" §6/bardrink set <player> <0-100> §7- Define a % do Biritômetro");
                return true;
            }

            Player alvo = Bukkit.getPlayer(args[0]);
            if (alvo == null) {
                sender.sendMessage("§cPlayer '" + args[0] + "' não encontrado.");
                return true;
            }

            if (!isSpecialOrDrinkId(args[1])) {
                sender.sendMessage("§cItem inválido! Opções: §ecerveja, verde, laranja, vermelho, barril, mangueira§c.");
                return true;
            }

            int qtd = parseAmount(args.length >= 3 ? args[2] : "1");
            giveBarItems(sender, alvo, args[1], qtd);
            return true;
        }

        return false;
    }

    private boolean isSpecialOrDrinkId(String id) {
        if (id == null) return false;
        return DrinkType.fromId(id) != null || id.equalsIgnoreCase("barril") || id.equalsIgnoreCase("mangueira");
    }

    private int parseAmount(String str) {
        try {
            return Math.max(1, Math.min(36, Integer.parseInt(str)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void giveBarItems(CommandSender sender, Player alvo, String itemId, int qtd) {
        String displayName;
        for (int i = 0; i < qtd; i++) {
            if (!barrelManager.hasInventorySpace(alvo)) {
                sender.sendMessage("§cSem espaço necessário no inventário");
                return;
            }
            if (itemId.equalsIgnoreCase("barril")) {
                alvo.getInventory().addItem(DrinkType.createBarrelItem(DrinkType.BARRIL_MAX_USES));
            } else if (itemId.equalsIgnoreCase("mangueira")) {
                alvo.getInventory().addItem(DrinkType.createHoseItem(DrinkType.MANGUEIRA_MAX_USES));
            } else {
                DrinkType type = DrinkType.fromId(itemId);
                if (type != null) {
                    alvo.getInventory().addItem(type.createItem());
                }
            }
        }

        if (itemId.equalsIgnoreCase("barril")) {
            displayName = "§6Barril de Chopp";
        } else if (itemId.equalsIgnoreCase("mangueira")) {
            displayName = "§eMangueira de Chopp";
        } else {
            DrinkType t = DrinkType.fromId(itemId);
            displayName = t != null ? t.getDisplayName() : itemId;
        }

        alvo.playSound(alvo.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        sender.sendMessage("§aEntregue §f" + qtd + "x " + displayName + " §apara §f" + alvo.getName() + "§a!");
        if (!alvo.equals(sender)) {
            alvo.sendMessage("§aO barman te entregou §f" + qtd + "x " + displayName + "§a!");
        }
    }

    private OfflinePlayer findOfflineOrOnlinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op;
            }
        }
        return Bukkit.getOfflinePlayer(name);
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

        int[] drinkSlots = {10, 11, 12, 13};
        DrinkType[] types = DrinkType.values();
        for (int i = 0; i < types.length && i < drinkSlots.length; i++) {
            ItemStack item = types[i].createItem();
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                List<String> lore = new ArrayList<>(meta.getLore());
                lore.add("");
                lore.add("§eClique para pegar 1x");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(drinkSlots[i], item);
        }

        gui.setItem(15, DrinkType.createBarrelItem(DrinkType.BARRIL_MAX_USES));
        gui.setItem(16, DrinkType.createHoseItem(DrinkType.MANGUEIRA_MAX_USES));

        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.9f, 1.1f);
    }

    private void startBarTickSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                drunkManager.tickPlayer(p);
            }
        }, 1L, 1L);
    }

    private void startFastEffectSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                double birita = drunkManager.getBiritaExact(p.getUniqueId());
                effectManager.tickFastEffects(p, birita);
            }
        }, 2L, 2L);
    }

    private void startPeriodicEffectSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                double birita = drunkManager.getBiritaExact(p.getUniqueId());
                effectManager.tickPeriodicEffects(p, birita);
            }
        }, 40L, 40L);
    }

    private void startBarrelCheckSystem() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            barrelManager.tickBarrels();
        }, 4L, 4L);
    }
}
