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

        if (stage == DrunkStage.SOBRIO) {
            return;
        }

        e.setMessage(applyDrunkSpeech(e.getMessage(), stage));
    }

    public String applyDrunkSpeech(String message, DrunkStage stage) {
        if (stage == DrunkStage.SOBRIO) return message;

        double wordChance;
        double hiccupChance;
        double stutterChance;
        double cebolinhaSentenceChance; // Chance menor que os outros para trocar R por L na frase

        switch (stage) {
            case BEBO_1: // 21% a 50% (Levemente Bêbado)
                wordChance = 0.25;
                hiccupChance = 0.10;
                stutterChance = 0.08;
                cebolinhaSentenceChance = 0.10;
                break;
            case BEBO_2: // 51% a 70% (Groguê)
                wordChance = 0.45;
                hiccupChance = 0.25;
                stutterChance = 0.15;
                cebolinhaSentenceChance = 0.16;
                break;
            case BEBO_3: // 71% a 100% (Bêbado)
            default:
                wordChance = 0.70;
                hiccupChance = 0.55;
                stutterChance = 0.25;
                cebolinhaSentenceChance = 0.22;
                break;
        }

        boolean applyCebolinhaInPhrase = r.nextDouble() < cebolinhaSentenceChance;

        String[] words = message.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int idx = 0; idx < words.length; idx++) {
            String word = words[idx];

            if (isUrlOrPlayerName(word)) {
                result.append(word).append(" ");
                continue;
            }

            if (idx > 0 && word.length() >= 2 && word.length() <= 4 && r.nextDouble() < stutterChance) {
                if (r.nextBoolean()) {
                    result.append(word.charAt(0)).append("-");
                } else {
                    result.append(word).append("... ");
                }
            }

            if (applyCebolinhaInPhrase && hasR(word)) {
                word = applyCebolinha(word);
            } else if (r.nextDouble() < wordChance) {
                word = slurWord(word, stage);
            }

            result.append(word).append(" ");
        }

        String finalMsg = result.toString().trim();

        if (r.nextDouble() < hiccupChance) {
            finalMsg += HICCUPS[r.nextInt(HICCUPS.length)];
        }

        return finalMsg;
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

    private boolean hasR(String word) {
        return word.indexOf('r') != -1 || word.indexOf('R') != -1;
    }

    /**
     * Troca R / RR por L igual ao Cebolinha (preservando maiúsculas/minúsculas).
     */
    private String applyCebolinha(String word) {
        String replaced = word
                .replace("RR", "L")
                .replace("Rr", "L")
                .replace("rr", "l");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < replaced.length(); i++) {
            char c = replaced.charAt(i);
            // Evita trocar o último 'r' de verbos no infinitivo por 'l' (ex: "falar" -> "falá" fica mais natural que "falal")
            if ((c == 'r' || c == 'R') && i == replaced.length() - 1 && replaced.length() >= 3) {
                sb.append(c);
            } else if (c == 'r') {
                sb.append('l');
            } else if (c == 'R') {
                sb.append('L');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String slurWord(String word, DrunkStage stage) {
        if (word.length() < 2) return word;

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

        // Chance menor de trocar R por L nesta palavra individualmente (estilo Cebolinha)
        if (hasR(word) && r.nextDouble() < 0.14) {
            return applyCebolinha(word) + punctuation;
        }

        int roll = r.nextInt(5);

        // Efeito 0: Sumir com os 'R' da palavra (ex: "barril" -> "bail", "cerveja" -> "ceveja", "falar" -> "falá")
        if ((roll == 0 || (hasR(word) && r.nextBoolean())) && hasR(word) && word.length() >= 3) {
            if (word.endsWith("ar") || word.endsWith("er") || word.endsWith("ir")) {
                String base = word.substring(0, word.length() - 2).replace("r", "").replace("R", "");
                char v = word.charAt(word.length() - 2);
                char accented = (v == 'a') ? 'á' : (v == 'e') ? 'ê' : 'i';
                if (!base.isEmpty()) {
                    return base + accented + punctuation;
                }
            }
            String withoutR = word.replace("r", "").replace("R", "");
            if (!withoutR.isEmpty()) {
                return withoutR + punctuation;
            }
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

        // Efeito 3/4: Trocar duas letras internas adjacentes
        if (word.length() >= 4) {
            int i = 1 + r.nextInt(word.length() - 2);
            char a = word.charAt(i);
            char b = word.charAt(i + 1);
            if (a != b) {
                return word.substring(0, i) + b + a + word.substring(i + 2) + punctuation;
            }
        }

        char last = word.charAt(word.length() - 1);
        if ("aeiouAEIOU".indexOf(last) != -1) {
            return word + last + punctuation;
        }
        return word + punctuation;
    }
}
