package ru.mrmuter.underworld;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Главная магия:
 * 1) Падаешь ниже порога в обычном мире или аду → вместо смерти в пустоте
 *    тебя "проваливает" в Забытое измерение.
 * 2) Падаешь в пустоту уже в подмире → "побег" обратно в обычный мир.
 * 3) Урон от пустоты (VOID) в этих мирах отменяем, чтобы игрок не умер
 *    за миг до телепорта.
 */
public class VoidFallListener implements Listener {

    private final UnderworldPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public VoidFallListener(UnderworldPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Оптимизация: реагируем только если реально сменился блок по Y
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        World world = player.getWorld();
        double y = event.getTo().getY();

        World underworld = plugin.getUnderworld();
        if (underworld == null) return;

        // --- Побег ИЗ подмира ---
        if (world.equals(underworld)) {
            if (y < plugin.getConfig().getInt("underworld-escape-y", -32)) {
                World overworld = plugin.getMainWorld();
                Location spawn = overworld.getSpawnLocation().clone().add(0.5, 0, 0.5);
                player.teleportAsync(spawn).thenRun(() -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 8, 0));
                    player.playSound(player, Sound.ITEM_TRIDENT_RETURN, 1f, 0.5f);
                    player.sendMessage(mm.deserialize(
                            plugin.getConfig().getString("messages.escape", "<gray>Ты сбежал из пустоты.")));
                });
            }
            return;
        }

        // --- Затягивание В подмир ---
        int threshold = switch (world.getEnvironment()) {
            case NORMAL -> plugin.getConfig().getInt("overworld-fall-y", -70);
            case NETHER -> plugin.getConfig().getInt("nether-fall-y", -6);
            default -> Integer.MIN_VALUE; // Энд не трогаем — там своя механика пустоты
        };

        if (threshold != Integer.MIN_VALUE && y < threshold) {
            pullIntoUnderworld(player);
        }
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        // Не даём пустоте убить игрока в мирах, где работает затягивание
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        if (!(event.getEntity() instanceof Player player)) return;

        World.Environment env = player.getWorld().getEnvironment();
        boolean protectedWorld = env == World.Environment.NORMAL
                || env == World.Environment.NETHER
                || player.getWorld().equals(plugin.getUnderworld());
        if (protectedWorld) {
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        World uw = plugin.getUnderworld();
        if (uw == null) return;
        Location spawn = event.getSpawnLocation();
        if (!uw.equals(spawn.getWorld())) return;
        // Новичок или игрок на точке спавна измерения — переносим в обычный мир.
        // Тот, кто вышел из игры в глубине пустоши, останется там же (это честно).
        // Переносим ТОЛЬКО тех, кто ни разу не играл (первый вход).
        // Игрок, вышедший в глубине пустоши, при заходе останется ровно там же.
        World main = plugin.getMainWorld();
        if (!event.getPlayer().hasPlayedBefore() && !main.equals(uw)) {
            event.setSpawnLocation(main.getSpawnLocation().clone().add(0.5, 0, 0.5));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        World uw = plugin.getUnderworld();
        if (uw == null) return;
        // Никаких возрождений в пустоши: умер — очнулся в обычном мире.
        if (uw.equals(event.getRespawnLocation().getWorld())) {
            event.setRespawnLocation(plugin.getMainWorld().getSpawnLocation().clone().add(0.5, 0, 0.5));
        }
    }

    private void pullIntoUnderworld(Player player) {
        World underworld = plugin.getUnderworld();
        int platformY = plugin.getConfig().getInt("platform-y", 64);

        // Появляемся ВЫСОКО над платформой — эффект долгого падения в неизвестность
        Location target = new Location(underworld, 8.5, platformY + 60, 8.5);

        player.teleportAsync(target).thenRun(() -> {
            int dark = plugin.getConfig().getInt("effects.darkness-seconds", 8);
            int slow = plugin.getConfig().getInt("effects.slow-falling-seconds", 12);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * dark, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * slow, 0));
            player.setFallDistance(0f);
            player.playSound(player, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.6f);
            player.sendMessage(mm.deserialize(
                    plugin.getConfig().getString("messages.enter", "<dark_gray>Забытое измерение...")));
        });
    }
}
