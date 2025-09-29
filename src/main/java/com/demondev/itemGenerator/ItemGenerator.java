package com.demondev.itemGenerator;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ShapedRecipe;

import java.io.File;

public class ItemGenerator extends JavaPlugin {

    public static final NamespacedKey GENERATOR_KEY = new NamespacedKey("itemgen", "generator_type");

    private GeneratorManager generatorManager;
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generatorManager = new GeneratorManager(this);
        generatorManager.loadGeneratorTypes();
        dataFile = new File(getDataFolder(), "data.yml");
        generatorManager.loadActiveGenerators(dataFile);

        addRecipes();

        getServer().getPluginManager().registerEvents(new GeneratorListener(this, generatorManager), this);
        this.getCommand("itemgen").setExecutor(new GeneratorCommand(this, generatorManager));

        // Start the generation task asynchronously
        new GenerationTask(this, generatorManager).runTaskTimerAsynchronously(this, 20L, 20L); // Check every second
    }

    @Override
    public void onDisable() {
        generatorManager.saveActiveGenerators(dataFile);
    }

    public void addRecipes() {
        for (GeneratorType type : generatorManager.getGeneratorTypes().values()) {
            if (type.shape == null || type.ingredients == null) continue;

            NamespacedKey recipeKey = new NamespacedKey(this, "generator_" + type.name);
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, GeneratorUtil.createGeneratorItem(type));

            recipe.shape(type.shape.toArray(new String[0]));

            for (java.util.Map.Entry<Character, org.bukkit.Material> entry : type.ingredients.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            Bukkit.addRecipe(recipe);
        }
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }
}