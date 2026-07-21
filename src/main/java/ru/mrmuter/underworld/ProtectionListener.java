package ru.mrmuter.underworld;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * В подмире строить и ломать могут только "Хранители" —
 * игроки с правом underworld.build (как Soul Keepers в видео).
 */
public class ProtectionListener implements Listener {

    private final UnderworldPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ProtectionListener(UnderworldPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer())) event.setCancelled(true);
    }

    private boolean deny(Player player) {
        if (!player.getWorld().equals(plugin.getUnderworld())) return false;
        if (player.hasPermission("underworld.build")) return false;
        player.sendMessage(mm.deserialize(
                plugin.getConfig().getString("messages.no-build", "<dark_gray>Нельзя.")));
        return true;
    }
}
