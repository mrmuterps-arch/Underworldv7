package ru.mandarin.chainbind;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит состояние всех связанных игроков и читает настройки из config.yml.
 * Состояние живёт только в памяти — специально не сохраняется между
 * перезапусками сервера (чтобы никто не "застрял" навсегда из-за краша).
 */
public class BindManager {

    /** Информация о связывании конкретного игрока. */
    public static final class BindInfo {
        public final UUID binder;
        public volatile Location lockLocation;
        public volatile long lastStruggleAttemptMs;

        public BindInfo(UUID binder, Location lockLocation) {
            this.binder = binder;
            this.lockLocation = lockLocation;
            this.lastStruggleAttemptMs = 0L;
        }
    }

    private final ChainBindPlugin plugin;
    private final Map<UUID, BindInfo> bound = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // Закэшированные настройки (перечитываются в reloadSettings())
    private String bindItem;
    private String releaseItem;
    private boolean consumeBindItem;
    private boolean damageReleaseItem;
    private double breakFreeChance;
    private long breakFreeCooldownMs;
    private boolean allowChainChain;
    private boolean releaseOnQuit;
    private boolean effectsEnabled;
    private Sound bindSound;
    private Sound releaseSound;
    private Sound struggleFailSound;

    public BindManager(ChainBindPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        FileConfiguration cfg = plugin.getConfig();
        this.bindItem = cfg.getString("bind-item", "CHAIN");
        this.releaseItem = cfg.getString("release-item", "SHEARS");
        this.consumeBindItem = cfg.getBoolean("consume-bind-item", true);
        this.damageReleaseItem = cfg.getBoolean("damage-release-item", true);
        this.breakFreeChance = cfg.getDouble("break-free-chance", 0.08);
        this.breakFreeCooldownMs = cfg.getLong("break-free-cooldown-ms", 600L);
        this.allowChainChain = cfg.getBoolean("allow-chain-chain", false);
        this.releaseOnQuit = cfg.getBoolean("release-on-quit", true);
        this.effectsEnabled = cfg.getBoolean("effects.enabled", true);
        this.bindSound = parseSound(cfg.getString("effects.bind-sound"), Sound.BLOCK_CHAIN_PLACE);
        this.releaseSound = parseSound(cfg.getString("effects.release-sound"), Sound.BLOCK_CHAIN_BREAK);
        this.struggleFailSound = parseSound(cfg.getString("effects.struggle-fail-sound"), Sound.ENTITY_CHICKEN_HURT);
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null) return fallback;
        try {
            return Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Некорректный звук в конфиге: '" + name + "', использую значение по умолчанию.");
            return fallback;
        }
    }

    // ------------------------------------------------------------------
    // Геттеры настроек
    // ------------------------------------------------------------------

    public String getBindItemName() {
        return bindItem;
    }

    public String getReleaseItemName() {
        return releaseItem;
    }

    public boolean isConsumeBindItem() {
        return consumeBindItem;
    }

    public boolean isDamageReleaseItem() {
        return damageReleaseItem;
    }

    public double getBreakFreeChance() {
        return breakFreeChance;
    }

    public long getBreakFreeCooldownMs() {
        return breakFreeCooldownMs;
    }

    public boolean isAllowChainChain() {
        return allowChainChain;
    }

    public boolean isReleaseOnQuit() {
        return releaseOnQuit;
    }

    // ------------------------------------------------------------------
    // Состояние связывания
    // ------------------------------------------------------------------

    public boolean isBound(UUID playerId) {
        return bound.containsKey(playerId);
    }

    public BindInfo getBindInfo(UUID playerId) {
        return bound.get(playerId);
    }

    public int getBoundCount() {
        return bound.size();
    }

    public void bind(Player target, Player binder) {
        BindInfo info = new BindInfo(binder.getUniqueId(), target.getLocation().clone());
        bound.put(target.getUniqueId(), info);
        playBindEffects(target);
    }

    /**
     * Снимает путы. Возвращает true, если игрок реально был связан.
     */
    public boolean release(UUID playerId) {
        BindInfo removed = bound.remove(playerId);
        return removed != null;
    }

    /** Обновляет "точку фиксации" — вызывается, когда движение разрешено (например, был отменён телепорт назад). */
    public void updateLockLocation(UUID playerId, Location location) {
        BindInfo info = bound.get(playerId);
        if (info != null) {
            info.lockLocation = location.clone();
        }
    }

    /**
     * Попытка вырваться нажатием Shift.
     * @return true, если попытка удалась и игрок освобождён
     */
    public boolean attemptStruggle(Player target) {
        BindInfo info = bound.get(target.getUniqueId());
        if (info == null) {
            return false;
        }
        return random.nextDouble() < breakFreeChance;
    }

    public boolean isOnStruggleCooldown(Player target) {
        BindInfo info = bound.get(target.getUniqueId());
        if (info == null) return false;
        long now = System.currentTimeMillis();
        return (now - info.lastStruggleAttemptMs) < breakFreeCooldownMs;
    }

    public void markStruggleAttempt(Player target) {
        BindInfo info = bound.get(target.getUniqueId());
        if (info != null) {
            info.lastStruggleAttemptMs = System.currentTimeMillis();
        }
    }

    public void releaseAll() {
        bound.clear();
    }

    // ------------------------------------------------------------------
    // Эффекты и сообщения
    // ------------------------------------------------------------------

    public void playBindEffects(Player target) {
        if (!effectsEnabled) return;
        target.getWorld().playSound(target.getLocation(), bindSound, 1.0f, 1.0f);
        target.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.01);
    }

    public void playReleaseEffects(Player target) {
        if (!effectsEnabled) return;
        target.getWorld().playSound(target.getLocation(), releaseSound, 1.0f, 1.0f);
        target.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, target.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
    }

    public void playStruggleFailEffects(Player target) {
        if (!effectsEnabled) return;
        target.getWorld().playSound(target.getLocation(), struggleFailSound, 0.6f, 1.4f);
    }

    public String msg(String key) {
        String raw = plugin.getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key, String placeholder, String value) {
        return msg(key).replace(placeholder, value);
    }

    /** То же сообщение, но как Adventure Component — нужен для sendActionBar(). */
    public Component msgComponent(String key) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getConfig().getString("messages." + key, "")
        );
    }
}
