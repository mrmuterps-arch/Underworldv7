package ru.mrmuter.underworld;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * В подмире обычные игроки принудительно переводятся в ADVENTURE
 * (нельзя ломать/ставить без спец-предметов — «музейный» режим).
 * Хранители (право underworld.build) сохраняют свой режим и могут строить.
 * При выходе прежний режим восстанавливается.
 */
public class AdventureModeListener implements Listener {

    private final UnderworldPlugin plugin;
    private final Map<UUID, GameMode> previous = new HashMap<>();

    public AdventureModeListener(UnderworldPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Небольшая задержка: гарантируем, что игрок уже в новом мире
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(event.getPlayer()), 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Чистим память, чтобы не копилась
        previous.remove(event.getPlayer().getUniqueId());
    }

    private void apply(Player player) {
        if (!plugin.getConfig().getBoolean("force-adventure", true)) return;

        boolean inUnderworld = player.getWorld().equals(plugin.getUnderworld());
        UUID id = player.getUniqueId();

        if (inUnderworld) {
            if (player.hasPermission("underworld.build")) {
                plugin.getLogger().info("[ADV] " + player.getName()
                        + " в подмире, но имеет underworld.build (ОП?) — режим НЕ меняем.");
                return; // Хранителей не трогаем
            }
            if (player.getGameMode() != GameMode.ADVENTURE) {
                previous.put(id, player.getGameMode());
                player.setGameMode(GameMode.ADVENTURE);
                plugin.getLogger().info("[ADV] " + player.getName() + " -> ADVENTURE");
            }
        } else {
            // Вышел из подмира — вернуть прежний режим
            GameMode prev = previous.remove(id);
            if (prev != null && player.getGameMode() == GameMode.ADVENTURE) {
                player.setGameMode(prev);
            }
        }
    }
}
