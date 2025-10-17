package com.demondev.itemGenerator;


import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GeneratorManager {

    private final ItemGenerator plugin;
    private final Map<String, GeneratorType> generatorTypes = new HashMap<>();
    private final Map<Location, ActiveGenerator> activeGenerators = new HashMap<>();

    public GeneratorManager(ItemGenerator plugin) {
        this.plugin = plugin;
    }

    public void loadGeneratorTypes() {
        ConfigurationSection generatorsSection = plugin.getConfig().getConfigurationSection("generators");
        if (generatorsSection == null) return;

        for (String key : generatorsSection.getKeys(false)) {
            ConfigurationSection section = generatorsSection.getConfigurationSection(key);

            org.bukkit.Material blockMaterial = org.bukkit.Material.getMaterial(section.getString("block", "STONE").toUpperCase());
            if (blockMaterial == null) {
                plugin.getLogger().warning("Invalid block material for generator: " + key);
                continue;
            }

            List<GeneratorItem> items = new ArrayList<>();
            List<Map<?, ?>> itemsList = section.getMapList("items");
            for (Map<?, ?> itemMap : itemsList) {
                String itemStr = (String) itemMap.get("item");
                org.bukkit.Material itemMaterial = org.bukkit.Material.getMaterial(itemStr.toUpperCase());
                if (itemMaterial == null) continue;

                Object amountObj = itemMap.get("amount");
                int amount = (amountObj instanceof Number) ? ((Number) amountObj).intValue() : 1;

                Object chanceObj = itemMap.get("chance");
                double chance = (chanceObj instanceof Number) ? ((Number) chanceObj).doubleValue() : 100.0;

                items.add(new GeneratorItem(new org.bukkit.inventory.ItemStack(itemMaterial, amount), chance));
            }

            long interval = section.getLong("interval", 60); // in seconds
            String permission = section.getString("permission", "itemgen.use." + key);

            // Recipe
            ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
            List<String> shape = recipeSection.getStringList("shape");
            Map<Character, org.bukkit.Material> ingredients = new HashMap<>();
            ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
            for (String ingKey : ingredientsSection.getKeys(false)) {
                char c = ingKey.charAt(0);
                org.bukkit.Material mat = org.bukkit.Material.getMaterial(ingredientsSection.getString(ingKey).toUpperCase());
                if (mat != null) {
                    ingredients.put(c, mat);
                }
            }

            int maxCrafts = section.getInt("max_crafts", -1);

            generatorTypes.put(key, new GeneratorType(key, blockMaterial, items, interval, permission, shape, ingredients, maxCrafts));
        }
    }

    public void loadActiveGenerators(File dataFile) {
        if (!dataFile.exists()) return;

        FileConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<?, ?>> generatorsList = dataConfig.getMapList("generators");
        for (Map<?, ?> genMap : generatorsList) {
            String worldUuid = (String) genMap.get("world");

            Object xObj = genMap.get("x");
            int x = (xObj instanceof Number) ? ((Number) xObj).intValue() : 0;

            Object yObj = genMap.get("y");
            int y = (yObj instanceof Number) ? ((Number) yObj).intValue() : 0;

            Object zObj = genMap.get("z");
            int z = (zObj instanceof Number) ? ((Number) zObj).intValue() : 0;

            String typeName = (String) genMap.get("type");

            Object lastGenerateObj = genMap.get("last_generate");
            long lastGenerate = (lastGenerateObj instanceof Number) ? ((Number) lastGenerateObj).longValue() : System.currentTimeMillis() / 1000;

            GeneratorType type = generatorTypes.get(typeName);
            if (type == null) continue;

            UUID worldId = UUID.fromString(worldUuid);
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldId);
            if (world == null) continue;

            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if (block.getType() != type.blockMaterial) continue; // Skip if block no longer matches

            ActiveGenerator ag = new ActiveGenerator(block, type);
            ag.lastGenerate = lastGenerate;
            activeGenerators.put(block.getLocation(), ag);
            plugin.getServer().getScheduler().runTask(plugin, () -> spawnHologram(ag)); // Schedule sync spawn
        }
    }

    public void saveActiveGenerators(File dataFile) {
        FileConfiguration dataConfig = new YamlConfiguration();

        List<Map<String, Object>> generatorsList = new ArrayList<>();
        for (ActiveGenerator gen : activeGenerators.values()) {
            Map<String, Object> genMap = new HashMap<>();
            genMap.put("world", gen.block.getWorld().getUID().toString());
            genMap.put("x", gen.block.getX());
            genMap.put("y", gen.block.getY());
            genMap.put("z", gen.block.getZ());
            genMap.put("type", gen.type.name);
            genMap.put("last_generate", gen.lastGenerate);
            generatorsList.add(genMap);
        }

        dataConfig.set("generators", generatorsList);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public Map<String, GeneratorType> getGeneratorTypes() {
        return generatorTypes;
    }

    public Collection<ActiveGenerator> getActiveGenerators() {
        return activeGenerators.values();
    }

    public ActiveGenerator getActiveGenerator(Location location) {
        return activeGenerators.get(location);
    }

    public void addActiveGenerator(ActiveGenerator generator) {
        activeGenerators.put(generator.block.getLocation(), generator);
    }

    public void removeActiveGenerator(ActiveGenerator generator) {
        activeGenerators.remove(generator.block.getLocation());
    }

    public void clearActiveGenerators() {
        for (ActiveGenerator ag : activeGenerators.values()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> removeHologram(ag));
        }
        activeGenerators.clear();
    }

    private void spawnHologram(ActiveGenerator ag) {
        Location loc = ag.block.getLocation().add(0.5, 1.5, 0.5);
        ArmorStand as = (ArmorStand) ag.block.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setGravity(false);
        as.setCanPickupItems(false);
        as.setCustomNameVisible(true);
        as.setVisible(false);
        as.setInvulnerable(true);
        as.setMarker(true);
        ag.hologram = as;
        updateHologram(ag); // Initial update
    }

    private void removeHologram(ActiveGenerator ag) {
        if (ag.hologram != null) {
            ag.hologram.remove();
            ag.hologram = null;
        }
    }

    private void updateHologram(ActiveGenerator ag) {
        if (ag.hologram == null) return;
        long currentTime = System.currentTimeMillis() / 1000;
        long timeLeft = ag.type.interval - (currentTime - ag.lastGenerate);
        String display;
        if (timeLeft > 0) {
            display = ChatColor.GOLD + ag.type.name + " Generator - " + timeLeft + "s";
        } else if (ag.pausedReason != null) {
            display = ChatColor.RED + ag.type.name + " Generator - Paused: " + ag.pausedReason;
        } else {
            display = ChatColor.GREEN + ag.type.name + " Generator - Ready";
        }
        ag.hologram.setCustomName(display);
    }
}