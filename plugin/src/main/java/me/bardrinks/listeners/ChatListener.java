package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Random;

public class ChatListener implements Listener {

    private final Random r = new Random();

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {

        int birita = BarDrinks.get().getDrunkManager().getBirita(e.getPlayer());

        if (birita <= 15) return;

        double chance;

        if (birita <= 40) chance = 0.15;
        else if (birita <= 75) chance = 0.35;
        else chance = 0.55;

        StringBuilder msg = new StringBuilder();

        for (char c : e.getMessage().toCharArray()) {

            if (r.nextDouble() < chance && Character.isLetter(c)) {

                if (r.nextBoolean())
                    c = Character.toUpperCase(c);
                else
                    c = Character.toLowerCase(c);
            }

            msg.append(c);
        }

        e.setMessage(msg.toString());
    }
}
