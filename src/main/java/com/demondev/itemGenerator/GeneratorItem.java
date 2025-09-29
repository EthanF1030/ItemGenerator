package com.demondev.itemGenerator;

import org.bukkit.inventory.ItemStack;

public class GeneratorItem {
    ItemStack item;
    double chance;

    GeneratorItem(ItemStack item, double chance) {
        this.item = item;
        this.chance = chance;
    }
}