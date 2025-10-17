package com.demondev.itemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class GeneratorListener implements Listener {

    private final ItemGenerator plugin;
    private final GeneratorManager manager;

    public GeneratorListener(ItemGenerator plugin, GeneratorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        PersistentDataContainer itemPdc = item.getItemMeta().getPersistentDataContainer();
        if (!itemPdc.has(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING)) {
            // Existing interact logic for clicking on generator
            Block block = event.getClickedBlock();
            ActiveGenerator gen = manager.getActiveGenerator(block.getLocation());
            if (gen == null) return;

            if (event.getPlayer().hasPermission("itemgen.admin")) {
                event.getPlayer().sendMessage(ChatColor.GREEN + "This is a " + gen.type.name + " generator.");
            }
            return;
        }

        // Placement logic for generator item
        String typeName = itemPdc.get(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING);
        GeneratorType type = manager.getGeneratorTypes().get(typeName);
        if (type == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(type.permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to place this generator.");
            return;
        }

        Block clicked = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        Block placeBlock = clicked.getRelative(face);

        if (!placeBlock.isEmpty()) return; // Can't place if not empty

        // Force place the block
        placeBlock.setType(type.blockMaterial);

        ActiveGenerator ag = new ActiveGenerator(placeBlock, type);
        ag.lastGenerate = System.currentTimeMillis() / 1000;
        manager.addActiveGenerator(ag);
        spawnHologram(ag);

        // Consume item
        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
            item.setAmount(item.getAmount() - 1);
        }

        event.setCancelled(true); // Prevent vanilla placement
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        ActiveGenerator gen = manager.getActiveGenerator(event.getBlock().getLocation());
        if (gen != null) {
            removeHologram(gen);
            manager.removeActiveGenerator(gen);
            // Optional: event.getPlayer().sendMessage(ChatColor.RED + "Generator removed.");
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        PersistentDataContainer pdc = result.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING)) return;

        String typeName = pdc.get(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING);
        GeneratorType type = manager.getGeneratorTypes().get(typeName);
        if (type == null) return;

        if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player) {
            Player player = (Player) event.getViewers().get(0);
            if (!player.hasPermission(type.permission)) {
                event.getInventory().setResult(null);
                return;
            }
            if (type.maxCrafts >= 0) {
                PersistentDataContainer playerPdc = player.getPersistentDataContainer();
                NamespacedKey countKey = new NamespacedKey(plugin, "crafts_" + type.name);
                int current = playerPdc.getOrDefault(countKey, PersistentDataType.INTEGER, 0);
                if (current >= type.maxCrafts) {
                    event.getInventory().setResult(null);
                    // Optional: player.sendMessage(ChatColor.RED + "You have reached the craft limit for " + type.name + " generators.");
                }
            }
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        PersistentDataContainer pdc = result.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING)) return;

        String typeName = pdc.get(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING);
        GeneratorType type = manager.getGeneratorTypes().get(typeName);
        if (type == null) return;

        // Check limit again to be safe
        if (type.maxCrafts >= 0) {
            PersistentDataContainer playerPdc = player.getPersistentDataContainer();
            NamespacedKey countKey = new NamespacedKey(plugin, "crafts_" + type.name);
            int current = playerPdc.getOrDefault(countKey, PersistentDataType.INTEGER, 0);
            if (current >= type.maxCrafts) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You have reached the craft limit for " + type.name + " generators.");
                return;
            }
            // Increment
            playerPdc.set(countKey, PersistentDataType.INTEGER, current + 1);
        }
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