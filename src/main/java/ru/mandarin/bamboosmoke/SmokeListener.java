package ru.mandarin.bamboosmoke;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class SmokeListener implements Listener {

    private final BambooSmokePlugin plugin;
    private final SmokeManager smokeManager;

    public SmokeListener(BambooSmokePlugin plugin, SmokeManager smokeManager) {
        this.plugin = plugin;
        this.smokeManager = smokeManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // не дублируем событие для второй руки
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BAMBOO) {
            return;
        }

        Player player = event.getPlayer();

        // Обычный ПКМ (без шифта) — не трогаем событие вообще, бамбук сажается
        // как в ваниле, что по блоку, что по воздуху (если рядом есть куда посадить).
        // RIGHT_CLICK_AIR как признак "курения" не подходит: он срабатывает только
        // если вообще нет ни одного блока в радиусе досягаемости, а на практике
        // игрок почти всегда целится в землю/траву — то есть RIGHT_CLICK_BLOCK.
        if (!player.isSneaking()) {
            return;
        }

        // Шифт + ПКМ (по блоку или по воздуху — не важно) — курим.
        event.setCancelled(true);

        if (smokeManager.getLevel(player) >= SmokeManager.MAX_LEVEL) {
            player.sendMessage("§7[§2БАМБУК§7] §fДальше уже некуда. Хватит на сегодня.");
            return;
        }

        item.setAmount(item.getAmount() - 1);
        smokeManager.smoke(player);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            smokeManager.drinkMilk(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        int level = smokeManager.getLevel(player);
        if (level <= 0) {
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        String garbled = ChatGarbler.garble(plain, level);
        event.message(Component.text(garbled));
    }
}
