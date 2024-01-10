package org.xsu.magicchunkloader;

import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MagicChunkLoader extends JavaPlugin {

    final Logger logger = this.getLogger();
    final String version = "1.0.0";

    @Override
    public void onEnable() {
        // Generic config boilerplate
        this.saveDefaultConfig();
        FileConfiguration config = this.getConfig();

        // Create and validate updated config schema
        MagicChunkConfig validatedConfig = new MagicChunkConfig(logger, version);
        if (!validatedConfig.loadConfig(config)) {
            onDisable();
            return;
        }

        // Enable listener and confirm plugin is enabled
        getServer().getPluginManager().registerEvents(new MagicChunkListener(this, validatedConfig), this);
        logger.info(String.format("Successfully enabled MagicChunkLoader v%s", version));
    }

    @Override
    public void onDisable() {
        logger.severe(String.format("Failed to enabled MagicChunkLoader v%s!", version));
    }
}
