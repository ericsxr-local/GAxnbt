package com.eric.gaxnbt;

import com.eric.gaxnbt.command.XNBTCommand;
import com.eric.gaxnbt.manager.HistoryManager;
import com.eric.gaxnbt.manager.LocalizationManager;
import com.eric.gaxnbt.manager.NbtEditorManager;
import com.eric.gaxnbt.manager.SpecialItemManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GAxnbtPlugin extends JavaPlugin {
    private LocalizationManager localization;
    private HistoryManager history;
    private NbtEditorManager editor;
    private SpecialItemManager specialItems;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        localization = new LocalizationManager(this);
        history = new HistoryManager(this);
        editor = new NbtEditorManager(this, localization, history);
        specialItems = new SpecialItemManager(this, localization);

        XNBTCommand command = new XNBTCommand(this, localization, editor, history, specialItems);
        getCommand("xnbt").setExecutor(command);
        getCommand("xnbt").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(specialItems, this);

        getLogger().info("GAxnbt 已启用。作者：Eric_SXR");
    }

    @Override
    public void onDisable() {
        if (history != null) history.clearAll();
    }

    public void reloadXNBT() {
        reloadConfig();
        localization.reload();
    }
}
