package ru.mrmuter.underworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Random;

/**
 * Генератор "Мёртвой пустоши":
 * парящая каменная плита в пустоте, изрезанная сквозными каньонами-разломами.
 * Палитра — базальт, чернокамень, глубинный сланец, пятна скалка (sculk).
 * Биом везде DEEP_DARK — тьма, зловещий эмбиент, без мобов.
 */
public class VoidGenerator extends ChunkGenerator {

    private final int anchorTopY;   // высота плиты у спавна (чанк 0,0)
    private final int ruinChance;   // 1 из N чанков — руина
    private final int crystalChance;
    private final int villageRegion;
    private final int villageRegionPercent;
    private final int villageHousePercent;

    // Шумы инициализируем лениво (seed знаем только при генерации)
    private SimplexOctaveGenerator heightNoise;
    private SimplexOctaveGenerator canyonNoise;
    private SimplexOctaveGenerator sculkNoise;
    private SimplexOctaveGenerator ashNoise;

    public VoidGenerator(int anchorTopY, int ruinChance, int crystalChance,
                         int villageRegion, int villageRegionPercent, int villageHousePercent) {
        this.anchorTopY = anchorTopY;
        this.ruinChance = ruinChance;
        this.crystalChance = crystalChance;
        this.villageRegion = villageRegion;
        this.villageRegionPercent = villageRegionPercent;
        this.villageHousePercent = villageHousePercent;
    }

    private void initNoise(WorldInfo info) {
        if (heightNoise != null) return;
        long seed = info.getSeed();
        heightNoise = new SimplexOctaveGenerator(seed, 4);
        heightNoise.setScale(0.006D);
        canyonNoise = new SimplexOctaveGenerator(seed + 1337L, 2);
        canyonNoise.setScale(0.004D);
        sculkNoise = new SimplexOctaveGenerator(seed + 4242L, 2);
        sculkNoise.setScale(0.05D);
        ashNoise = new SimplexOctaveGenerator(seed + 777L, 2);
        ashNoise.setScale(0.008D);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        initNoise(info);

        final int base = anchorTopY;      // средняя высота поверхности плиты
        final int thickness = 28;         // толщина парящей плиты

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;

                // Якорь у спавна: плотный гарантированный грунт под точкой появления
                boolean anchor = (chunkX == 0 && chunkZ == 0 && x >= 5 && x <= 11 && z >= 5 && z <= 11);

                // Высота поверхности: пустошь — неровная, но не гористая
                double h = heightNoise.noise(worldX, worldZ, 0.5D, 0.5D, true); // [-1..1]
                int surfaceTop = base + (int) Math.round(h * 12);

                // Каньоны: узкие "жилы" шума прорезают плиту насквозь
                double c = Math.abs(canyonNoise.noise(worldX, worldZ, 0.5D, 0.5D, true));
                boolean canyon = c < 0.045D && !anchor;
                if (canyon) {
                    // чем ближе к центру жилы — тем глубже разлом (вплоть до сквозного)
                    double depthFactor = 1.0D - (c / 0.045D); // 0..1
                    int cut = (int) Math.round(depthFactor * (thickness + 6));
                    surfaceTop -= cut;
                }

                if (anchor) surfaceTop = base;

                int bottom = surfaceTop - thickness;

                for (int y = bottom; y <= surfaceTop; y++) {
                    Material mat = pickBlock(worldX, y, worldZ, surfaceTop, bottom, random);
                    data.setBlock(x, y, z, mat);
                }
            }
        }
    }

    private Material pickBlock(int wx, int y, int wz, int top, int bottom, Random random) {
        int depth = top - y; // 0 = верхний слой

        // Верхний слой — местами скалк-корка, местами пепельные зоны
        if (depth == 0) {
            double s = sculkNoise.noise(wx, wz, 0.5D, 0.5D, true);
            if (s > 0.55D) return Material.SCULK;

            double ash = ashNoise.noise(wx, wz, 0.5D, 0.5D, true);
            if (ash > 0.4D) {
                // пепельная зона: выгоревшая земля из кальцита и туфа
                return random.nextInt(3) == 0 ? Material.TUFF : Material.CALCITE;
            }

            if (s > 0.42D) return Material.BASALT;
            return random.nextInt(3) == 0 ? Material.BLACKSTONE : Material.SMOOTH_BASALT;
        }
        // Дно плиты — рваный край из базальта
        if (y <= bottom + 1) {
            return random.nextInt(2) == 0 ? Material.BASALT : Material.SMOOTH_BASALT;
        }
        // Внутренняя порода
        int r = random.nextInt(100);
        if (r < 6)  return Material.SCULK;
        if (r < 10) return Material.BLACKSTONE;
        if (r < 22) return Material.BASALT;
        if (r < 27) return Material.SMOOTH_BASALT;
        if (r < 36) return Material.TUFF;
        return Material.DEEPSLATE;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo i, int x, int y, int z) { return Biome.BASALT_DELTAS; }
            @Override public List<Biome> getBiomes(WorldInfo i) { return List.of(Biome.BASALT_DELTAS); }
        };
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(
                new RuinPopulator(ruinChance, anchorTopY),
                new VillagePopulator(villageRegion, villageRegionPercent, villageHousePercent, anchorTopY),
                new CrystalPopulator(crystalChance, anchorTopY));
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
