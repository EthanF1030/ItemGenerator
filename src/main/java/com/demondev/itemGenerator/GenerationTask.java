package com.demondev.itemGenerator;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GenerationTask extends BukkitRunnable {

    private final ItemGenerator plugin;
    private final GeneratorManager manager;

    public GenerationTask(ItemGenerator plugin, GeneratorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis() / 1000;
        List<ActiveGenerator> generatorsCopy = new ArrayList<>(manager.getActiveGenerators()); // Avoid concurrent mod
        for (ActiveGenerator gen : generatorsCopy) {
            if (currentTime - gen.lastGenerate < gen.type.interval) continue;

            // Select item based on chance
            GeneratorItem selected = null;
            double totalChance = 0;
            for (GeneratorItem item : gen.type.items) {
                totalChance += item.chance;
            }
            double rand = ThreadLocalRandom.current().nextDouble() * totalChance;
            double cumulative = 0;
            for (GeneratorItem item : gen.type.items) {
                cumulative += item.chance;
                if (rand <= cumulative) {
                    selected = item;
                    break;
                }
            }

            if (selected != null) {
                org.bukkit.inventory.ItemStack toSpawn = selected.item.clone();
                // Schedule sync spawn
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (gen.block.getType() != gen.type.blockMaterial) {
                            // Block destroyed, remove
                            manager.removeActiveGenerator(gen);
                            return;
                        }
                        gen.block.getWorld().dropItemNaturally(gen.block.getLocation().add(0.5, 1, 0.5), toSpawn);
                        gen.lastGenerate = currentTime;
                    }
                }.runTask(plugin);
            }
        }
    }
}