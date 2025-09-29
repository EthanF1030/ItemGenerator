package com.demondev.itemGenerator;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;

public class GeneratorCommand implements CommandExecutor {

    private final ItemGenerator plugin;
    private final GeneratorManager manager;

    public GeneratorCommand(ItemGenerator plugin, GeneratorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/itemgen reload - Reload config");
            sender.sendMessage(ChatColor.YELLOW + "/itemgen list - List generator types");
            sender.sendMessage(ChatColor.YELLOW + "/itemgen give <type> [player] - Give a generator item");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("itemgen.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            // Remove old recipes before clearing types
            for (String typeName : new ArrayList<>(manager.getGeneratorTypes().keySet())) {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "generator_" + typeName);
                Bukkit.removeRecipe(recipeKey);
            }

            plugin.reloadConfig();
            manager.getGeneratorTypes().clear();
            manager.clearActiveGenerators();
            manager.loadGeneratorTypes();
            manager.loadActiveGenerators(new File(plugin.getDataFolder(), "data.yml"));


            plugin.addRecipes();

            sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("itemgen.use")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Available generators:");
            for (String key : manager.getGeneratorTypes().keySet()) {
                sender.sendMessage(ChatColor.YELLOW + "- " + key);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("itemgen.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /itemgen give <type> [player]");
                return true;
            }
            String typeName = args[1];
            GeneratorType type = manager.getGeneratorTypes().get(typeName);
            if (type == null) {
                sender.sendMessage(ChatColor.RED + "Invalid generator type.");
                return true;
            }
            Player target = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player ? (Player) sender : null);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not specified.");
                return true;
            }
            target.getInventory().addItem(GeneratorUtil.createGeneratorItem(type));
            sender.sendMessage(ChatColor.GREEN + "Gave " + typeName + " generator to " + target.getName());
            return true;
        }

        return false;
    }
}