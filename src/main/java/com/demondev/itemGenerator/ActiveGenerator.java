package com.demondev.itemGenerator;

import org.bukkit.block.Block;

public class ActiveGenerator {
    Block block;
    GeneratorType type;
    long lastGenerate;

    ActiveGenerator(Block block, GeneratorType type) {
        this.block = block;
        this.type = type;
    }
}