package ru.mandarin.bamboosmoke;

import org.bukkit.plugin.java.JavaPlugin;

public class BambooSmokePlugin extends JavaPlugin {

    private SmokeManager smokeManager;

    @Override
    public void onEnable() {
        this.smokeManager = new SmokeManager(this);
        getServer().getPluginManager().registerEvents(new SmokeListener(this, smokeManager), this);

        // Тикающая задача: затухание шкалы, шатание, обновление bossbar/мыслей.
        getServer().getScheduler().runTaskTimer(this, smokeManager::tick, 20L, 20L);

        getLogger().info("BambooSmoke включён. Курите ответственно (в игре).");
    }

    @Override
    public void onDisable() {
        if (smokeManager != null) {
            smokeManager.clearAll();
        }
        getLogger().info("BambooSmoke выключен.");
    }

    public SmokeManager getSmokeManager() {
        return smokeManager;
    }
}
