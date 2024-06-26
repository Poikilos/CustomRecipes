package me.mehboss.recipe;

import java.io.File;

/*
 * Mozilla Public License v2.0
 * 
 * Author: Mehboss
 * Copyright (c) 2023 Mehboss
 * Spigot: https://www.spigotmc.org/resources/authors/mehboss.139036/
 *
 * DO NOT REMOVE THIS SECTION IF YOU WISH TO UTILIZE ANY PORTIONS OF THIS CODE
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;

import me.clip.placeholderapi.PlaceholderAPI;
import me.mehboss.recipe.RecipeAPI.Ingredient;

public class CraftManager implements Listener {

	boolean matchedRecipe(CraftingInventory inv) {
		if (inv.getResult() == null || inv.getResult() == new ItemStack(Material.AIR)) {
			if (debug())
				debug("Could not find a recipe to match with!");

			return false;
		}
		return true;
	}

	HashMap<String, Integer> countItemsByMaterial(CraftingInventory inv) {
		HashMap<String, Integer> counts = new HashMap<>();

		for (ItemStack item : inv.getContents()) {
			if (item != null && item.getType() != Material.AIR && !(item.isSimilar(inv.getResult()))) {
				Material material = item.getType();
				String displayName = null; // Default to null if no display name is present

				if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
					displayName = item.getItemMeta().getDisplayName();
				}

				// Generate a unique key for the material and display name combination
				String key = material.toString() + "-" + displayName;

				if (debug())
					debug("countItemsByMaterial Key: " + key);

				int amount = item.getAmount();
				counts.put(key, counts.getOrDefault(key, 0) + amount);
			}
		}

		// Combine counts for items with the same material and display name
		HashMap<String, Integer> combinedCounts = new HashMap<>();
		for (HashMap.Entry<String, Integer> entry : counts.entrySet()) {
			String key = entry.getKey();
			int amount = entry.getValue();
			combinedCounts.put(key, combinedCounts.getOrDefault(key, 0) + amount);
		}

		return combinedCounts;
	}

	boolean amountsMatch(CraftingInventory inv, String recipeName) {
		for (RecipeAPI.Ingredient ingredient : getIngredients(recipeName)) {
			if (ingredient.isEmpty())
				continue;

			Material material = ingredient.getMaterial();
			String displayName = ingredient.getDisplayName();
			int requiredAmount = ingredient.getAmount();

			for (ItemStack item : inv.getMatrix()) {

				if (item != null && item.getType() == material && hasMatchingDisplayName(recipeName, item, displayName,
						ingredient.getIdentifier(), ingredient.hasIdentifier())) {

					if (debug()) {
						debug("ITEM AMOUNT: " + item.getAmount());
						debug("REQUIRED AMOUNT: " + requiredAmount);
					}

					if (item.getAmount() < requiredAmount)
						return false;
				}
			}
		}

		return true; // All required amounts are present in the inventory
	}

	boolean isBlacklisted(CraftingInventory inv, Player p) {
		if (customConfig().getBoolean("blacklist-recipes") == true) {
			for (String item : disabledrecipe()) {

				String[] split = item.split(":");
				String id = split[0];
				ItemStack i = null;

				if (customConfig().getString("vanilla-recipes." + split[0]) != null
						&& !XMaterial.matchXMaterial(split[0]).isPresent()) {
					getLogger().log(Level.SEVERE, "We are having trouble matching the material '" + split[0]
							+ "' to a minecraft item. This can cause issues with the plugin. Please double check you have inputted the correct material "
							+ "ID into the blacklisted config file and try again. If this problem persists please contact Mehboss on Spigot!");
				}

				if (XMaterial.matchXMaterial(split[0]).isPresent())
					i = XMaterial.matchXMaterial(split[0]).get().parseItem();

				if (split.length == 2)
					i.setDurability(Short.valueOf(split[1]));

				if (debug())
					debug("Blacklisted Array Size: " + disabledrecipe().size());

				String getPerm = customConfig().getString("vanilla-recipes." + item + ".permission");

				if (i == null)
					return false;

				if ((NBTEditor.contains(i, "CUSTOM_ITEM_IDENTIFIER", id)
						&& NBTEditor.getString(i, "CUSTOM_ITEM_IDENTIFIER", id).equals(id)
						&& !identifier().containsKey(id)) || inv.getResult().isSimilar(i)) {

					if (getPerm != null && !(getPerm.equalsIgnoreCase("none"))) {
						if (p.hasPermission("crecipe." + getPerm)) {
							if (debug())
								debug("User DOES have permission!");
							return false;
						}
					}

					if (debug())
						debug("Recipe has been set to air");

					sendMessages(p, getPerm);
					inv.setResult(new ItemStack(Material.AIR));
					return true;
				}
			}
		}
		return false;
	}

	// Updated handleShiftClicks method
	@EventHandler
	void handleShiftClicks(CraftItemEvent e) {
		CraftingInventory inv = e.getInventory();

		if (!(matchedRecipe(inv)))
			return;

		if (!(getRecipe().values().contains(inv.getResult())))
			return;

		if (e.getCurrentItem() == null)
			return;

		String findName = configName().get(inv.getResult());
		boolean found = false;
		final ItemStack result = inv.getResult();
		for (String recipes : api().getRecipes()) {
			if (hasIngredients(inv, recipes) && getConfig(findName).getBoolean(findName + ".Shapeless") == true) {
				findName = recipes;
				found = true;
				break;
			}
			if (findIngredients(inv, recipes) && getConfig(findName).getBoolean(findName + ".Shapeless") == false) {
				findName = recipes;
				found = true;
				break;
			}
		}

		if (NBTEditor.contains(inv.getResult(), "CUSTOM_ITEM_IDENTIFIER")) {
			String foundID = NBTEditor.getString(inv.getResult(), "CUSTOM_ITEM_IDENTIFIER");

			if (identifier().containsKey(foundID) && getRecipe().containsKey(identifier().get(foundID)))
				findName = configName().get(identifier().get(foundID));

		}

		if (!found)
			return;

		final String recipeName = findName;

		if (e.getCursor().equals(result) && result.getMaxStackSize() <= 1)
			return;

		// Remove the required items from the inventory

		int itemsToAdd = Integer.MAX_VALUE;
		int itemsToRemove = 0;

		if (debug())
			debug("recipeName for amount check:" + recipeName);

		for (RecipeAPI.Ingredient ingredient : getIngredients(recipeName)) {
			if (ingredient.isEmpty())
				continue;

			Material material = ingredient.getMaterial();
			String displayName = ingredient.getDisplayName();
			final int requiredAmount = ingredient.getAmount();
			boolean hasIdentifier = ingredient.hasIdentifier();

			for (int highest = 1; highest < 10; highest++) {
				ItemStack slot = inv.getItem(highest);
				if (slot == null)
					continue;

				if (slot.getAmount() < requiredAmount)
					continue; // Skip this slot if it doesn't have enough

				int availableItems = slot.getAmount();
				int possibleItemsToRemove = availableItems / requiredAmount;

				if (possibleItemsToRemove < itemsToAdd) {
					itemsToAdd = possibleItemsToRemove;
					itemsToRemove = possibleItemsToRemove * requiredAmount;
				}
			}

			if (debug()) {
				debug("ItemsToRemove: " + itemsToRemove);
				debug("RequiredAmount: " + requiredAmount);
				debug("Identifier: " + ingredient.getIdentifier());
				debug("HasIdentifier: " + hasIdentifier);
				debug("Material: " + material.toString());
				debug("Displayname: " + displayName);
			}

			for (int i = 1; i < 10; i++) {
				ItemStack item = inv.getItem(i);
				int slot = i;

				if (item == null)
					continue;

				if ((ingredient.hasIdentifier() && item.isSimilar(identifier().get(ingredient.getIdentifier())))
						|| (item.getType() == material && hasMatchingDisplayName(recipeName, item, displayName,
								ingredient.getIdentifier(), hasIdentifier))) {
					int itemAmount = item.getAmount();

					if (itemAmount < requiredAmount)
						continue;

					if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
						if (item.getType().toString().contains("_BUCKET")
								&& getConfig(recipeName).isSet(recipeName + ".Consume-Bucket")
								&& !getConfig(recipeName).getBoolean(recipeName + ".Consume-Bucket")) {
							item.setType(XMaterial.BUCKET.parseMaterial());
						} else {
							BukkitScheduler scheduler = Bukkit.getScheduler();
							scheduler.runTask(Main.getInstance(), () -> {
								if ((item.getAmount() + 1) - requiredAmount == 0)
									inv.setItem(slot, null);
								else
									item.setAmount((item.getAmount() + 1) - requiredAmount);
							});
						}
					} else {
						if (item.getType().toString().contains("_BUCKET")
								&& getConfig(recipeName).isSet(recipeName + ".Consume-Bucket")
								&& !getConfig(recipeName).getBoolean(recipeName + ".Consume-Bucket")) {
							item.setType(XMaterial.BUCKET.parseMaterial());
						} else {
							item.setAmount(item.getAmount() - itemsToRemove);
						}
					}
				}
			}
		}

		// Add the result items to the player's inventory
		Player player = (Player) e.getWhoClicked();

		if (e.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {

			BukkitScheduler scheduler = Bukkit.getScheduler();
			scheduler.runTask(Main.getInstance(), () -> {
				if (!(amountsMatch(inv, recipeName)))
					inv.setResult(new ItemStack(Material.AIR));
			});

			ItemStack add = inv.getResult();

			if (e.getCursor() != null && e.getCursor().getType() != Material.AIR)
				add.setAmount(e.getCursor().getAmount() + result.getAmount());

			if (inv.getResult() != null && inv.getResult().equals(result))
				Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> e.setCursor(add), 1L);

			return;
		}

		// Check if the result can be added to the player's inventory
		// Set the cursor item to null (to prevent duplication)

		e.setCursor(null);
		inv.setResult(new ItemStack(Material.AIR));

		getLogger().log(Level.SEVERE, "ITEMSTOADD: " + itemsToAdd);
		for (int i = 0; i < itemsToAdd; i++) {
			if (player.getInventory().firstEmpty() == -1) {
				player.getLocation().getWorld().dropItem(player.getLocation(), result);
				continue;
			}

			player.getInventory().addItem(result);
		}
	}

	public ArrayList<Ingredient> getIngredients(String recipeName) {
		ArrayList<RecipeAPI.Ingredient> allIngredients = api().getIngredients(recipeName);
		ArrayList<RecipeAPI.Ingredient> newIngredients = new ArrayList<>();

		for (RecipeAPI.Ingredient ingredient : allIngredients) {
			// Ignore the slot field and only save material, amount, and slot
			Ingredient newIngr = api().new Ingredient(ingredient.getMaterial(), ingredient.getDisplayName(),
					ingredient.getIdentifier(), ingredient.getAmount(), 0, false);

			if (newIngr.getMaterial() != Material.AIR && newIngr.getMaterial() != null) {
				if (!newIngredients.contains(newIngr)) {
					newIngredients.add(newIngr);
				}
			}
		}

		return newIngredients;
	}

	boolean hasMatchingDisplayName(String recipeName, ItemStack item, String displayName, String identifier,
			boolean hasIdentifier) {
		if (getConfig(recipeName).isSet(recipeName + ".Ignore-Data")
				&& getConfig(recipeName).getBoolean(recipeName + ".Ignore-Data") == true)
			return true;

		if (hasIdentifier)
			return item.isSimilar(identifier().get(identifier));

		if (displayName == null || displayName.equals("false")) {
			if (debug())
				debug("displaynameCheck: " + displayName + " | hasMatchingDisplayName: "
						+ Boolean.valueOf(!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()));
			return !item.hasItemMeta() || !item.getItemMeta().hasDisplayName();
		} else {
			if (debug())
				debug("displaynameCheck: " + displayName + " | hasMatchingDisplayName: "
						+ Boolean.valueOf(item.hasItemMeta() && item.getItemMeta().hasDisplayName()
								&& item.getItemMeta().getDisplayName().equals(displayName)));

			return item.hasItemMeta() && item.getItemMeta().hasDisplayName()
					&& item.getItemMeta().getDisplayName().equals(displayName);
		}
	}

	boolean findIngredients(CraftingInventory inv, String recipeName) {
		ArrayList<String> invMaterials = new ArrayList<>();

		for (ItemStack contents : inv.getMatrix()) {
			if (invMaterials.size() == 9)
				break;

			if (contents == null || contents.getType() == Material.AIR) {
				invMaterials.add("null");
				continue;
			}

			invMaterials.add(contents.getType().toString());
		}

		ArrayList<RecipeAPI.Ingredient> recipeIngredients = api().getIngredients(recipeName);
		int slot = 0;

		for (RecipeAPI.Ingredient ingredient : recipeIngredients) {

			if (ingredient.getMaterial() == null && !invMaterials.get(slot).equals("null")) {
				if (debug())
					debug("[Line 407] findIngredients method is a FAIL for " + recipeName);
				return false;
			}
			if (ingredient.getMaterial() != null
					&& !invMaterials.get(slot).equals(ingredient.getMaterial().toString())) {

				if (debug())
					debug("[Line 414] findIngredients method is a FAIL for " + recipeName);

				return false;
			}

			slot++;
		}

		if (invMaterials.size() != 9) {

			if (debug())
				debug("[Line 425] findIngredients method is a FAIL for " + recipeName);

			return false;
		}
		return true;
	}

	boolean hasIngredients(CraftingInventory inv, String recipeName) {
		ArrayList<String> invMaterials = new ArrayList<>();
		ArrayList<String> ingMaterials = new ArrayList<>();

		for (ItemStack contents : inv.getMatrix()) {

			if (invMaterials.size() == 9)
				break;

			if (contents == null || contents.getType() == Material.AIR) {
				invMaterials.add("null");
				continue;
			}

			invMaterials.add(contents.getType().toString());
		}

		ArrayList<RecipeAPI.Ingredient> recipeIngredients = api().getIngredients(recipeName);
		for (RecipeAPI.Ingredient ingredient : recipeIngredients) {
			if (ingredient.getMaterial() == null)
				ingMaterials.add("null");

			if (ingredient.getMaterial() != null)
				ingMaterials.add(ingredient.getMaterial().toString());
		}

		if (inv.getType() == InventoryType.WORKBENCH
				&& (invMaterials.size() != 9 || ingMaterials.size() != 9 || !invMaterials.containsAll(ingMaterials))) {

			if (debug())
				debug("[Line 458] hasIngredients method is a FAIL for " + recipeName);

			return false;
		}

		if (inv.getType() == InventoryType.CRAFTING && !invMaterials.containsAll(ingMaterials)) {

			if (debug())
				debug("[Line 466] hasIngredients method is a FAIL for " + recipeName);

			return false;
		}

		return true;
	}

	boolean hasVanillaIngredients(CraftingInventory inv) {

		if (inv.getResult().hasItemMeta()
				&& (inv.getResult().getItemMeta().hasDisplayName() || inv.getResult().getItemMeta().hasLore()
						|| NBTEditor.contains(inv.getResult(), "CUSTOM_ITEM_IDENTIFIER")))
			return false;

		for (ItemStack item : inv.getContents()) {
			if (item == null || item.getType() == Material.AIR)
				continue;

			if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName() || item.getItemMeta().hasLore()))
				return false;
		}
		return true;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	void handleCrafting(PrepareItemCraftEvent e) {

		CraftingInventory inv = e.getInventory();

		Boolean passedCheck = true;
		Boolean found = false;
		String recipeName = null;

		if (!(e.getView().getPlayer() instanceof Player))
			return;

		Player p = (Player) e.getView().getPlayer();

		if ((inv.getType() != InventoryType.WORKBENCH && inv.getType() != InventoryType.CRAFTING)
				|| !(matchedRecipe(inv)) || isBlacklisted(inv, p))
			return;

		recipeName = configName().get(inv.getResult());

		for (String recipes : api().getRecipes()) {
			ArrayList<RecipeAPI.Ingredient> recipeIngredients = api().getIngredients(recipes);

			passedCheck = true;
			recipeName = recipes;

			if (!hasIngredients(inv, recipes)) {
				if (debug())
					debug("Recipe: " + recipeName + " | Materials did not match, skipping!");
				passedCheck = false;
				continue;
			}

			if (debug())
				debug("[2] Recipe Config: " + recipeName + " | Found Ingredients: "
						+ Boolean.valueOf(findIngredients(inv, recipeName)));

			passedCheck = true;
			found = true;

			if (getConfig(recipeName).isBoolean(recipeName + ".Shapeless")
					&& getConfig(recipeName).getBoolean(recipeName + ".Shapeless") == true) {
				// runs checks if recipe is shapeless

				ArrayList<String> slotNames = new ArrayList<String>();
				ArrayList<String> recipeNames = new ArrayList<String>();

				ArrayList<Integer> recipeMD = new ArrayList<Integer>();
				ArrayList<Integer> inventoryMD = new ArrayList<Integer>();

				Map<Material, Integer> recipeCount = new HashMap<>();
				Map<Material, Integer> inventoryCount = new HashMap<>();

				for (RecipeAPI.Ingredient names : recipeIngredients) {

					if (names.getMaterial() != null && !(inv.contains(names.getMaterial())))
						break;

					if (names.isEmpty()) {
						recipeCount.put(Material.AIR, recipeCount.getOrDefault(Material.AIR, 0) + 1);
						recipeNames.add("null");
						continue;
					}

					recipeCount.put(names.getMaterial(), recipeCount.getOrDefault(names.getMaterial(), 0) + 1);

					if (names.hasIdentifier() && identifier().containsKey(names.getIdentifier())) {

						if (identifier().get(names.getIdentifier()).hasItemMeta()
								&& identifier().get(names.getIdentifier()).getItemMeta().hasCustomModelData()) {
							recipeMD.add(identifier().get(names.getIdentifier()).getItemMeta().getCustomModelData());
							// grab ingredient model data
						}

						if (identifier().get(names.getIdentifier()).getItemMeta().hasDisplayName()) {
							recipeNames.add(identifier().get(names.getIdentifier()).getItemMeta().getDisplayName());
						} else {
							recipeNames.add("false");
						}
						continue;
					}
					recipeNames.add(names.getDisplayName());
				}

				for (int slot = 1; slot < 10; slot++) {
					if (inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR) {
						slotNames.add("null");
						inventoryCount.put(Material.AIR, inventoryCount.getOrDefault(Material.AIR, 0) + 1);
						continue;
					}

					inventoryCount.put(inv.getItem(slot).getType(),
							inventoryCount.getOrDefault(inv.getItem(slot).getType(), 0) + 1);

					if (!(inv.getItem(slot).getItemMeta().hasDisplayName())) {
						slotNames.add("false");
						continue;
					}

					if (inv.getItem(slot).hasItemMeta() && inv.getItem(slot).getItemMeta().hasCustomModelData()) {
						inventoryMD.add(inv.getItem(slot).getItemMeta().getCustomModelData());
					}

					if (!(NBTEditor.contains(inv.getItem(slot), "CUSTOM_ITEM_IDENTIFIER")))
						continue;

					slotNames.add(inv.getItem(slot).getItemMeta().getDisplayName());
				}

				boolean hasIngredients = true;
				for (Map.Entry<Material, Integer> entry : recipeCount.entrySet()) {
					Material material = entry.getKey();
					int requiredCount = entry.getValue();
					int invCount = inventoryCount.getOrDefault(material, 0);

					if (invCount < requiredCount) {
						hasIngredients = false;
						break;
					}
				}

				if (debug()) {
					debug("Has ingredients: " + hasIngredients);
				}

				if (!hasIngredients) {
					passedCheck = false;
					continue;
				}

				if (debug()) {
					debug("ContainsAll: " + slotNames.containsAll(recipeNames) + " | AmountsMatch: "
							+ amountsMatch(inv, recipeName));

					getLogger().log(Level.SEVERE, "SN SIZE: " + slotNames.size());
					getLogger().log(Level.SEVERE, "RN SIZE: " + recipeNames.size());
					for (String names : slotNames)
						getLogger().log(Level.SEVERE, names);
					for (String names : recipeNames)
						getLogger().log(Level.SEVERE, names);
				}

				if (getConfig(recipeName).isSet(recipeName + ".Ignore-Model-Data")
						&& getConfig(recipeName).getBoolean(recipeName + ".Ignore-Model-Data") == false
						&& recipeMD.size() != inventoryMD.size()
						&& (!recipeMD.containsAll(inventoryMD) || !inventoryMD.containsAll(recipeMD))) {
					passedCheck = false;
					continue;
				}

				if ((getConfig(recipeName).isSet(recipeName + ".Ignore-Data")
						&& getConfig(recipeName).getBoolean(recipeName + ".Ignore-Data") == false)
						&& (slotNames.size() != 9 || recipeNames.size() != 9 || !(slotNames.containsAll(recipeNames))
								|| !(amountsMatch(inv, recipeName)))) {
					passedCheck = false;
					continue;
				}

			} else {

				if (!findIngredients(inv, recipes)) {
					if (debug())
						debug("Recipe: " + recipeName + " | Materials did not match, skipping!");
					found = false;
					continue;
				}

				// runs check for non-shapeless recipes
				if (debug())
					debug("Checking for RECIPE: " + recipeName);

				if (getConfig(recipeName).isSet(recipeName + ".Ignore-Data")
						&& getConfig(recipeName).getBoolean(recipeName + ".Ignore-Data") == true)
					continue;

				int i = 0;

				for (RecipeAPI.Ingredient ingredient : recipeIngredients) {
					i++;

					if (ingredient.getMaterial() != null && !(inv.contains(ingredient.getMaterial()))) {
						passedCheck = false;
						break;
					}

					if (inv.getItem(i) == null && !(ingredient.isEmpty())) {
						passedCheck = false;

						if (debug())
							debug("[1] SKIPPING: " + recipeName);
						break;
					}

					if (inv.getItem(i) != null && inv.getItem(i).getType() != Material.AIR) {
						ItemMeta meta = inv.getItem(i).getItemMeta();

						if (ingredient.hasIdentifier() && identifier().containsKey(ingredient.getIdentifier())
								&& identifier().get(ingredient.getIdentifier()).isSimilar(inv.getItem(i))
								&& (NBTEditor.contains(inv.getItem(i), "CUSTOM_ITEM_IDENTIFIER")
										&& NBTEditor.getString(inv.getItem(i), "CUSTOM_ITEM_IDENTIFIER")
												.equals(ingredient.getIdentifier()))) {
							continue;

						} else if (ingredient.hasIdentifier()) {

							if (debug())
								debug("ingredient.hasIdentifier()");

							passedCheck = false;
							break;
						}

						// checks if displayname is null
						if ((!meta.hasDisplayName() && ingredient.hasDisplayName())
								|| (meta.hasDisplayName() && !ingredient.hasDisplayName())) {
							passedCheck = false;
							if (debug()) {
								debug("[3] SKIPPING: " + recipeName);
								debug("[3] IngDN: " + ingredient.hasDisplayName());
								debug("[3] MetaDN: " + meta.hasDisplayName());
								debug("[3] IngSlot: " + ingredient.getSlot());
								debug("[3] IngDN: " + ingredient.hasDisplayName());
							}
							break;
						}

						if (ingredient.hasDisplayName() && meta.hasDisplayName()
								&& !(ingredient.getDisplayName().equals(meta.getDisplayName()))) {
							passedCheck = false;
							if (debug()) {
								debug("[4] SKIPPING: " + recipeName);
								debug("[4] ING DN: " + ingredient.getDisplayName());
								debug("[4] META DN: " + meta.getDisplayName());
								break;
							}
						}

						// checks amounts
						if (!(amountsMatch(inv, recipeName))) {
							if (debug())
								debug("[5] SKIPPING: " + recipeName);
							passedCheck = false;
							break;
						}
					}
				}
			}

			if (passedCheck && found) {
				break;
			}
		}

		if (passedCheck && (getConfig(recipeName).getBoolean(recipeName + ".Enabled") == false
				|| ((getConfig(recipeName).isSet(recipeName + ".Permission")
						&& (!(p.hasPermission(getConfig(recipeName).getString(recipeName + ".Permission")))))))) {
			inv.setResult(new ItemStack(Material.AIR));
			sendNoPermsMessage(p);
			return;
		}

		if (hasVanillaIngredients(inv))
			return;

		if (getConfig(recipeName).isSet(recipeName + ".Disabled-Worlds")) {
			for (String string : getConfig(recipeName).getStringList(recipeName + ".Disabled-Worlds")) {
				if (p.getWorld().getName().equalsIgnoreCase(string)) {
					inv.setResult(new ItemStack(Material.AIR));
					sendMessages(p, "none");
					return;
				}
			}
		}

		if ((!passedCheck) || (found) || (passedCheck && !found))
			inv.setResult(new ItemStack(Material.AIR));

		if (passedCheck && found && getRecipe().containsKey(recipeName.toLowerCase())) {

			List<String> withPlaceholders = null;

			if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
				withPlaceholders = getRecipe().get(recipeName.toLowerCase()).hasItemMeta()
						&& getRecipe().get(recipeName.toLowerCase()).getItemMeta().hasLore()
								? PlaceholderAPI.setPlaceholders(p,
										getRecipe().get(recipeName.toLowerCase()).getItemMeta().getLore())
								: null;

			ItemStack finalItem = new ItemStack(getRecipe().get(recipeName.toLowerCase()));
			ItemMeta finalItemm = finalItem.getItemMeta();

			if (withPlaceholders != null) {
				finalItemm.setLore(withPlaceholders);
				finalItem.setItemMeta(finalItemm);
			}

			inv.setResult(finalItem);
		}

		if (debug())
			debug("Final Recipe Match: " + passedCheck + "| Recipe Pulled: " + recipeName + "| Found: " + found);
	}

	boolean isInt(String s) {
		try {
			Integer.parseInt(s);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

	boolean debug() {
		return Main.getInstance().debug;
	}

	void debug(String st) {
		if (debug()) {
			getLogger().log(Level.WARNING, "-----------------");
			getLogger().log(Level.WARNING, "DEBUG IS TURNED ON! PLEASE CONTACT MEHBOSS ON SPIGOT FOR ASSISTANCE");
		}

		getLogger().log(Level.WARNING, st);

		if (debug())
			getLogger().log(Level.WARNING, "-----------------");
	}

	void sendMessages(Player p, String s) {
		Main.getInstance().sendMessages(p, s);
	}

	void sendNoPermsMessage(Player p) {
		if (debug())
			debug("Sending noPERMS message now");

		Main.getInstance().sendMessage(p);
	}

	HashMap<String, ItemStack> identifier() {
		return Main.getInstance().identifier;
	}

	ArrayList<String> disabledrecipe() {
		return Main.getInstance().disabledrecipe;
	}

	HashMap<String, List<Material>> ingredients() {
		return Main.getInstance().ingredients;
	}

	HashMap<String, ItemStack> getRecipe() {
		return Main.getInstance().giveRecipe;
	}

	HashMap<ItemStack, String> configName() {
		return Main.getInstance().configName;
	}

	Logger getLogger() {
		return Main.getInstance().getLogger();
	}

	FileConfiguration customConfig() {
		return Main.getInstance().customConfig;
	}

	FileConfiguration getConfig(String recipeName) {
		File dataFolder = Main.getInstance().getDataFolder();
		File recipesFolder = new File(dataFolder, "recipes");
		File recipeFile = new File(recipesFolder, recipeName + ".yml");

		return YamlConfiguration.loadConfiguration(recipeFile);
	}

	RecipeAPI api() {
		return Main.getInstance().api;
	}
}
