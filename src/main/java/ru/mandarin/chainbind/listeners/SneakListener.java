package ru.mandarin.chainbind.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import ru.mandarin.chainbind.BindManager;
import ru.mandarin.chainbind.ChainBindPlugin;

public class SneakListener implements Listener {

    private final ChainBindPlugin plugin;
    private final BindManager bindManager;

    public SneakListener(ChainBindPlugin plugin, BindManager bindManager) {
        this.plugin = plugin;
        this.bindManager = bindManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        // Реагируем только на начало приседания (нажатие, а не отпускание Shift)
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        if (!bindManager.isBound(player.getUniqueId())) {
            return;
        }

        if (bindManager.isOnStruggleCooldown(player)) {
            player.sendActionBar(bindManager.msgComponent("struggle-cooldown"));
            return;
        }

        bindManager.markStruggleAttempt(player);
        boolean freed = bindManager.attemptStruggle(player);

        if (freed) {
            bindManager.release(player.getUniqueId());
            bindManager.playReleaseEffects(player);
            player.sendMessage(bindManager.msg("struggle-success"));
        } else {
            bindManager.playStruggleFailEffects(player);
            player.sendActionBar(bindManager.msgComponent("struggle-fail"));
        }
    }
}
