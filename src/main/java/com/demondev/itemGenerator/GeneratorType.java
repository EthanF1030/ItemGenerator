package com.demondev.itemGenerator;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public class GeneratorType {
    String name;
    Material blockMaterial;
    List<GeneratorItem> items;
    long interval;
    String permission;
    List<String> shape;
    Map<Character, Material> ingredients;
    int maxCrafts;

    GeneratorType(String name, Material blockMaterial, List<GeneratorItem> items, long interval, String permission,
                  List<String> shape, Map<Character, Material> ingredients, int maxCrafts) {
        this.name = name;
        this.blockMaterial = blockMaterial;
        this.items = items;
        this.interval = interval;
        this.permission = permission;
        this.shape = shape;
        this.ingredients = ingredients;
        this.maxCrafts = maxCrafts;
    }
}