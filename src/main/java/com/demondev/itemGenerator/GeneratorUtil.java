package com.demondev.itemGenerator;

import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class GeneratorUtil {

    public static ItemStack createGeneratorItem(GeneratorType type) {
        ItemStack item = new ItemStack(type.blockMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + type.name + " Generator");
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemGenerator.GENERATOR_KEY, PersistentDataType.STRING, type.name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean hasRoomFor(ItemStack item, Inventory inv) {
        int remaining = item.getAmount();
        for (ItemStack inSlot : inv.getContents()) {
            if (inSlot == null) {
                remaining -= item.getMaxStackSize();
            } else if (inSlot.isSimilar(item)) {
                remaining -= (inSlot.getMaxStackSize() - inSlot.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }
}