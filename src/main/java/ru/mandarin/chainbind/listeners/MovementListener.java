package ru.mandarin.chainbind.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import ru.mandarin.chainbind.BindManager;

/**
 * Не даёт связанному игроку сдвинуться с места.
 * Осмотр (вращение камеры) остаётся полностью свободным — блокируется
 * только изменение X/Y/Z координат.
 */
public class MovementListener implements Listener {

    private static final double EPSILON = 0.001;

    private final BindManager bindManager;

    public MovementListener(BindManager bindManager) {
        this.bindManager = bindManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!bindManager.isBound(player.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        boolean movedTooFar = from.distanceSquared(withSameYawPitch(to, from)) > EPSILON;
        if (!movedTooFar) {
            // Разрешаем менять только yaw/pitch — координаты остаются прежними
            return;
        }

        // Возвращаем игрока в точку фиксации, но сохраняем его текущий взгляд,
        // чтобы он мог свободно осматриваться, оставаясь на месте.
        BindManager.BindInfo info = bindManager.getBindInfo(player.getUniqueId());
        Location lock = (info != null && info.lockLocation != null) ? info.lockLocation.clone() : from.clone();
        lock.setYaw(to.getYaw());
        lock.setPitch(to.getPitch());
        event.setTo(lock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (bindManager.isBound(player.getUniqueId())) {
            // гасим любой импульс (взрывы, эликсиры отбрасывания и т.п.)
            event.setCancelled(true);
        }
    }

    private Location withSameYawPitch(Location to, Location from) {
        Location clone = to.clone();
        clone.setYaw(from.getYaw());
        clone.setPitch(from.getPitch());
        return clone;
    }
}
