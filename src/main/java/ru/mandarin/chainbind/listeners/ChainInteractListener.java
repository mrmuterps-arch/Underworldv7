package ru.mandarin.chainbind.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.mandarin.chainbind.BindManager;
import ru.mandarin.chainbind.ChainBindPlugin;

public class ChainInteractListener implements Listener {

    private final ChainBindPlugin plugin;
    private final BindManager bindManager;

    public ChainInteractListener(ChainBindPlugin plugin, BindManager bindManager) {
        this.plugin = plugin;
        this.bindManager = bindManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        // Игнорируем клик офф-хендом, чтобы событие не срабатывало дважды на одно ПКМ
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player source = event.getPlayer();
        ItemStack itemInHand = source.getInventory().getItemInMainHand();
        Material type = itemInHand.getType();

        Material bindMaterial = safeMaterial(bindManager.getBindItemName(), Material.IRON_CHAIN);
        Material releaseMaterial = safeMaterial(bindManager.getReleaseItemName(), Material.SHEARS);

        if (type == bindMaterial) {
            handleBind(event, source, target, itemInHand);
        } else if (type == releaseMaterial) {
            handleRelease(event, source, target, itemInHand);
        }
    }

    private void handleBind(PlayerInteractEntityEvent event, Player source, Player target, ItemStack itemInHand) {
        event.setCancelled(true);

        if (!source.hasPermission("chainbind.use")) {
            source.sendMessage(bindManager.msg("no-permission"));
            return;
        }
        if (source.getUniqueId().equals(target.getUniqueId())) {
            source.sendMessage(bindManager.msg("cannot-bind-self"));
            return;
        }
        if (target.hasPermission("chainbind.bypass")) {
            source.sendMessage(bindManager.msg("bypass-target"));
            return;
        }
        if (bindManager.isBound(target.getUniqueId())) {
            source.sendMessage(bindManager.msg("already-bound"));
            return;
        }
        if (!bindManager.isAllowChainChain() && bindManager.isBound(source.getUniqueId())) {
            // связанный сам не может никого связать своей второй цепью
            return;
        }

        bindManager.bind(target, source);

        target.sendMessage(bindManager.msg("bound-target"));
        source.sendMessage(bindManager.msg("bound-source", "%target%", target.getName()));

        if (bindManager.isConsumeBindItem() && source.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }
    }

    private void handleRelease(PlayerInteractEntityEvent event, Player source, Player target, ItemStack itemInHand) {
        if (!bindManager.isBound(target.getUniqueId())) {
            // Ножницы по обычному игроку — не наше дело, пропускаем событие дальше
            return;
        }

        event.setCancelled(true);

        if (!source.hasPermission("chainbind.use")) {
            source.sendMessage(bindManager.msg("no-permission"));
            return;
        }

        boolean released = bindManager.release(target.getUniqueId());
        if (!released) {
            return;
        }

        bindManager.playReleaseEffects(target);
        target.sendMessage(bindManager.msg("released-by-shears"));
        source.sendMessage(bindManager.msg("released-source", "%target%", target.getName()));

        if (bindManager.isDamageReleaseItem() && source.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            damageItem(source, itemInHand, 1);
        }
    }

    private void damageItem(Player owner, ItemStack item, int amount) {
        if (item.getType().getMaxDurability() <= 0) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int newDamage = damageable.getDamage() + amount;
            if (newDamage >= item.getType().getMaxDurability()) {
                // предмет ломается
                owner.getInventory().getItemInMainHand().setAmount(0);
                owner.getWorld().playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                return;
            }
            damageable.setDamage(newDamage);
            item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) damageable);
        }
    }

    private Material safeMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Некорректный материал в конфиге: '" + name + "', использую значение по умолчанию.");
            return fallback;
        }
    }
}
