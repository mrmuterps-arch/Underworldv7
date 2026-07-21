package ru.mrmuter.underworld;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Заброшенные деревни Мёртвой пустоши (лор: вирус/война).
 *
 * Дома — не коробки: двускатные крыши из лестниц с коньком и свесами,
 * фахверковый каркас (столбы + верхняя обвязка), фронтоны, окна,
 * обрушенный угол, паутина, бочки и черепа внутри.
 *
 * Стили: 0 — фахверк (тёмный дуб + кальцит + сланцевая крыша),
 *        1 — обгоревший (ель + уголь + чернокамень, тлеющий очаг душ),
 *        2 — скелет дома (каркас и стропила без стен).
 */
public class VillagePopulator extends BlockPopulator {

    private final int regionChunks;
    private final int regionChancePercent;
    private final int houseChancePercent;
    private final int scanTop;

    public VillagePopulator(int regionChunks, int regionChancePercent, int houseChancePercent, int anchorTopY) {
        this.regionChunks = Math.max(8, regionChunks);
        this.regionChancePercent = regionChancePercent;
        this.houseChancePercent = houseChancePercent;
        this.scanTop = anchorTopY + 25;
    }

    @Override
    public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int rcx = Math.floorDiv(chunkX, regionChunks);
        int rcz = Math.floorDiv(chunkZ, regionChunks);

        Random rr = new Random(info.getSeed() ^ (rcx * 341873128712L + rcz * 132897987541L));
        if (rr.nextInt(100) >= regionChancePercent) return;

        int centerCX = rcx * regionChunks + 3 + rr.nextInt(regionChunks - 6);
        int centerCZ = rcz * regionChunks + 3 + rr.nextInt(regionChunks - 6);

        int dist = Math.max(Math.abs(chunkX - centerCX), Math.abs(chunkZ - centerCZ));
        if (dist > 2) return;

        if (dist == 0) {
            buildWell(region, random, chunkX * 16 + 8, chunkZ * 16 + 8);
            return;
        }

        if (random.nextInt(100) >= houseChancePercent) return;

        int bx = chunkX * 16 + 7 + random.nextInt(3);
        int bz = chunkZ * 16 + 7 + random.nextInt(3);
        int surfaceY = findSurface(region, bx, bz);
        if (surfaceY == Integer.MIN_VALUE) return;

