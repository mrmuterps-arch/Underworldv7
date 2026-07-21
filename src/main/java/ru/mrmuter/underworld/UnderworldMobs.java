package ru.mrmuter.underworld;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/**
 * Редкий спавн мобов Мёртвой пустоши:
 * - Зомби с случайным оружием в руке
 * - Зимогоры (Stray) в полной незеритовой броне
 * Экипировка НИКОГДА не выпадает (drop chance = 0).
 */
public class UnderworldMobs extends BukkitRunnable {

    private static final List<Material> ZOMBIE_WEAPONS = List.of(
            Material.STONE_SWORD, Material.STONE_AXE,
            Material.IRON_SWORD, Material.IRON_AXE,
            Material.GOLDEN_SWORD, Material.NETHERITE_SWORD);

    private final UnderworldPlugin plugin;
    private final Random random = new Random();

    public UnderworldMobs(UnderworldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        World w = plugin.getUnderworld();
        if (w == null) return;

        int chance = plugin.getConfig().getInt("mobs.spawn-chance-percent", 20);
        int cap = plugin.getConfig().getInt("mobs.max-near-player", 4);

        for (Player p : w.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (random.nextInt(100) >= chance) continue;
            if (countMonstersNear(p) >= cap) continue;
            for (int attempt = 0; attempt < 5; attempt++) { // до 5 попыток найти площадку
                if (trySpawn(w, p)) break;
            }
        }
    }

    private int countMonstersNear(Player p) {
        int n = 0;
        for (Entity e : p.getNearbyEntities(48, 48, 48)) {
            if (e instanceof Monster) n++;
        }
        return n;
    }

    private boolean trySpawn(World w, Player p) {
        int dx = (16 + random.nextInt(17)) * (random.nextBoolean() ? 1 : -1);
        int dz = (16 + random.nextInt(17)) * (random.nextBoolean() ? 1 : -1);
        int x = p.getLocation().getBlockX() + dx;
        int z = p.getLocation().getBlockZ() + dz;

        // Ищем твёрдую поверхность сканом сверху вниз (getHighestBlockYAt врёт в дырявом мире)
        int y = Integer.MIN_VALUE;
        for (int yy = 100; yy >= 20; yy--) {
            if (w.getBlockAt(x, yy, z).getType().isSolid()
                    && w.getBlockAt(x, yy + 1, z).getType().isAir()
                    && w.getBlockAt(x, yy + 2, z).getType().isAir()) {
                y = yy; break;
            }
        }
        if (y == Integer.MIN_VALUE) return false; // каньон/пустота — мимо

        Location loc = new Location(w, x + 0.5, y + 1.0, z + 0.5);

        if (random.nextInt(3) == 0) spawnStray(w, loc);
        else spawnZombie(w, loc);
        plugin.getLogger().info("[MOBS] спавн у " + p.getName() + " на Y=" + y);
        return true;
    }

    private void spawnStray(World w, Location loc) {
        Stray stray = (Stray) w.spawnEntity(loc, EntityType.STRAY);
        EntityEquipment eq = stray.getEquipment();
        if (eq == null) return;
        eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        noDrops(eq);
    }

    private void spawnZombie(World w, Location loc) {
        Zombie zombie = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
        EntityEquipment eq = zombie.getEquipment();
        if (eq == null) return;
        eq.setItemInMainHand(new ItemStack(ZOMBIE_WEAPONS.get(random.nextInt(ZOMBIE_WEAPONS.size()))));
        noDrops(eq);
    }

    private void noDrops(EntityEquipment eq) {
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }
}
