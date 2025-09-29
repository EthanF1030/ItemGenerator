package com.demondev.itemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        PersistentDataContainer itemPdc = item.getItemMeta().getPersistentDataContainer();
        if (!itemPdc.has(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING)) return;

        String typeName = itemPdc.get(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING);
        GeneratorType type = manager.getGeneratorTypes().get(typeName);
        if (type == null) return;

        if (!event.getPlayer().hasPermission(type.permission)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to place this generator.");
            return;
        }

        Block block = event.getBlockPlaced();
        ActiveGenerator ag = new ActiveGenerator(block, type);
        ag.lastGenerate = System.currentTimeMillis() / 1000;
        manager.addActiveGenerator(ag);
        spawnHologram(ag);
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        ActiveGenerator gen = manager.getActiveGenerator(block.getLocation());
        if (gen == null) return;

        if (event.getPlayer().hasPermission("itemgen.admin")) {
            event.getPlayer().sendMessage(ChatColor.GREEN + "This is a " + gen.type.name + " generator.");
            // Could add more info or removal option
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

        boolean hasPerm = true;
        if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player) {
            Player player = (Player) event.getViewers().get(0);
            hasPerm = player.hasPermission(type.permission);
        }

        if (!hasPerm) {
            event.getInventory().setResult(null);
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
        String display = ChatColor.GOLD + ag.type.name + " Generator - " + Math.max(0, timeLeft) + "s";
        ag.hologram.setCustomName(display);
    }
}