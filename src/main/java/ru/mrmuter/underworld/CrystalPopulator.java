package ru.mrmuter.underworld;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * «Кристальный разлом» v2 — редкий КРАТЕР в земле:
 * яма 5x5 глубиной 2 со ступенькой-входом с юга,
 * аметист в стенах и на дне, сундук с лутом на дне.
 * Полностью проходим и лутается в режиме приключений.
 */
public class CrystalPopulator extends BlockPopulator {

    private final int chance;
    private final int scanTop;

    public CrystalPopulator(int chance, int anchorTopY) {
        this.chance = Math.max(20, chance);
        this.scanTop = anchorTopY + 25;
    }

    @Override
    public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (random.nextInt(chance) != 0) return;

        int cx = chunkX * 16 + 8;
        int cz = chunkZ * 16 + 8;

        int surfaceY = Integer.MIN_VALUE;
        for (int y = scanTop; y >= scanTop - 70; y--) {
            if (!region.isInRegion(cx, y, cz)) continue;
            if (region.getType(cx, y, cz).isSolid()) { surfaceY = y; break; }
        }
        if (surfaceY == Integer.MIN_VALUE) return;

        int floorY = surfaceY - 2; // дно кратера

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int r = Math.max(Math.abs(dx), Math.abs(dz));
                int x = cx + dx;
                int z = cz + dz;

                if (r <= 1) {
                    // дно из аметиста, над ним воздух
                    setSafe(region, x, floorY, z, random.nextInt(4) == 0
                            ? Material.BUDDING_AMETHYST : Material.AMETHYST_BLOCK);
                    for (int y = floorY + 1; y <= surfaceY + 1; y++) {
                        setSafe(region, x, y, z, Material.AIR);
                    }
                } else {
                    // стенки кратера: кальцит с прожилками аметиста
                    for (int y = floorY; y <= surfaceY; y++) {
                        Material m = random.nextInt(3) == 0 ? Material.AMETHYST_BLOCK : Material.CALCITE;
                        setSafe(region, x, y, z, m);
                    }
                }
            }
        }

        // ступенька-вход с юга: спуск на дно
        setSafe(region, cx, surfaceY, cz + 2, Material.CALCITE);
        setSafe(region, cx, surfaceY + 1, cz + 2, Material.AIR);
        setSafe(region, cx, surfaceY - 1, cz + 1, Material.CALCITE);
        setSafe(region, cx, surfaceY, cz + 1, Material.AIR);
        setSafe(region, cx, surfaceY + 1, cz + 1, Material.AIR);

        // кристаллы на дне
        setSafe(region, cx - 1, floorY + 1, cz - 1, Material.AMETHYST_CLUSTER);
        if (random.nextBoolean()) {
            setSafe(region, cx + 1, floorY + 1, cz - 1, Material.LARGE_AMETHYST_BUD);
        }

        // сундук с лутом на дне
        int chestX = cx + 1;
        int chestZ = cz + 1;
        if (region.isInRegion(chestX, floorY + 1, chestZ)) {
            region.setType(chestX, floorY + 1, chestZ, Material.CHEST);
            try {
                BlockState st = region.getBlockState(chestX, floorY + 1, chestZ);
                if (st instanceof Chest chest) {
                    LootChests.fill(chest.getSnapshotInventory(), random);
                    // бонус кратера: гарантированные осколки аметиста
                    chest.getSnapshotInventory().setItem(13,
                            new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD, 3 + random.nextInt(5)));
                    st.update(true, false);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void setSafe(LimitedRegion region, int x, int y, int z, Material m) {
        if (region.isInRegion(x, y, z)) region.setType(x, y, z, m);
    }
}
