package ru.mrmuter.underworld;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * Кастомный лут Мёртвой пустоши: микс еды, ресурсов, ценностей и лорных записок.
 */
public final class LootChests {

    private LootChests() {}

    private static final List<String> NOTES = List.of(
            "Они пришли из разломов...",
            "Вирус не щадит никого. Уходите, пока можете.",
            "Мы держали оборону три дня. Больше не выйдет.",
            "Не смотрите в каньоны слишком долго.",
            "Хранители знают правду, но молчат.",
            "Небо здесь никогда не светлеет.");

    public static void fill(Inventory inv, Random random) {
        int items = 4 + random.nextInt(5); // 4..8 стаков
        for (int i = 0; i < items; i++) {
            inv.setItem(random.nextInt(inv.getSize()), roll(random));
        }
    }

    private static ItemStack roll(Random r) {
        int roll = r.nextInt(100);
        // Еда и выживание
        if (roll < 15) return new ItemStack(Material.BREAD, 2 + r.nextInt(4));
        if (roll < 27) return new ItemStack(Material.GOLDEN_CARROT, 1 + r.nextInt(3));
        if (roll < 40) return new ItemStack(Material.TORCH, 4 + r.nextInt(8));
        // Ресурсы
        if (roll < 52) return new ItemStack(Material.IRON_INGOT, 1 + r.nextInt(3));
        if (roll < 62) return new ItemStack(Material.GOLD_INGOT, 1 + r.nextInt(3));
        if (roll < 70) return new ItemStack(Material.AMETHYST_SHARD, 1 + r.nextInt(2));
        if (roll < 75) return new ItemStack(Material.DIAMOND, 1);
        // Ценности
        if (roll < 80) return new ItemStack(Material.NETHERITE_SCRAP, 1);
        if (roll < 85) return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1);
        if (roll < 90) return new ItemStack(Material.EXPERIENCE_BOTTLE, 3 + r.nextInt(6));
        // Лор
        return lorePaper(r);
    }

    private static ItemStack lorePaper(Random r) {
        ItemStack paper = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Обрывок дневника"));
            meta.lore(List.of(Component.text(NOTES.get(r.nextInt(NOTES.size())))));
            paper.setItemMeta(meta);
        }
        return paper;
    }
}
