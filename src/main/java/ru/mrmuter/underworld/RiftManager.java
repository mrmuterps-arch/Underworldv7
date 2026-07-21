package ru.mrmuter.underworld;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Разломы реальности — вертикальные завесы из частиц с зоной-триггером.
 *
 *  ФИОЛЕТОВЫЙ (постоянный, у земли) — при входе телепорт в случайную точку пустоты.
 *  БЕЛЫЙ       (постоянный, у земли) — при входе телепорт на спавн подмира.
 *  СЕРЫЙ       (временный, спавнится рядом с игроком на 20 с) —
 *              выпускает 5 зомби и 2 скелета в невыпадающей броне, затем исчезает.
 *
 * Постоянные разломы «дрейфуют»: раз в N минут пересоздаются в новом месте
 * рядом с кем-то из игроков, чтобы их можно было находить.
 */
public class RiftManager extends BukkitRunnable {

    public enum Type { PURPLE, WHITE, GREY }

    public static final class Rift {
        final Type type;
        final Location center;
        long expireTick; // -1 = постоянный
        Rift(Type type, Location center, long expireTick) {
            this.type = type; this.center = center; this.expireTick = expireTick;
        }
    }

    private final UnderworldPlugin plugin;
    private final Random random = new Random();
    private final List<Rift> rifts = new ArrayList<>();
    private long tick = 0L;
    private long nextPermRefresh = 0L;

