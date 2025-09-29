package com.demondev.itemGenerator;

import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;

public class ActiveGenerator {
    Block block;
    GeneratorType type;
    long lastGenerate;
    ArmorStand hologram;

    ActiveGenerator(Block block, GeneratorType type) {
        this.block = block;
        this.type = type;
    }
}