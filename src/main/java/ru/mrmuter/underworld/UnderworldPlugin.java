package ru.mrmuter.underworld;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnderworldPlugin extends JavaPlugin {

    private World underworld;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String worldName = getConfig().getString("world-name", "underworld");
        int anchorTop = getConfig().getInt("anchor-top-y", 70);
        int ruinChance = getConfig().getInt("ruins.chance", 40);
        int crystalChance = getConfig().getInt("crystal.chance", 130);
        int vRegion = getConfig().getInt("village.region-chunks", 24);
        int vRegionPct = getConfig().getInt("village.region-chance-percent", 55);
        int vHousePct = getConfig().getInt("village.house-chance-percent", 45);

        WorldCreator creator = new WorldCreator(worldName)
                .generator(new VoidGenerator(anchorTop, ruinChance, crystalChance, vRegion, vRegionPct, vHousePct))
                .environment(World.Environment.NORMAL);
        underworld = creator.createWorld();

        if (underworld != null) {
            underworld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            underworld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            underworld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            underworld.setGameRule(GameRule.DO_FIRE_TICK, false);
            underworld.setTime(18000L); // вечная полночь
            underworld.setSpawnLocation(0, anchorTop + 1, 0);
        } else {
            getLogger().severe("Не удалось создать мир подмира! Плагин работает вхолостую.");
        }

        getServer().getPluginManager().registerEvents(new VoidFallListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new AdventureModeListener(this), this);

        int mobInterval = Math.max(3, getConfig().getInt("mobs.interval-seconds", 12));
        new UnderworldMobs(this).runTaskTimer(this, 100L, 20L * mobInterval);

        // Разломы реальности (фиолетовый/белый/серый)
        new RiftManager(this).runTaskTimer(this, 120L, 5L);

        // Жёсткая вечная ночь: гейм-рулы в новых версиях Paper могут игнорироваться,
        // поэтому каждые 5 секунд силой держим полночь и разгоняем погоду
        final String uwName = worldName;
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (underworld == null) return;
            // Бьём СТРОГО по подмиру, сверяя имя — чтобы не задеть обычный мир
            if (!underworld.getName().equals(uwName)) return;
            if (underworld.getTime() != 18000L) underworld.setTime(18000L);
            if (underworld.hasStorm()) underworld.setStorm(false);
        }, 100L, 100L);

        // Диагностика: подмир не должен быть главным миром сервера!
        if (getServer().getWorlds().get(0).equals(underworld)) {
            getLogger().severe("=================================================");
            getLogger().severe("ВНИМАНИЕ: мир '" + underworld.getName() + "' установлен ГЛАВНЫМ миром сервера!");
            getLogger().severe("Игроки будут спавниться в измерении. Исправь в server.properties:");
            getLogger().severe("level-name=world  (и перезапусти сервер)");
            getLogger().severe("=================================================");
        }

        getLogger().info("Underworld включён. Мёртвая пустошь ждёт.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Команда только для игроков.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(mm.deserialize("<gray>/underworld <white>tp <gray>| <white>leave <gray>| <white>info"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tp" -> {
                if (!player.hasPermission("underworld.tp")) {
                    player.sendMessage(mm.deserialize("<red>Недостаточно прав."));
                    return true;
                }
                if (underworld == null) return true;
                player.teleportAsync(underworld.getSpawnLocation().clone().add(0.5, 0, 0.5));
                player.sendMessage(mm.deserialize("<dark_gray>Ты шагнул в Мёртвую пустошь."));
            }
            case "leave" -> {
                World overworld = getMainWorld();
                player.teleportAsync(overworld.getSpawnLocation().clone().add(0.5, 0, 0.5));
                player.sendMessage(mm.deserialize("<gray>Ты вернулся в обычный мир."));
            }
            case "info" -> {
                Location loc = player.getLocation();
                player.sendMessage(mm.deserialize("<gray>Мир: <white>" + loc.getWorld().getName()
                        + " <gray>Y: <white>" + loc.getBlockY()));
            }
            default -> player.sendMessage(mm.deserialize("<red>Неизвестная подкоманда."));
        }
        return true;
    }

    public World getUnderworld() {
        return underworld;
    }

    /** Обычный мир сервера. Приоритет: мир с именем "world", затем любой NORMAL не-подмир. */
    public World getMainWorld() {
        World byName = getServer().getWorld("world");
        if (byName != null && !byName.equals(underworld)) return byName;
        for (World w : getServer().getWorlds()) {
            if (!w.equals(underworld) && w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return getServer().getWorlds().get(0);
    }
}