    public RiftManager(UnderworldPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        World w = plugin.getUnderworld();
        if (w == null) return;
        tick += 5L; // задача крутится раз в 5 тиков

        // Раз в ~4 минуты пересоздаём постоянные разломы рядом с игроками
        if (tick >= nextPermRefresh && !w.getPlayers().isEmpty()) {
            spawnPermanentRifts(w);
            nextPermRefresh = tick + 20L * 60L * 4L;
        }

        // Серый разлом: редкий шанс появиться рядом с игроком
        for (Player p : w.getPlayers()) {
            if (random.nextInt(1000) < 3) { // ~шанс на тик-цикл
                spawnGreyRift(p);
            }
        }

        // Отрисовка частиц + проверка входа игроков + истечение
        Iterator<Rift> it = rifts.iterator();
        while (it.hasNext()) {
            Rift r = it.next();
            if (r.expireTick > 0 && tick >= r.expireTick) {
                r.center.getWorld().playSound(r.center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
                it.remove();
                continue;
            }
            drawRift(r);
            if (r.type != Type.GREY && tick % 10 == 0) shakeNearby(r);
            checkEntry(r);
        }
    }

    // ---------- создание ----------

    private void spawnPermanentRifts(World w) {
        rifts.removeIf(r -> r.expireTick < 0); // убираем старые постоянные
        Player anchor = w.getPlayers().get(random.nextInt(w.getPlayers().size()));
        Location base = anchor.getLocation();

        rifts.add(new Rift(Type.PURPLE, groundNear(w, base, 25 + random.nextInt(25)), -1));
        rifts.add(new Rift(Type.WHITE, groundNear(w, base, 25 + random.nextInt(25)), -1));
    }

    private void spawnGreyRift(Player p) {
        Location loc = groundNear(p.getWorld(), p.getLocation(), 6 + random.nextInt(4));
        Rift grey = new Rift(Type.GREY, loc, tick + 20L * 20L); // 20 секунд
        rifts.add(grey);
        p.playSound(p, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1f, 0.4f);
        p.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.5f);

        // выпускаем волну через 2 секунды после появления
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> releaseWave(loc), 40L);
    }

    private void releaseWave(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        for (int i = 0; i < 5; i++) {
            Zombie z = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
            armor(z.getEquipment(), false);
        }
        for (int i = 0; i < 2; i++) {
            Skeleton s = (Skeleton) w.spawnEntity(loc, EntityType.SKELETON);
            armor(s.getEquipment(), true);
        }
    }

    private void armor(EntityEquipment eq, boolean bow) {
        if (eq == null) return;
        eq.setHelmet(new ItemStack(org.bukkit.Material.IRON_HELMET));
        eq.setChestplate(new ItemStack(org.bukkit.Material.IRON_CHESTPLATE));
        eq.setLeggings(new ItemStack(org.bukkit.Material.IRON_LEGGINGS));
        eq.setBoots(new ItemStack(org.bukkit.Material.IRON_BOOTS));
        if (bow) eq.setItemInMainHand(new ItemStack(org.bukkit.Material.BOW));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    // ---------- отрисовка ----------

    private void drawRift(Rift r) {
        World w = r.center.getWorld();
        if (w == null) return;

        if (r.type == Type.GREY) {
            // Серый разлом — клуб дыма от костра, зависший в воздухе
            for (int i = 0; i < 6; i++) {
                double ox = (random.nextDouble() - 0.5) * 1.6;
                double oy = 0.5 + random.nextDouble() * 2.2;
                double oz = (random.nextDouble() - 0.5) * 1.6;
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, r.center.clone().add(ox, oy, oz),
                        0, 0, 0.02, 0, 0.0);
            }
            w.spawnParticle(Particle.SMOKE, r.center.clone().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0.01);
            return;
        }

        // Трещина реальности: рваная вертикаль с боковыми ветвлениями (не ровная "дверь")
        Color color = (r.type == Type.PURPLE)
                ? Color.fromRGB(150, 40, 200)
                : Color.fromRGB(235, 235, 245);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);

        double sway = Math.sin(tick * 0.15) * 0.15; // лёгкое дрожание трещины
        for (double dy = 0.1; dy <= 3.2; dy += 0.25) {
            // зигзаг ствола трещины
            double jag = Math.sin(dy * 3.1 + r.center.getBlockX()) * 0.35 + sway;
            Location pt = r.center.clone().add(jag, dy, 0);
            w.spawnParticle(Particle.DUST, pt, 1, dust);

            // случайные короткие ветви-осколки
            if (random.nextInt(3) == 0) {
                double bx = jag + (random.nextBoolean() ? 0.4 : -0.4);
                w.spawnParticle(Particle.DUST, r.center.clone().add(bx, dy + 0.1, 0), 1, dust);
            }
        }
        // искры по центру
        if (random.nextInt(4) == 0) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, r.center.clone().add(0, 1.6, 0), 2, 0.2, 0.5, 0.2, 0.0);
        }
    }

    /** Тряска экрана у игроков рядом с трещиной. */
    private void shakeNearby(Rift r) {
        World w = r.center.getWorld();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            double d2 = p.getLocation().distanceSquared(r.center);
            if (d2 > 8 * 8) continue;
            // Микро-толчок камеры через быстрый разворот туда-обратно
            Location l = p.getLocation();
            float amp = (float) (1.5 * (1.0 - Math.sqrt(d2) / 8.0));
            l.setYaw(l.getYaw() + (random.nextBoolean() ? amp : -amp));
            l.setPitch(l.getPitch() + (random.nextBoolean() ? amp : -amp) * 0.5f);
            p.teleport(l);
        }
    }

    // ---------- вход игрока ----------

    private void checkEntry(Rift r) {
        World w = r.center.getWorld();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(r.center) > 2.2 * 2.2) continue;
            switch (r.type) {
                case PURPLE -> {
                    Location dest = randomVoidPoint(w, p);
                    p.teleportAsync(dest);
                    p.playSound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
                }
                case WHITE -> {
                    p.teleportAsync(w.getSpawnLocation().clone().add(0.5, 0, 0.5));
                    p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
                }
                case GREY -> { /* серый не телепортирует, только мобы */ }
            }
        }
    }

    private Location randomVoidPoint(World w, Player p) {
        int x = p.getLocation().getBlockX() + (random.nextInt(1000) - 500);
        int z = p.getLocation().getBlockZ() + (random.nextInt(1000) - 500);
        for (int y = 100; y >= 20; y--) {
            if (w.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(w, x + 0.5, y + 1.5, z + 0.5);
            }
        }
        // не нашли землю — высоко над точкой, упадёт (в подмире это ок)
        return new Location(w, x + 0.5, 90, z + 0.5);
    }

    // ---------- утилиты ----------

    private Location groundNear(World w, Location base, int radius) {
        double ang = random.nextDouble() * Math.PI * 2;
        int x = base.getBlockX() + (int) (Math.cos(ang) * radius);
        int z = base.getBlockZ() + (int) (Math.sin(ang) * radius);
        for (int y = 100; y >= 20; y--) {
            if (w.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return new Location(w, x + 0.5, base.getY(), z + 0.5);
    }
}
