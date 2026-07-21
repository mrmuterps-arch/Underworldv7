package ru.mandarin.chainbind.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.mandarin.chainbind.BindManager;
import ru.mandarin.chainbind.ChainBindPlugin;

import java.util.UUID;

public class QuitListener implements Listener {

    private final ChainBindPlugin plugin;
    private final BindManager bindManager;

    public QuitListener(ChainBindPlugin plugin, BindManager bindManager) {
        this.plugin = plugin;
        this.bindManager = bindManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!bindManager.isReleaseOnQuit()) {
            return;
        }

        Player who = event.getPlayer();
        UUID id = who.getUniqueId();

        // Случай 1: сам связанный вышел с сервера
        if (bindManager.isBound(id)) {
            bindManager.release(id);
        }

        // Случай 2: вышел тот, кто связывал кого-то — освобождаем его жертву
        for (UUID boundId : java.util.List.copyOf(getAllBoundIdsBoundBy(id))) {
            bindManager.release(boundId);
            Player victim = Bukkit.getPlayer(boundId);
            if (victim != null && victim.isOnline()) {
                bindManager.playReleaseEffects(victim);
                victim.sendMessage(bindManager.msg("released-on-quit"));
            }
        }
    }

    private java.util.List<UUID> getAllBoundIdsBoundBy(UUID binderId) {
        java.util.List<UUID> result = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            BindManager.BindInfo info = bindManager.getBindInfo(p.getUniqueId());
            if (info != null && info.binder.equals(binderId)) {
                result.add(p.getUniqueId());
            }
        }
        return result;
    }
}
