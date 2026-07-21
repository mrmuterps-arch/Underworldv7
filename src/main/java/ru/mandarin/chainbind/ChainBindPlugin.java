package ru.mandarin.chainbind;

import org.bukkit.plugin.java.JavaPlugin;
import ru.mandarin.chainbind.commands.ChainBindCommand;
import ru.mandarin.chainbind.listeners.ChainInteractListener;
import ru.mandarin.chainbind.listeners.MovementListener;
import ru.mandarin.chainbind.listeners.QuitListener;
import ru.mandarin.chainbind.listeners.SneakListener;

public final class ChainBindPlugin extends JavaPlugin {

    private BindManager bindManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.bindManager = new BindManager(this);

        // Регистрируем все обработчики событий
        getServer().getPluginManager().registerEvents(new ChainInteractListener(this, bindManager), this);
        getServer().getPluginManager().registerEvents(new MovementListener(bindManager), this);
        getServer().getPluginManager().registerEvents(new SneakListener(this, bindManager), this);
        getServer().getPluginManager().registerEvents(new QuitListener(this, bindManager), this);

        // Команда администрирования
        ChainBindCommand command = new ChainBindCommand(this, bindManager);
        getCommand("chainbind").setExecutor(command);
        getCommand("chainbind").setTabCompleter(command);

        getLogger().info("ChainBind включён. Связано игроков на старте: " + bindManager.getBoundCount());
    }

    @Override
    public void onDisable() {
        if (bindManager != null) {
            // На выключение сервера/плагина отпускаем всех, чтобы никто не остался
            // "залипшим" в путах при следующем запуске (состояние не персистентно).
            bindManager.releaseAll();
        }
    }

    public BindManager getBindManager() {
        return bindManager;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (bindManager != null) {
            bindManager.reloadSettings();
        }
    }
}
