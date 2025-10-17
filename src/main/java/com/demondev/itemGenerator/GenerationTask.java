package com.demondev.itemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
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
            // Schedule hologram update sync
            plugin.getServer().getScheduler().runTask(plugin, () -> updateHologram(gen));

            if (currentTime - gen.lastGenerate < gen.type.interval) continue;

            // Schedule sync generation attempt
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (gen.block.getType() != gen.type.blockMaterial) {
                        // Block destroyed, remove
                        manager.removeActiveGenerator(gen);
                        return;
                    }

                    Block below = gen.block.getRelative(BlockFace.DOWN);
                    if (below.getType() != Material.HOPPER) {
                        gen.pausedReason = "No Hopper";
                        updateHologram(gen);
                        return;
                    }

                    Hopper hopper = (Hopper) below.getState();

                    // Select item
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

                    if (selected == null) return;

                    ItemStack toSpawn = selected.item.clone();

                    if (!GeneratorUtil.hasRoomFor(toSpawn, hopper.getInventory())) {
                        gen.pausedReason = "Hopper Full";
                        updateHologram(gen);
                        return;
                    }

                    // Add to hopper
                    HashMap<Integer, ItemStack> leftovers = hopper.getInventory().addItem(toSpawn);
                    if (!leftovers.isEmpty()) {
                        // Shouldn't happen, but drop leftovers
                        gen.block.getWorld().dropItemNaturally(gen.block.getLocation().add(0.5, 1, 0.5), leftovers.get(0));
                    }

                    gen.lastGenerate = currentTime;
                    gen.pausedReason = null;
                    updateHologram(gen);
                }
            }.runTask(plugin);
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