package ru.mandarin.bamboosmoke;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SmokeManager {

    public static final int MAX_LEVEL = 10;
    // Каждые столько "тиков" менеджера (1 тик = 1 секунда) уровень падает на 1, если игрок не курит.
    private static final int DECAY_EVERY_SECONDS = 12;
    // Раз в сколько секунд в чат прилетает случайная "мысль" (не перекрывает шкалу — шлётся отдельным сообщением).
    private static final int THOUGHT_EVERY_SECONDS = 15;

    private final BambooSmokePlugin plugin;
    private final Random random = new Random();

    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> decayCounters = new HashMap<>();
    // Раз в несколько секунд шлём "мысль" в чат.
    private final Map<UUID, Integer> thoughtCounters = new HashMap<>();

    public SmokeManager(BambooSmokePlugin plugin) {
        this.plugin = plugin;
    }

    public int getLevel(Player player) {
        return levels.getOrDefault(player.getUniqueId(), 0);
    }

    /** Вызывается когда игрок скурил бамбук. */
    public void smoke(Player player) {
        UUID id = player.getUniqueId();
        int newLevel = Math.min(MAX_LEVEL, getLevel(player) + 1);
        levels.put(id, newLevel);
        decayCounters.put(id, 0);

        Location loc = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 25, 0.3, 0.3, 0.3, 0.01);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 6, 0.2, 0.2, 0.2, 0.01);
        player.getWorld().playSound(loc, Sound.ENTITY_PANDA_EAT, 0.6f, 0.7f);

        player.sendMessage("§7[§2БАМБУК§7] §fВы скурили бамбук. §7(Обкуренность: §e" + newLevel + "/" + MAX_LEVEL + "§7)");

        applyEffects(player, newLevel);
        updateBar(player, newLevel);
    }

    /** Молоко мгновенно отрезвляет. */
    public void drinkMilk(Player player) {
        UUID id = player.getUniqueId();
        if (getLevel(player) <= 0) {
            return;
        }
        levels.put(id, 0);
        decayCounters.put(id, 0);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.sendMessage("§7[§bМОЛОКО§7] §fГолова прояснилась. Обкуренность сброшена.");
        removeBar(player);
    }

    private void applyEffects(Player player, int level) {
        if (level <= 0) {
            player.removePotionEffect(PotionEffectType.NAUSEA);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            return;
        }
        int nauseaAmp = Math.max(0, (level - 1) / 3);      // 0..3
        int slownessAmp = Math.max(0, (level - 1) / 4);    // 0..2
        int durationTicks = 20 * 15; // 15 секунд, обновляется при каждом новом бамбуке / тике

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationTicks, nauseaAmp, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, slownessAmp, true, false));
    }

    private void updateBar(Player player, int level) {
        UUID id = player.getUniqueId();
        BossBar bar = bars.get(id);
        if (bar == null) {
            bar = Bukkit.createBossBar("Обкуренность", BarColor.GREEN, BarStyle.SEGMENTED_10);
            bar.addPlayer(player);
            bars.put(id, bar);
        }
        double progress = (double) level / MAX_LEVEL;
        bar.setProgress(Math.max(0, Math.min(1, progress)));
        bar.setColor(barColorFor(level));
        bar.setTitle(scaleTitle(level));
        bar.setVisible(level > 0);
    }

    private void removeBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private BarColor barColorFor(int level) {
        if (level <= 3) return BarColor.GREEN;
        if (level <= 6) return BarColor.YELLOW;
        return BarColor.RED;
    }

    private String scaleTitle(int level) {
        StringBuilder sb = new StringBuilder("§2Обкуренность: ");
        for (int i = 1; i <= MAX_LEVEL; i++) {
            sb.append(i <= level ? "█" : "░");
        }
        sb.append(" §7(").append(level).append("/").append(MAX_LEVEL).append(")");
        return sb.toString();
    }

    /** Вызывается раз в секунду планировщиком. Затухание, шатание, смена заголовка бара. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            int level = getLevel(player);
            if (level <= 0) {
                continue;
            }

            // Шатание — случайный горизонтальный импульс, шанс растёт с уровнем.
            int stumbleChance = Math.min(80, level * 8);
            if (random.nextInt(100) < stumbleChance) {
                double strength = 0.05 + (level * 0.02);
                double dx = (random.nextDouble() - 0.5) * strength;
                double dz = (random.nextDouble() - 0.5) * strength;
                Vector velocity = player.getVelocity();
                player.setVelocity(velocity.add(new Vector(dx, 0, dz)));
            }

            // Освежаем зелья, пока уровень не упал (иначе они истекут раньше, чем шкала).
            applyEffects(player, level);

            // Шкала в BossBar всегда остаётся шкалой — не перекрываем её "мыслями",
            // чтобы её всегда можно было спокойно прочитать.
            BossBar bar = bars.get(id);
            if (bar != null) {
                bar.setTitle(scaleTitle(level));
            }

            // Раз в ~15 секунд шлём случайную "мысль" отдельным сообщением в чат —
            // так она не пропадает мгновенно и остаётся в истории чата.
            int tCounter = thoughtCounters.getOrDefault(id, 0) + 1;
            if (tCounter >= THOUGHT_EVERY_SECONDS) {
                tCounter = 0;
                String thought = ThoughtsUtil.randomThought(level);
                if (thought != null) {
                    player.sendMessage("§d§o" + thought);
                }
            }
            thoughtCounters.put(id, tCounter);

            // Затухание уровня со временем, если не курить дальше.
            int decayCounter = decayCounters.getOrDefault(id, 0) + 1;
            if (decayCounter >= DECAY_EVERY_SECONDS) {
                decayCounter = 0;
                int newLevel = level - 1;
                levels.put(id, newLevel);
                if (newLevel <= 0) {
                    applyEffects(player, 0);
                    removeBar(player);
                } else {
                    updateBar(player, newLevel);
                }
            }
            decayCounters.put(id, decayCounter);
        }
    }

    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        levels.clear();
        decayCounters.clear();
        thoughtCounters.clear();
    }
}
