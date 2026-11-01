package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrunkStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Random;

public class ChatListener implements Listener {

    private final Random r = new Random();
    private static final String[] HICCUPS = {"...hic", " *hic*", "... hic!", " ~hic~"};

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        double birita = BarDrinks.get().getDrunkManager().getBiritaExact(player.getUniqueId());
        DrunkStage stage = DrunkStage.fromPercentage(birita);

        // Sóbrio (0% a 20%): chat normal
        if (stage == DrunkStage.SOBRIO) {
            return;
        }

        double wordChance;
        double hiccupChance;
        double stutterChance;

        switch (stage) {
            case BEBO_1: // 21% a 50%
                wordChance = 0.25;
                hiccupChance = 0.10;
                stutterChance = 0.08;
                break;
            case BEBO_2: // 51% a 70%
                wordChance = 0.45;
                hiccupChance = 0.25;
                stutterChance = 0.15;
                break;
            case BEBO_3: // 71% a 100%
            default:
                wordChance = 0.70;
                hiccupChance = 0.55;
                stutterChance = 0.25;
                break;
        }

        String[] words = e.getMessage().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int idx = 0; idx < words.length; idx++) {
            String word = words[idx];

            // Não altera links nem nomes exatos de players online
            if (isUrlOrPlayerName(word)) {
                result.append(word).append(" ");
                continue;
            }

            // Chance de gaguejar antes de palavras curtas ("eu... eu", "n-não")
            if (idx > 0 && word.length() >= 2 && word.length() <= 4 && r.nextDouble() < stutterChance) {
                if (r.nextBoolean()) {
                    result.append(word.charAt(0)).append("-");
                } else {
                    result.append(word).append("... ");
                }
            }

            if (r.nextDouble() < wordChance) {
                word = slurWord(word, stage);
            }

            result.append(word).append(" ");
        }

        String finalMsg = result.toString().trim();

        if (r.nextDouble() < hiccupChance) {
            finalMsg += HICCUPS[r.nextInt(HICCUPS.length)];
        }

        e.setMessage(finalMsg);
    }

    private boolean isUrlOrPlayerName(String word) {
        String lower = word.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return true;
        }
        String clean = word.replace("@", "");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transforma uma palavra de forma realista para simular fala enrolada de bêbado:
     * 1. Engolir o 'r' final em verbos (ex: "beber" -> "bebê", "falar" -> "falá")
     * 2. Chiado na língua: 's'/'ss'/'ç'/'ce'/'ci' -> 'sh'/'x' (ex: "certeza" -> "sherteza", "você" -> "voxê")
     * 3. Arrastar vogais (ex: "mano" -> "maaano", "cara" -> "caara")
     * 4. Tropeçar em duas consoantes/letras internas adjacentes
     */
    private String slurWord(String word, DrunkStage stage) {
        if (word.length() < 2) return word;

        // Separa pontuação final (!, ?, ., ,) para não bagunçar
        String punctuation = "";
        int end = word.length();
        while (end > 0 && "!?,.;:~".indexOf(word.charAt(end - 1)) != -1) {
            end--;
        }
        if (end < word.length()) {
            punctuation = word.substring(end);
            word = word.substring(0, end);
        }
        if (word.length() < 2) return word + punctuation;

        int roll = r.nextInt(4);

        // Efeito 0: Engolir o 'r' final de verbos ("falar" -> "falá", "beber" -> "bebê")
        if (roll == 0 && word.length() >= 4 && (word.endsWith("ar") || word.endsWith("er") || word.endsWith("ir"))) {
            String base = word.substring(0, word.length() - 2);
            char v = word.charAt(word.length() - 2);
            char accented = (v == 'a') ? 'á' : (v == 'e') ? 'ê' : 'i';
            return base + accented + punctuation;
        }

        // Efeito 1: Língua presa / chiado ("s", "ç", "ce", "ci" -> "sh" ou "x")
        if (roll == 1) {
            String slurred = word
                    .replaceFirst("(?i)ss", "sh")
                    .replaceFirst("(?i)ç", "x")
                    .replaceFirst("(?i)ce", "she")
                    .replaceFirst("(?i)ci", "shi");
            if (slurred.equals(word) && (word.contains("s") || word.contains("S"))) {
                slurred = word.replaceFirst("(?i)s", r.nextBoolean() ? "sh" : "x");
            }
            if (!slurred.equals(word)) {
                return slurred + punctuation;
            }
        }

        // Efeito 2: Arrastar uma vogal da palavra ("mano" -> "maaano")
        if (roll == 2 || word.length() <= 3) {
            for (int tries = 0; tries < 5; tries++) {
                int pos = r.nextInt(word.length());
                char c = word.charAt(pos);
                if ("aeiouáéíóúâêôãõAEIOUÁÉÍÓÚÂÊÔÃÕ".indexOf(c) != -1) {
                    int repeatCount = (stage == DrunkStage.BEBO_3 && r.nextBoolean()) ? 2 : 1;
                    StringBuilder stretch = new StringBuilder();
                    for (int k = 0; k < repeatCount; k++) stretch.append(c);
                    return word.substring(0, pos + 1) + stretch + word.substring(pos + 1) + punctuation;
                }
            }
        }

        // Efeito 3: Trocar duas letras internas adjacentes (sem mexer na primeira letra para continuar legível)
        if (word.length() >= 4) {
            int i = 1 + r.nextInt(word.length() - 2);
            char a = word.charAt(i);
            char b = word.charAt(i + 1);
            if (a != b) {
                return word.substring(0, i) + b + a + word.substring(i + 2) + punctuation;
            }
        }

        // Fallback: arrastar a última letra se for vogal
        char last = word.charAt(word.length() - 1);
        if ("aeiouAEIOU".indexOf(last) != -1) {
            return word + last + punctuation;
        }
        return word + punctuation;
    }
}
