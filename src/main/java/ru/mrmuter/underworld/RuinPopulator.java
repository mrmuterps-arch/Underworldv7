package ru.mrmuter.underworld;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Разбрасывает по пустоши редкие руины из потрескавшегося глубинного сланца
 * с сундуком (лут-таблица Ancient City) и скалк-катализатором внутри.
 */
public class RuinPopulator extends BlockPopulator {

    private final int chance;      // 1 из N чанков
    private final int scanTop;     // с какой высоты искать поверхность

    public RuinPopulator(int chance, int anchorTopY) {
        this.chance = Math.max(2, chance);
        this.scanTop = anchorTopY + 20;
    }

    @Override
    public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (random.nextInt(chance) != 0) return;

        int cx = chunkX * 16 + 8;
        int cz = chunkZ * 16 + 8;

        // Ищем поверхность в центре чанка
        int surfaceY = findSurface(region, cx, cz);
        if (surfaceY == Integer.MIN_VALUE) return; // над каньоном/пустотой — руину не ставим

        buildRuin(region, random, cx, surfaceY, cz);
    }

    private int findSurface(LimitedRegion region, int x, int z) {
        for (int y = scanTop; y >= scanTop - 60; y--) {
            if (!region.isInRegion(x, y, z)) continue;
            Material m = region.getType(x, y, z);
            if (m.isSolid()) return y;
        }
        return Integer.MIN_VALUE;
    }

    private void buildRuin(LimitedRegion region, Random random, int cx, int baseY, int cz) {
        int floorY = baseY + 1;
        int half = 3; // руина 7x7

        // Пол + низкие обломанные стены
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!region.isInRegion(x, floorY, z)) continue;

                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;

                // пол
                setSafe(region, x, baseY, z, brick(random));

                if (edge) {
                    // стена рваной высоты 1..3, местами провал
                    int wallH = 1 + random.nextInt(3);
                    for (int h = 1; h <= wallH; h++) {
                        if (random.nextInt(5) == 0) continue; // дыры в стене
                        setSafe(region, x, baseY + h, z, brick(random));
                    }
                }
            }
        }

        // Сундук с лутом в центре
        if (region.isInRegion(cx, floorY, cz)) {
            region.setType(cx, floorY, cz, Material.CHEST);
            try {
                BlockState st = region.getBlockState(cx, floorY, cz);
                if (st instanceof Chest chest) {
                    LootChests.fill(chest.getSnapshotInventory(), random);
                    st.update(true, false);
                }
            } catch (Throwable ignored) {
                // если лут-API капризит — сундук просто останется пустым, руина не сломается
            }
        }

        // Скалк-катализатор и фонарь душ как декор
        setSafe(region, cx + 1, floorY, cz + 1, Material.SCULK_CATALYST);
        setSafe(region, cx - 1, floorY, cz - 1, Material.SOUL_LANTERN);
    }

    private void setSafe(LimitedRegion region, int x, int y, int z, Material m) {
        if (region.isInRegion(x, y, z)) region.setType(x, y, z, m);
    }

    private Material brick(Random random) {
        int r = random.nextInt(10);
        if (r < 5) return Material.DEEPSLATE_BRICKS;
        if (r < 8) return Material.CRACKED_DEEPSLATE_BRICKS;
        return Material.DEEPSLATE_TILES;
    }
}
