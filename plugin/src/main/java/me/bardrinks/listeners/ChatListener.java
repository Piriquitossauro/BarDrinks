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

        if (birita <= 15)
            return;

        double chance;

        if (birita <= 40) chance = 0.25;
        else if (birita <= 75) chance = 0.45;
        else chance = 0.70;

        String[] words = e.getMessage().split(" ");

        StringBuilder result = new StringBuilder();

        for (String word : words) {

            if (r.nextDouble() < chance) {

                word = drunkWord(word);

            }

            result.append(word).append(" ");
        }

        if (birita > 75 && r.nextDouble() < 0.25)
            result.append("...hic");

        e.setMessage(result.toString().trim());
    }

    private String drunkWord(String word) {

        if (word.length() < 3)
            return word;

        int effect = r.nextInt(4);

        switch (effect) {

            case 0: // repetir letra
                int pos = r.nextInt(word.length());
                char c = word.charAt(pos);
                return word.substring(0, pos) + c + c + word.substring(pos);

            case 1: // inverter duas letras
                int i = r.nextInt(word.length() - 1);
                char a = word.charAt(i);
                char b = word.charAt(i + 1);
                return word.substring(0, i) + b + a + word.substring(i + 2);

            case 2: // remover letra
                int rpos = r.nextInt(word.length());
                return word.substring(0, rpos) + word.substring(rpos + 1);

            case 3: // misturar maiúsculo
                StringBuilder sb = new StringBuilder();
                for (char ch : word.toCharArray()) {
                    if (r.nextBoolean())
                        sb.append(Character.toUpperCase(ch));
                    else
                        sb.append(Character.toLowerCase(ch));
                }
                return sb.toString();
        }

        return word;
    }
}