        buildHouse(region, random, bx, surfaceY + 1, bz, random.nextInt(3));
    }

    private int findSurface(LimitedRegion region, int x, int z) {
        for (int y = scanTop; y >= scanTop - 70; y--) {
            if (!region.isInRegion(x, y, z)) continue;
            if (region.getType(x, y, z).isSolid()) return y;
        }
        return Integer.MIN_VALUE;
    }

    // ==================== ДОМ ====================

    private void buildHouse(LimitedRegion region, Random random, int cx, int floorY, int cz, int style) {
        int halfX = 3 + random.nextInt(2); // длина 7 или 9 (конёк вдоль X)
        int halfZ = 3;                     // ширина 7
        int wallH = 4;
        int roofBase = floorY + wallH - 1; // уровень верхней обвязки

        Material frame;
        Material wall;
        Material roofStairs;
        Material roofSolid;
        int decay;

        switch (style) {
            case 0 -> { frame = Material.DARK_OAK_LOG; wall = Material.CALCITE;
                        roofStairs = Material.DEEPSLATE_TILE_STAIRS; roofSolid = Material.DEEPSLATE_TILES; decay = 20; }
            case 1 -> { frame = Material.SPRUCE_LOG; wall = Material.SPRUCE_PLANKS;
                        roofStairs = Material.BLACKSTONE_STAIRS; roofSolid = Material.BLACKSTONE; decay = 35; }
            default -> { frame = Material.DARK_OAK_LOG; wall = null;
                         roofStairs = Material.DEEPSLATE_TILE_STAIRS; roofSolid = Material.DEEPSLATE_TILES; decay = 15; }
        }

        // Обрушенный угол — у каждого дома свой
        int ruinDX = random.nextBoolean() ? halfX : -halfX;
        int ruinDZ = random.nextBoolean() ? halfZ : -halfZ;

        // --- Пол + опоры до земли ---
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                if (style == 2 && random.nextInt(100) < 30) continue; // у скелета пол дырявый
                Material f = random.nextInt(4) == 0 ? Material.CRACKED_DEEPSLATE_BRICKS : Material.DEEPSLATE_BRICKS;
                if (style == 1 && random.nextInt(5) == 0) f = Material.COAL_BLOCK; // прогоревший пол
                setSafe(region, cx + dx, floorY - 1, cz + dz, f);
            }
        }
        for (int dx : new int[]{-halfX, halfX}) {
            for (int dz : new int[]{-halfZ, halfZ}) {
                supportPillar(region, cx + dx, floorY - 2, cz + dz);
            }
        }

        // --- Каркас: угловые + серединные столбы, верхняя обвязка ---
        for (int dx : new int[]{-halfX, 0, halfX}) {
            for (int dz : new int[]{-halfZ, halfZ}) {
                for (int h = 0; h < wallH; h++) {
                    if (ruined(dx, dz, h, ruinDX, ruinDZ, random)) continue;
                    setSafe(region, cx + dx, floorY + h, cz + dz, frame);
                }
            }
        }
        for (int dz : new int[]{-halfZ, 0, halfZ}) {
            for (int dx : new int[]{-halfX, halfX}) {
                for (int h = 0; h < wallH; h++) {
                    if (dz == 0 && h < wallH - 1) continue; // по центру торца только под обвязкой
                    if (ruined(dx, dz, h, ruinDX, ruinDZ, random)) continue;
                    setSafe(region, cx + dx, floorY + h, cz + dz, frame);
                }
            }
        }
        // обвязка по периметру (верх стен)
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                boolean edge = Math.abs(dx) == halfX || Math.abs(dz) == halfZ;
                if (!edge) continue;
                if (ruined(dx, dz, wallH - 1, ruinDX, ruinDZ, random)) continue;
                setSafe(region, cx + dx, floorY + wallH - 1, cz + dz, frame);
            }
        }

        // --- Стены с окнами и дверью ---
        if (wall != null) {
            for (int dx = -halfX + 1; dx <= halfX - 1; dx++) {
                for (int dz : new int[]{-halfZ, halfZ}) { // длинные стены
                    if (dx == 0) continue; // там столб
                    boolean door = dz == halfZ && dx == 1; // дверь на юге
                    boolean window = Math.abs(dx) == 2 && !door;
                    for (int h = 0; h < wallH - 1; h++) {
                        if (door && h < 2) continue;
                        if (window && h == 2) continue;
                        if (random.nextInt(100) < decay) continue;
                        if (ruined(dx, dz, h, ruinDX, ruinDZ, random)) continue;
                        Material m = wall;
                        if (style == 1 && random.nextInt(100) < 30) m = Material.COAL_BLOCK;
                        setSafe(region, cx + dx, floorY + h, cz + dz, m);
                    }
                }
            }
            for (int dz = -halfZ + 1; dz <= halfZ - 1; dz++) {
                for (int dx : new int[]{-halfX, halfX}) { // торцевые стены
                    boolean window = dz == 0;
                    for (int h = 0; h < wallH - 1; h++) {
                        if (window && h == 2) continue;
                        if (random.nextInt(100) < decay) continue;
                        if (ruined(dx, dz, h, ruinDX, ruinDZ, random)) continue;
                        Material m = wall;
                        if (style == 1 && random.nextInt(100) < 30) m = Material.COAL_BLOCK;
                        setSafe(region, cx + dx, floorY + h, cz + dz, m);
                    }
                }
            }
        }

        // --- Фронтоны на торцах ---
        Material gableMat = (wall != null) ? wall : frame;
        for (int s = 1; s <= 2; s++) {
            for (int dx : new int[]{-halfX, halfX}) {
                for (int dz = -(halfZ - s); dz <= halfZ - s; dz++) {
                    if (wall == null && Math.abs(dz) != halfZ - s && dz != 0) continue; // скелет: только контур
                    if (random.nextInt(100) < decay) continue;
                    if (ruined(dx, dz, wallH + s - 1, ruinDX, ruinDZ, random)) continue;
                    setSafe(region, cx + dx, roofBase + s, cz + dz, gableMat);
                }
            }
        }

        // --- Двускатная крыша из лестниц, со свесами и коньком ---
        int roofDecay = (style == 2) ? 70 : decay + 10;
        for (int s = 0; s <= 2; s++) {
            int dzOff = 4 - s; // 4 (свес), 3, 2
            int y = roofBase + s;
            for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
                if (random.nextInt(100) >= roofDecay) {
                    setStairs(region, cx + dx, y, cz + dzOff, roofStairs, BlockFace.NORTH, roofSolid);
                }
                if (random.nextInt(100) >= roofDecay) {
                    setStairs(region, cx + dx, y, cz - dzOff, roofStairs, BlockFace.SOUTH, roofSolid);
                }
            }
        }
        for (int dx = -halfX - 1; dx <= halfX + 1; dx++) { // предконёк + конёк
            if (random.nextInt(100) >= roofDecay) {
                setStairs(region, cx + dx, roofBase + 3, cz + 1, roofStairs, BlockFace.NORTH, roofSolid);
            }
            if (random.nextInt(100) >= roofDecay) {
                setStairs(region, cx + dx, roofBase + 3, cz - 1, roofStairs, BlockFace.SOUTH, roofSolid);
            }
            if (random.nextInt(100) >= roofDecay) {
                setSafe(region, cx + dx, roofBase + 4, cz, roofSolid);
            }
        }

        // --- Интерьер ---
        int webs = 1 + random.nextInt(3);
        for (int i = 0; i < webs; i++) {
            setSafe(region, cx - halfX + 2 + random.nextInt(halfX * 2 - 3), floorY + random.nextInt(2),
                    cz - halfZ + 2 + random.nextInt(halfZ * 2 - 3), Material.COBWEB);
        }
        if (random.nextInt(100) < 45) placeLootChest(region, random, cx - 1, floorY, cz - 1);
        if (random.nextInt(100) < 40) setSafe(region, cx + 2, floorY, cz + 1, Material.BARREL);
        if (random.nextInt(100) < 30) setSafe(region, cx - 2, floorY, cz + 1, Material.SOUL_LANTERN);
        if (random.nextInt(100) < 15) setSafe(region, cx + 1, floorY, cz - 2, Material.SKELETON_SKULL);
        if (style == 1 && random.nextInt(100) < 40) setSafe(region, cx, floorY, cz, Material.SOUL_CAMPFIRE);
    }

    /** Обрушенный угол: рядом с ним всё выше первого блока осыпалось. */
    private boolean ruined(int dx, int dz, int h, int ruinDX, int ruinDZ, Random random) {
        int d = Math.abs(dx - ruinDX) + Math.abs(dz - ruinDZ);
        return d <= 3 && h >= 1 && random.nextInt(100) < 70;
    }

    private void supportPillar(LimitedRegion region, int x, int startY, int z) {
        for (int y = startY; y > startY - 8; y--) {
            if (!region.isInRegion(x, y, z)) return;
            if (region.getType(x, y, z).isSolid()) return;
            region.setType(x, y, z, Material.DEEPSLATE_BRICKS);
        }
    }

    // ==================== КОЛОДЕЦ ====================

    private void buildWell(LimitedRegion region, Random random, int cx, int cz) {
        int surfaceY = findSurface(region, cx, cz);
        if (surfaceY == Integer.MIN_VALUE) return;
        int y = surfaceY + 1;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean ring = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (ring) {
                    setSafe(region, cx + dx, y, cz + dz, Material.DEEPSLATE_BRICKS);
                } else {
                    for (int d = 0; d < 4; d++) {
                        setSafe(region, cx, surfaceY - d, cz, Material.AIR);
                    }
                    if (random.nextBoolean()) {
                        setSafe(region, cx, surfaceY - 4, cz, Material.SKELETON_SKULL);
                    }
                }
            }
        }
        // навес над колодцем
        setSafe(region, cx - 1, y + 1, cz - 1, Material.DARK_OAK_FENCE);
        setSafe(region, cx + 1, y + 1, cz + 1, Material.DARK_OAK_FENCE);
        setSafe(region, cx - 1, y + 2, cz - 1, Material.DARK_OAK_FENCE);
        setSafe(region, cx + 1, y + 2, cz + 1, Material.DARK_OAK_FENCE);
        setSafe(region, cx, y + 3, cz, Material.SOUL_LANTERN);

        placeLootChest(region, random, cx + 2, y, cz + 2);
    }

    // ==================== УТИЛИТЫ ====================

    private void placeLootChest(LimitedRegion region, Random random, int x, int y, int z) {
        if (!region.isInRegion(x, y, z)) return;
        region.setType(x, y, z, Material.CHEST);
        try {
            BlockState st = region.getBlockState(x, y, z);
            if (st instanceof Chest chest) {
                LootChests.fill(chest.getSnapshotInventory(), random);
                st.update(true, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setStairs(LimitedRegion region, int x, int y, int z,
                           Material stairMat, BlockFace facing, Material fallback) {
        if (!region.isInRegion(x, y, z)) return;
        try {
            Stairs data = (Stairs) stairMat.createBlockData();
            data.setFacing(facing);
            region.setBlockData(x, y, z, data);
        } catch (Throwable t) {
            region.setType(x, y, z, fallback);
        }
    }

    private void setSafe(LimitedRegion region, int x, int y, int z, Material m) {
        if (region.isInRegion(x, y, z)) region.setType(x, y, z, m);
    }
}
