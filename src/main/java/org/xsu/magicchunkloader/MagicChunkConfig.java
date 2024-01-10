package org.xsu.magicchunkloader;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MagicChunkConfig {

    private final Logger logger;
    private final String version;
    private ItemStack droppedItem;
    private double spawnChance;
    private long despawnTime;
    private boolean allowFluidSpawns;
    private boolean allowNetherSpawns;
    private boolean allowEndSpawns;
    private boolean includesLogs;

    public MagicChunkConfig(Logger logger, String version) {
        this.logger = logger;
        this.version = version;
    }

    public boolean loadConfig(FileConfiguration config) {
        if (!version.equals(config.getString("version"))) {
            logger.severe(String.format(
                    "Improperly configured config.yml for v%s: Reported config version mismatches to %s!",
                    version,
                    config.getString("version")
            ));
            return false;
        }

        // Load and validate dropped item
        droppedItem = config.getItemStack("dropped-item");
        if (droppedItem == null) {
            logger.severe(String.format(
                    "Improperly configured config.yml for v%s: invalid droppedItem!",
                    version
            ));
            return false;
        }

        // Add a hidden enchantment to the dropped item
        ItemMeta meta = droppedItem.getItemMeta();
        if (meta != null) {
            // Translate color codes in display name
            if (meta.hasDisplayName()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', meta.getDisplayName()));
            }

            // Translate color codes in lore
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                lore = lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
                meta.setLore(lore);
            }

            // Check for glow effect
            boolean glows = config.getBoolean("item-glows");
            if (glows) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            droppedItem.setItemMeta(meta);
        }

        // Load and validate other properties
        spawnChance = config.getDouble("spawn-chance");
        if (spawnChance < 0) {
            logger.severe(String.format(
                    "Improperly configured config.yml for v%s: spawn-chance must be >=0! It is currently %s!",
                    version,
                    spawnChance
            ));
            return false;
        }

        despawnTime = config.getLong("despawn-time");
        if (despawnTime <= 0) {
            logger.severe(String.format(
                    "Improperly configured config.yml for v%s: despawn-time must be >0! It is currently %s!",
                    version,
                    despawnTime
            ));
            return false;
        }

        allowFluidSpawns = config.getBoolean("allow-fluid-spawns");
        allowNetherSpawns = config.getBoolean("allow-nether-spawns");
        allowEndSpawns = config.getBoolean("allow-end-spawns");
        includesLogs = config.getBoolean("logs-enabled");

        return true;
    }

    // Getters
    public Logger getLogger() {
        return logger;
    }
    public ItemStack getDroppedItem() {
        return droppedItem;
    }

    public double getSpawnChance() {
        return spawnChance;
    }

    public long getDespawnTime() {
        return despawnTime;
    }

    public boolean isAllowFluidSpawns() {
        return allowFluidSpawns;
    }

    public boolean isAllowNetherSpawns() {
        return allowNetherSpawns;
    }

    public boolean isAllowEndSpawns() {
        return allowEndSpawns;
    }

    public boolean isIncludesLogs() {
        return includesLogs;
    }

}