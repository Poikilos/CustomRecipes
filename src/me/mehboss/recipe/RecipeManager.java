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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice.ExactChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;

import dev.lone.itemsadder.api.CustomStack;
import io.github.bananapuncher714.nbteditor.NBTEditor;
import io.github.bananapuncher714.nbteditor.NBTEditor.NBTCompound;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.mehboss.utils.RecipeUtil;
import me.mehboss.utils.RecipeUtil.Ingredient;
import me.mehboss.utils.RecipeUtil.Recipe;
import me.mehboss.utils.RecipeUtil.Recipe.RecipeType;
import net.advancedplugins.ae.api.AEAPI;
import valorless.havenbags.hooks.CustomRecipes;
import valorless.havenbags.hooks.CustomRecipes.BagInfo;

public class RecipeManager {

	RecipeUtil recipeUtil = Main.getInstance().recipeUtil;
	FileConfiguration recipeConfig = null;

	boolean hasHavenBag() {
		if (Main.getInstance().hasHavenBags)
			return true;
		return false;
	}

	boolean isHavenBag(String item) {
		if (getConfig().isSet(item + ".Identifier")
				&& getConfig().getString(item + ".Identifier").split("-")[0].contains("havenbags"))
			return true;

		return false;
	}

	void handleBucketConsume(Material material, String item, Recipe recipe) {
		if (getConfig().isBoolean(item + ".Consume-Bucket"))
			recipe.setConsume(getConfig().getBoolean(item + ".Consume-Bucket"));
	}

	void handlePlaceable(String item, Recipe recipe) {
		if (getConfig().isBoolean(item + ".Placeable"))
			recipe.setPlaceable(getConfig().getBoolean(item + ".Placeable"));
	}

	void handleCooldown(String item, Recipe recipe) {
		if (getConfig().isInt(item + ".Cooldown") && getConfig().getInt(item + ".Cooldown") != -1)
			recipe.setCooldown(getConfig().getInt(item + ".Cooldown"));
	}

	void handleIgnoreFlags(String item, Recipe recipe) {
		if (getConfig().isBoolean(item + ".Ignore-Data"))
			recipe.setIgnoreData(getConfig().getBoolean(item + ".Ignore-Data"));

		if (getConfig().isBoolean(item + ".Ignore-Model-Data"))
			recipe.setIgnoreModelData(getConfig().getBoolean(item + ".Ignore-Model-Data"));

		if (getConfig().isBoolean(item + ".Exact-Choice"))
			recipe.setExactChoice(getConfig().getBoolean(item + ".Exact-Choice"));

		if (getConfig().isBoolean(item + ".Auto-Discover-Recipe"))
			recipe.setDiscoverable(getConfig().getBoolean(item + ".Auto-Discover-Recipe"));

		if (getConfig().isSet(item + ".Book-Category"))
			recipe.setBookCategory(getConfig().getString(item + ".Book-Category").toUpperCase());
	}

	void handleFurnaceData(Recipe recipe, String configPath) {
		if (getConfig().isInt(configPath + ".Cooktime"))
			recipe.setCookTime(getConfig().getInt(configPath + ".Cooktime"));

		if (getConfig().isInt(configPath + ".Experience"))
			recipe.setExperience(getConfig().getInt(configPath + ".Experience"));
	}

	void handleAnvilData(Recipe recipe, String configPath) {
		if (getConfig().isInt(configPath + ".Repair-Cost"))
			recipe.setRepairCost(getConfig().getInt(configPath + ".Cost"));
	}

	@SuppressWarnings("unchecked")
	ItemStack applyCustomTags(ItemStack item, String recipe) {
		try {
			List<Map<String, Object>> customTags = getCustomTags(recipe);
			for (Map<String, Object> tagEntry : customTags) {
				List<String> path = (List<String>) tagEntry.get("path");
				Object value = tagEntry.get("value");

				logDebug("Handling tag path " + path.get(0) + "..", recipe);

				// Check if this is an AttributeModifier entry
				if (path.get(0).equalsIgnoreCase("AttributeModifiers")) {
					List<Map<String, Object>> modifiers = (List<Map<String, Object>>) value;

					for (Map<String, Object> modifier : modifiers) {
						// Apply attribute modifier
						item = applyAttributeModifier(item, modifier);
					}
				} else if (path.get(0).equalsIgnoreCase("SpawnerID")) {
					logDebug("Attempting to apply path tags now..", recipe);

					if (XMaterial.matchXMaterial(item.getType()) != XMaterial.SPAWNER)
						return item;

					EntityType entity = EntityType.valueOf((String) value);
					logDebug("Attempting to apply entity type " + entity + " to a spawner..", recipe);

					BlockStateMeta bsm = (BlockStateMeta) item.getItemMeta();
					CreatureSpawner cs = (CreatureSpawner) bsm.getBlockState();

					if (entity == null) {
						logDebug("Could not find entitytype " + entity + "! Skipping application of tag.", recipe);
						return item;
					}

					logDebug("Successfully set " + entity + " to the spawner..", recipe);
					cs.setSpawnedType(EntityType.valueOf((String) value));
					bsm.setBlockState(cs);
					item.setItemMeta(bsm);
					return item;

				} else if (path.get(0).equalsIgnoreCase("Potion")) {
					ItemStack potion = item;
					PotionMeta meta = (PotionMeta) potion.getItemMeta();

					boolean extended = tagEntry.containsKey("extended") && (boolean) tagEntry.get("extended");
					boolean upgraded = tagEntry.containsKey("upgraded") && (boolean) tagEntry.get("upgraded");

					meta.setBasePotionData(new PotionData(PotionType.valueOf((String) value), extended, upgraded));
					potion.setItemMeta(meta);

					return potion;
				} else {
					// Apply general NBT tag
					item = applyNBT(item, value, path.toArray(new String[0]));
				}
			}
			return item;
		} catch (Exception e) {
			logError("Could not apply custom tags! This generally happens when the operations are incorrect.", recipe);
			e.printStackTrace();
			return item;
		}
	}

	ItemStack applyAttributeModifier(ItemStack item, Map<String, Object> modifier) {
		// Retrieve necessary fields
		String name = (String) modifier.get("Name");
		String attributeName = (String) modifier.get("AttributeName");

		double amount = (double) modifier.get("Amount");
		int operation = (int) modifier.get("Operation");
		String slot = (String) modifier.get("Slot");

		// Debugging output
		logDebug("Applying attribute modifier:", "null");
		logDebug("Name: " + name + ", AttributeName: " + attributeName + ", Amount: " + amount + ", Operation: "
				+ operation + ", Slot: " + slot, "null");

		int[] uuid = { 0, 0, 0, 0 };

		NBTCompound compound = NBTEditor.getNBTCompound(item);
		compound = NBTEditor.set(compound, attributeName, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers",
				NBTEditor.NEW_ELEMENT, "AttributeName");
		compound = NBTEditor.set(compound, name, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0, "Name");
		compound = NBTEditor.set(compound, amount, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0, "Amount");
		compound = NBTEditor.set(compound, operation, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0,
				"Operation");
		compound = NBTEditor.set(compound, slot, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0, "Slot");
		compound = NBTEditor.set(compound, uuid, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0, "UUID");
		compound = NBTEditor.set(compound, 99L, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0, "UUIDMost");
		compound = NBTEditor.set(compound, 77530600L, NBTEditor.ITEMSTACK_COMPONENTS, "AttributeModifiers", 0,
				"UUIDLeast");

		item = NBTEditor.getItemFromTag(compound);
		return item;
	}

	ItemStack applyNBT(ItemStack item, Object value, String... path) {

		if (path == null || path.length == 0) {
			throw new IllegalArgumentException("NBT path cannot be null or empty");
		}
		return NBTEditor.set(item, value, (Object[]) path);
	}

	@SuppressWarnings("unchecked")
	List<Map<String, Object>> getCustomTags(String recipe) {
		List<Map<?, ?>> rawList = getConfig().getMapList(recipe + ".Custom-Tags");
		List<Map<String, Object>> castedList = new ArrayList<>();

		for (Map<?, ?> rawMap : rawList) {
			castedList.add((Map<String, Object>) (Map<?, ?>) rawMap);
		}

		return castedList;
	}

	ItemStack handleBagCreation(Material bagMaterial, String item) {
		String bagTexture = getConfig().isString(item + ".Bag-Texture") ? getConfig().getString(item + ".Bag-Texture")
				: "none";
		boolean canBind = getConfig().isBoolean(item + ".Can-Bind") ? getConfig().getBoolean(item + ".Can-Bind") : true;
		int bagSize = getConfig().isInt(item + ".Bag-Size") ? getConfig().getInt(item + ".Bag-Size") : 1;
		int bagCMD = 0;

		CustomRecipes havenBags = new CustomRecipes();
		BagInfo bInfo = havenBags.new BagInfo(null, bagMaterial, bagSize, canBind, bagTexture);

		if (Main.getInstance().serverVersionAtLeast(1, 14) && getConfig().isSet(item + ".Custom-Model-Data")
				&& isInt(getConfig().getString(item + ".Custom-Model-Data"))) {
			bagCMD = getConfig().getInt(item + ".Custom-Model-Data");
		}

		bInfo.setModelData(bagCMD);

		ItemStack bag = CustomRecipes.CreateBag(bInfo);
		return bag;
	}

	@SuppressWarnings("deprecation")
	ItemStack handleItemDamage(ItemStack i, String item, String damage, Optional<XMaterial> type, int amount) {
		if (!getConfig().isSet(item + ".Item-Damage") || damage.equalsIgnoreCase("none")) {
			return new ItemStack(type.get().parseMaterial(), amount);
		} else {
			try {
				return new ItemStack(type.get().parseMaterial(), amount, Short.valueOf(damage));
			} catch (Exception e) {
				Main.getInstance().getLogger().log(Level.WARNING, "Couldn't apply item damage to the recipe " + item
						+ ". Please double check that it is a valid item-damage. Skipping for now.");
				return new ItemStack(type.get().parseMaterial(), amount);
			}
		}
	}

	ItemStack handleIdentifier(ItemStack i, String item, Recipe recipe) {
		if (!getConfig().isSet(item + ".Identifier"))
			return i;

		String identifier = getConfig().getString(item + ".Identifier");
		recipe.setKey(identifier);

		if (getConfig().getBoolean(item + ".Custom-Tagged") == true)
			i = NBTEditor.set(i, identifier, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER");

		if (identifier.equalsIgnoreCase("LifeStealHeart")) {
			i.getItemMeta().getPersistentDataContainer().set(new NamespacedKey(Main.getInstance(), "heart"),
					PersistentDataType.INTEGER, 1);
		}

		return i;
	}

	@SuppressWarnings("deprecation")
	ItemStack handleDurability(ItemStack i, String item) {
		if (getConfig().isSet(item + ".Durability"))
			if (!getConfig().getString(item + ".Durability").equals("100"))
				i.setDurability(Short.valueOf(getConfig().getString(item + ".Durability")));

		return i;
	}

	ItemStack handleEnchants(ItemStack i, String item) {
		if (getConfig().isSet(item + ".Enchantments")) {
			if (!getConfig().isSet(item + ".Enchantments")) {
				logError("Enchantment section is not valid, skipping..", item);
				return i;
			}

			for (String e : getConfig().getStringList(item + ".Enchantments")) {
				String[] breakdown = e.split(":");
				String enchantment = breakdown[0];

				if (breakdown.length >= 3)
					continue;

				if (!(XEnchantment.matchXEnchantment(enchantment).isPresent())) {
					logError("Enchantment " + enchantment + " is not valid, skipping..", item);
					continue;
				}

				Enchantment parsedEnchant = XEnchantment.matchXEnchantment(enchantment).get().getEnchant();
				int lvl = Integer.parseInt(breakdown[1]);
				i.addUnsafeEnchantment(parsedEnchant, lvl);
			}
		}
		return i;
	}

	ItemStack handleCustomEnchants(ItemStack i, String item) {
		if (getConfig().isSet(item + ".Enchantments")) {

			try {
				for (String e : getConfig().getStringList(item + ".Enchantments")) {
					Boolean applied = false;
					String[] breakdown = e.split(":");

					if (breakdown.length != 3)
						continue;

					String enchantment = breakdown[1].toLowerCase();
					int lvl = Integer.parseInt(breakdown[2]);

					if (Main.getInstance().hasAE && AEAPI.isAnEnchantment(enchantment)) {
						i = AEAPI.applyEnchant(enchantment, lvl, i);
						applied = true;
						continue;
					}

					if (Main.getInstance().hasEE) {
						NamespacedKey enchantKey = NamespacedKey.fromString("minecraft:" + enchantment);
						if (Enchantment.getByKey(enchantKey) != null) {
							Enchantment enchant = Enchantment.getByKey(enchantKey);
							i.addUnsafeEnchantment(enchant, lvl);
							applied = true;
							continue;
						}
					}

					if (!applied && !XEnchantment.matchXEnchantment(enchantment).isPresent())
						logError("Could not find custom enchantment " + enchantment + "..", item);
				}
			} catch (Exception e) {
				logError("Enchantment section is not valid. Skipping..", item);
			}
		}
		return i;
	}

	ItemMeta handleFlags(String item, ItemMeta m) {

		if (!(Main.getInstance().serverVersionAtLeast(1, 14)))
			return m;

		if (getConfig().isSet(item + ".Item-Flags")) {
			for (String flag : getConfig().getStringList(item + ".Item-Flags")) {
				try {
					m.addItemFlags(ItemFlag.valueOf(flag));
				} catch (IllegalArgumentException e) {
					logError("Could not add the item flag (" + flag
							+ ").. This item flag could not be found so we will be skipping this flag for now. Please visit https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/inventory/ItemFlag.html for a list of valid flags.",
							item);
					continue;
				}
			}
		}
		return m;
	}

	ItemMeta handleDisplayname(String item, ItemStack recipe) {
		ItemMeta itemMeta = recipe.getItemMeta();

		if (getConfig().isSet(item + ".Name")) {
			logDebug("Applying displayname..", item);
			logDebug("Displayname: " + getConfig().getString(item + ".Name"), item);

			itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString(item + ".Name")));
		}
		return itemMeta;
	}

	ItemMeta handleLore(String item, ItemMeta m) {

		ArrayList<String> loreList = new ArrayList<String>();

		if (!getConfig().isSet(item + ".Lore"))
			return m;

		if (m.hasLore() && getConfig().getBoolean(item + ".Hide-Enchants") == false) {
			loreList = (ArrayList<String>) m.getLore();
		}

		for (String Item1Lore : getConfig().getStringList(item + ".Lore")) {
			String crateLore = (Item1Lore.replaceAll("(&([a-fk-o0-9]))", "\u00A7$2"));
			loreList.add(crateLore);
		}

		if (!(loreList.isEmpty())) {
			m.setLore(loreList);
		}

		return m;
	}

	ItemMeta handleHideEnchants(String item, ItemMeta m) {
		if (getConfig().isSet(item + ".Hide-Enchants") && getConfig().getBoolean(item + ".Hide-Enchants") == true) {
			m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}
		return m;
	}

	ItemMeta handleCustomModelData(String item, ItemMeta m) {
		if (!(Main.getInstance().serverVersionAtLeast(1, 14)))
			return m;

		if (getConfig().isSet(item + ".Custom-Model-Data")
				&& isInt(getConfig().getString(item + ".Custom-Model-Data"))) {
			try {
				Integer data = Integer.parseInt(getConfig().getString(item + ".Custom-Model-Data"));
				m.setCustomModelData(data);
			} catch (Exception e) {
				logError(
						"Error occured while setting custom model data. This feature is only available for MC 1.14 or newer!",
						item);
			}
		}
		return m;
	}

	ItemMeta handleAttributes(String item, ItemMeta m) {
		if (!(Main.getInstance().serverVersionAtLeast(1, 12)))
			return m;

		if (getConfig().isSet(item + ".Attribute")) {
			for (String split : getConfig().getStringList(item + ".Attribute")) {
				String[] st = split.split(":");
				String attribute = st[0];
				double attributeAmount = Double.valueOf(st[1]);
				String equipmentSlot = null;

				if (st.length > 2)
					equipmentSlot = st[2];

				try {
					AttributeModifier modifier;
					if (equipmentSlot == null)
						modifier = new AttributeModifier(attribute, attributeAmount,
								AttributeModifier.Operation.ADD_NUMBER);
					else
						modifier = new AttributeModifier(UUID.randomUUID(), attribute, attributeAmount,
								AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.valueOf(equipmentSlot));

					m.addAttributeModifier(Attribute.valueOf(attribute), modifier);
				} catch (Exception e) {
					logError("Could not add attribute " + attribute + ", " + attributeAmount + ", " + equipmentSlot
							+ ", skipping for now..", item);
				}
			}
		}
		return m;
	}

	private String[] customItemChecks(String item, String recipeName, String ingredientIdentifier, Boolean debug) {
	    String[] configSplit = null;

	    if (item != null) {
	        configSplit = getConfig().isSet(item + ".Item") ? getConfig().getString(item + ".Item").split(":") : null;
	    } else if (ingredientIdentifier != null) {
	        if (Main.getInstance().serverVersionLessThan(1, 15)) {
				logError("Issue detected with recipe.. ", recipeName);
				logError("Your server version does not support custom items from 'ItemsAdder' or 'MythicMobs'", recipeName);
				logError("You must be on 1.15 or higher!", recipeName);
	            return null;
	        }
	        configSplit = ingredientIdentifier.split(":");
	    }

	    if (configSplit == null || configSplit.length < 2) {
	        return null;
	    }

	    return configSplit;
	}
	
	public ItemStack handleMythicItemCheck(String item, String recipeName, String ingredientIdentifier, Boolean debug) {

		String[] configSplit = customItemChecks(item, recipeName, ingredientIdentifier, debug);
		
		if (configSplit == null)
			return null;
		
		String customPlugin = configSplit[0];
		String customItem = configSplit[1];
		
		if (customPlugin.equalsIgnoreCase("mythicmobs")) {
			if (!Main.getInstance().hasMythicMobsPlugin()) {
				if (debug) {
					logError("Issue detected with recipe..", recipeName);
					logError("Found custom item from the MythicMobs plugin, but did not detect the plugin installed!",
							recipeName);
					logError("Attempting to ignore..", recipeName);
				}
				return null;
			}
		    ItemStack mythicItem = MythicBukkit.inst().getItemManager().getItemStack(customItem);
		    
			if (customItem == null || mythicItem == null) {
				if (debug) {
					logError("Issue detected with recipe.. ", recipeName);
					logError(
							"Custom item from MythicMobs was attempted for the result or ingredient identifier, but the item was not found.",
							recipeName);
					logError("Attempting to ignore..", recipeName);
				}
				return null;
			}
			if (debug)
				logError("Found custom item from ItemsAdder: " + customItem, recipeName);
			return mythicItem;
		}
		
		return null;
	}
	
	public ItemStack handleItemAdderCheck(String item, String recipeName, String ingredientIdentifier, Boolean debug) {

		String[] configSplit = customItemChecks(item, recipeName, ingredientIdentifier, debug);
		
		if (configSplit == null)
			return null;
		
		String customPlugin = configSplit[0];
		String customItem = configSplit[1];
		
		if (customPlugin.equalsIgnoreCase("itemsadder")) {
			if (!Main.getInstance().hasItemsAdderPlugin()) {
				if (debug) {
					logError("Issue detected with recipe.. ", recipeName);
					logError("Found custom item from the ItemsAdder plugin, but did not detect the plugin installed!",
							recipeName);
					logError("Attempting to ignore..", recipeName);
				}
				return null;
			}

			CustomStack stack = CustomStack.getInstance(customItem);
			if (customItem == null || stack == null) {
				if (debug) {
					logError("Issue detected with recipe.. ", recipeName);
					logError(
							"Custom item from ItemsAdder was attempted for the result or ingredient identifier, but the item was not found.",
							recipeName);
					logError("Attempting to ignore..", recipeName);
				}
				return null;
			}
			if (debug)
				logError("Found custom item from ItemsAdder: " + customItem, recipeName);
			return stack.getItemStack();
		}
		
		return null;
	}

	public void addRecipes() {
		File recipeFolder = new File(Main.getInstance().getDataFolder(), "recipes");
		if (!recipeFolder.exists()) {
			recipeFolder.mkdirs();
		}

		File[] recipeFiles = recipeFolder.listFiles();
		if (recipeFiles == null) {
			logError("Could not add recipes because none were found to load!", "null");
			return;
		}

		recipeLoop: for (File recipeFile : recipeFiles) {
			recipeConfig = YamlConfiguration.loadConfiguration(recipeFile);
			String item = recipeFile.getName().replace(".yml", "");

			if (!(recipeConfig.isConfigurationSection(item))) {
				Main.getInstance().getLogger().log(Level.WARNING,
						"Could not find configuration section " + item
								+ " in the recipe file that must match its filename: " + item
								+ ".yml - (CaSeSeNsItIvE) - Skipping this recipe");
				continue;
			}

			Recipe recipe = new Recipe(item);

			List<String> gridRows = getConfig().getStringList(item + ".ItemCrafting");
			String converter = getConfig().isString(item + ".Converter")
					? getConfig().getString(item + ".Converter").toLowerCase()
					: "converterNotDefined";
			int amountRequirement = 9;

			logDebug("Attempting to add recipe..", recipe.getName());

			if (!(hasHavenBag()) && isHavenBag(item)) {
				logError("Error loading recipe..", recipe.getName());
				logError("Found a havenbag recipe, but can not find the havenbags plugin. Skipping recipe..",
						recipe.getName());
				continue;
			}

			if (!getConfig().isConfigurationSection(item + ".Ingredients")) {
				logError("Error loading recipe..", recipe.getName());
				logError("Could not locate the ingredients section. Please double check formatting. Skipping recipe..",
						recipe.getName());
				continue;
			}

			switch (converter) {
			case "stonecutter":
				recipe.setType(RecipeType.STONECUTTER);
				amountRequirement = 1;
				break;
			case "furnace":
				recipe.setType(RecipeType.FURNACE);
				handleFurnaceData(recipe, item);
				amountRequirement = 2;
				break;
			case "blastfurnace":
				recipe.setType(RecipeType.BLASTFURNACE);
				handleFurnaceData(recipe, item);
				amountRequirement = 2;
				break;
			case "smoker":
				recipe.setType(RecipeType.SMOKER);
				handleFurnaceData(recipe, item);
				amountRequirement = 2;
			case "anvil":
				recipe.setType(RecipeType.ANVIL);
				handleAnvilData(recipe, item);
				amountRequirement = 2;
				break;
			default:
				if (getConfig().isBoolean(item + ".Shapeless") && getConfig().getBoolean(item + ".Shapeless") == true) {
					recipe.setType(RecipeType.SHAPELESS);
					break;
				} else {
					recipe.setType(RecipeType.SHAPED);
					break;
				}
			}

			if (!Main.getInstance().serverVersionAtLeast(1, 14) && (recipe.getType() == RecipeType.STONECUTTER
					|| recipe.getType() == RecipeType.BLASTFURNACE || recipe.getType() == RecipeType.SMOKER)) {
				logError("Error loading recipe..", recipe.getName());
				logError("Found recipe type  " + converter + ", but your server version is below 1.14.",
						recipe.getName());
				continue;
			}

			// HavenBag detected, but converter is not SHAPED or SHAPELESS
			if (recipe.getType() != RecipeType.SHAPED && recipe.getType() != RecipeType.SHAPELESS && isHavenBag(item)) {
				logError("Error loading recipe..", recipe.getName());
				logError("Got " + recipe.getType() + ", but the recipe is a havenbag recipe! Skipping..",
						recipe.getName());
				continue;
			}

			String damage = getConfig().getString(item + ".Item-Damage");
			int amount = getConfig().isInt(item + ".Amount") ? getConfig().getInt(item + ".Amount") : 1;
			Optional<XMaterial> type = getConfig().isString(item + ".Item")
					? XMaterial.matchXMaterial(getConfig().getString(item + ".Item").toUpperCase())
					: null;

			ItemStack i = handleItemAdderCheck(item, item, null, true);
			i = (i != null) ? i : handleMythicItemCheck(item, item, null, true);

			ItemMeta m = i != null ? i.getItemMeta() : null;

			if (i == null) {
				if (!(validMaterial(recipe.getName(), getConfig().getString(item + ".Item"), type)))
					continue;

				i = handleItemDamage(i, item, damage, type, amount);
				i = handleEnchants(i, item);
				i = handleCustomEnchants(i, item);
				i = applyCustomTags(i, item);
				m = handleDisplayname(item, i);
				m = handleHideEnchants(item, m);
				m = handleCustomModelData(item, m);
				m = handleAttributes(item, m);
				m = handleFlags(item, m);
				m = handleLore(item, m);
				i.setItemMeta(m);
			}

			i = handleDurability(i, item);
			i = handleIdentifier(i, item, recipe);

			if (isHavenBag(item))
				i = handleBagCreation(i.getType(), item);

			recipe.setResult(i);
			handleIgnoreFlags(item, recipe);
			handleCooldown(item, recipe);
			handlePlaceable(item, recipe);
			handleBucketConsume(i.getType(), item, recipe);

			if (getConfig().getBoolean(item + ".Custom-Tagged") == true)
				recipe.setTagged(true);

			ArrayList<String> slotsAbbreviations = new ArrayList<String>();
			String row1 = gridRows.get(0);
			String row2 = gridRows.get(1);
			String row3 = gridRows.get(2);

			slotsAbbreviations.add(row1.split("")[0]);
			slotsAbbreviations.add(row1.split("")[1]);
			slotsAbbreviations.add(row1.split("")[2]);

			slotsAbbreviations.add(row2.split("")[0]);
			slotsAbbreviations.add(row2.split("")[1]);
			slotsAbbreviations.add(row2.split("")[2]);

			slotsAbbreviations.add(row3.split("")[0]);
			slotsAbbreviations.add(row3.split("")[1]);
			slotsAbbreviations.add(row3.split("")[2]);

			recipe.setRow(1, row1);
			recipe.setRow(2, row2);
			recipe.setRow(3, row3);

			int slot = 0;
			int count = 0;

			for (String abbreviation : slotsAbbreviations) {
				// Iterate through the specified 9x9 grid and get it from the Ingredients
				// section..
				slot++;
				count++;

				RecipeUtil.Ingredient recipeIngredient;

				// adds the abbreviation for AIR automatically to the recipe
				if (abbreviation.equalsIgnoreCase("X")) {
					recipeIngredient = new RecipeUtil.Ingredient("X", Material.AIR);
					recipeIngredient.setSlot(slot);
					recipe.addIngredient(recipeIngredient);
					continue;
				}

				String configPath = item + ".Ingredients." + abbreviation;
				String material = getConfig().getString(configPath + ".Material");
				Optional<XMaterial> rawMaterial = XMaterial.matchXMaterial(material);

				if (!validMaterial(recipe.getName(), material, rawMaterial)) {
					continue recipeLoop;
				}

				Material ingredientMaterial = rawMaterial.get().parseMaterial();
				if (count > amountRequirement) {
					logError("Error loading recipe..", recipe.getName());
					logError(
							"Found " + count + " slots but converter is " + converter + " so use only "
									+ amountRequirement + " slot(s) (X for others) for 'ItemCrafting'",
							recipe.getName());
					continue recipeLoop;
				}

				String ingredientName = getConfig().isString(configPath + ".Name")
						? getConfig().getString(configPath + ".Name")
						: null;
				String ingredientIdentifier = getConfig().isString(configPath + ".Identifier")
						? getConfig().getString(configPath + ".Identifier")
						: null;
				int ingredientAmount = getConfig().isInt(configPath + ".Amount")
						? getConfig().getInt(configPath + ".Amount")
						: 1;

				logDebug("Ingredient Name: " + ingredientName, recipe.getName());
				logDebug("Ingredient Identifier: " + ingredientIdentifier, recipe.getName());
				logDebug("Ingredient Type: " + ingredientMaterial, recipe.getName());
				logDebug("Ingredient Amount: " + ingredientAmount, recipe.getName());

				recipeIngredient = new RecipeUtil.Ingredient(abbreviation, ingredientMaterial);
				recipeIngredient.setDisplayName(ingredientName);
				recipeIngredient.setIdentifier(ingredientIdentifier);
				recipeIngredient.setAmount(ingredientAmount);
				recipeIngredient.setSlot(slot);
				recipe.addIngredient(recipeIngredient);

				if (count == amountRequirement)
					break;
			}

			logDebug("Recipe Type: " + recipe.getType(), recipe.getName());
			logDebug("Successfully added " + item + " with the amount output of " + i.getAmount(), recipe.getName());

			if (getConfig().isBoolean(item + ".Enabled"))
				recipe.setActive(getConfig().getBoolean(item + ".Enabled"));

			if (getConfig().isString(item + ".Permission"))
				recipe.setPerm(getConfig().getString(item + ".Permission"));

			recipeUtil.createRecipe(recipe);
		}

		recipeUtil.reloadRecipes();
	}

	public void addRecipesFromAPI(Recipe specificRecipe) {
		RecipeUtil recipeUtil = Main.getInstance().recipeUtil;
		HashMap<String, Recipe> recipeList = recipeUtil.getAllRecipes();

		if (specificRecipe != null) {
			recipeList.clear();
			recipeList.put(specificRecipe.getName(), specificRecipe);
		}

		for (Recipe recipe : recipeList.values()) {
			try {
				ShapedRecipe shapedRecipe = null;
				ShapelessRecipe shapelessRecipe = null;
				FurnaceRecipe furnaceRecipe = null;
				StonecuttingRecipe sCutterRecipe = null;
				BlastingRecipe blastRecipe = null;
				SmokingRecipe smokerRecipe = null;

				// Create recipes based on type
				switch (recipe.getType()) {
				case SHAPELESS:
					shapelessRecipe = createShapelessRecipe(recipe);
					break;
				case SHAPED:
					shapedRecipe = createShapedRecipe(recipe);
					break;
				case FURNACE:
					furnaceRecipe = createFurnaceRecipe(recipe);
					break;
				case BLASTFURNACE:
					blastRecipe = createBlastFurnaceRecipe(recipe);
					break;
				case SMOKER:
					smokerRecipe = createSmokerRecipe(recipe);
					break;
				case STONECUTTER:
					sCutterRecipe = createStonecuttingRecipe(recipe);
					break;
				default:
					break;
				}

				// Register recipes with the server
				if (shapedRecipe != null)
					Bukkit.getServer().addRecipe(shapedRecipe);
				if (shapelessRecipe != null)
					Bukkit.getServer().addRecipe(shapelessRecipe);
				if (furnaceRecipe != null)
					Bukkit.getServer().addRecipe(furnaceRecipe);
				if (blastRecipe != null)
					Bukkit.getServer().addRecipe(blastRecipe);
				if (sCutterRecipe != null)
					Bukkit.getServer().addRecipe(sCutterRecipe);
				if (smokerRecipe != null)
					Bukkit.getServer().addRecipe(smokerRecipe);

			} catch (Exception e) {
				Main.getInstance().getLogger().log(Level.SEVERE, "Error loading recipe: " + e.getMessage(), e);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private ShapelessRecipe createShapelessRecipe(Recipe recipe) {
		ShapelessRecipe shapelessRecipe;

		if (Main.getInstance().serverVersionAtLeast(1, 12)) {
			shapelessRecipe = new ShapelessRecipe(createNamespacedKey(recipe), recipe.getResult());
		} else {
			shapelessRecipe = new ShapelessRecipe(recipe.getResult());
		}

		for (RecipeUtil.Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient.isEmpty())
				continue;

			// Uses exactChoice if version is 1.14 or higher
			if (recipe.isExactChoice() && !recipe.getIgnoreData() && !recipe.getIgnoreModelData()) {
				shapelessRecipe.addIngredient(findExactChoice(recipe, ingredient));

			} else {
				shapelessRecipe.addIngredient(ingredient.getMaterial());
			}
		}

		if (recipe.isExactChoice())
			logDebug("Created " + recipe.getType() + " recipe using the ExactChoice method.", recipe.getName());

		if (Main.getInstance().serverVersionAtLeast(1, 14))
			shapelessRecipe.setCategory(CraftingBookCategory.valueOf(recipe.getBookCategory()));

		return shapelessRecipe;
	}

	@SuppressWarnings("deprecation")
	private ShapedRecipe createShapedRecipe(Recipe recipe) {
		ShapedRecipe shapedRecipe;

		ArrayList<String> ingredients = new ArrayList<String>();

		if (Main.getInstance().serverVersionAtLeast(1, 12)) {
			shapedRecipe = new ShapedRecipe(createNamespacedKey(recipe), recipe.getResult());
		} else {
			shapedRecipe = new ShapedRecipe(recipe.getResult());
		}

		shapedRecipe.shape(recipe.getRow(1), recipe.getRow(2), recipe.getRow(3));
		for (RecipeUtil.Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient.getMaterial() == Material.AIR || ingredients.contains(ingredient.getAbbreviation()))
				continue;

			ingredients.add(ingredient.getAbbreviation());

			// Uses exactChoice if version is 1.14 or higher
			// Ignores if IgnoreData or IgnoreModelData is true
			if (recipe.isExactChoice() && !recipe.getIgnoreData() && !recipe.getIgnoreModelData()) {
				shapedRecipe.setIngredient(ingredient.getAbbreviation().charAt(0), findExactChoice(recipe, ingredient));

			} else {
				shapedRecipe.setIngredient(ingredient.getAbbreviation().charAt(0), ingredient.getMaterial());
			}
		}

		if (recipe.isExactChoice())
			logDebug("Created " + recipe.getType() + " recipe using the ExactChoice method.", recipe.getName());

		if (Main.getInstance().serverVersionAtLeast(1, 14))
			shapedRecipe.setCategory(CraftingBookCategory.valueOf(recipe.getBookCategory()));

		return shapedRecipe;
	}

	@SuppressWarnings("deprecation")
	private FurnaceRecipe createFurnaceRecipe(Recipe recipe) {

		FurnaceRecipe furnaceRecipe;

		if (Main.getInstance().serverVersionAtLeast(1, 14)) {
			furnaceRecipe = new FurnaceRecipe(createNamespacedKey(recipe), recipe.getResult(),
					findExactChoice(recipe, null), recipe.getExperience(), recipe.getCookTime());
		} else if (Main.getInstance().serverVersionAtLeast(1, 12)) {
			furnaceRecipe = new FurnaceRecipe(createNamespacedKey(recipe), recipe.getResult(),
					recipe.getSlot(1).getMaterial(), recipe.getExperience(), recipe.getCookTime());
		} else {
			furnaceRecipe = new FurnaceRecipe(recipe.getResult(), recipe.getSlot(1).getMaterial());
		}

		if (Main.getInstance().serverVersionAtLeast(1, 14))
			furnaceRecipe.setCategory(CookingBookCategory.valueOf(recipe.getBookCategory()));

		return furnaceRecipe;
	}

	private BlastingRecipe createBlastFurnaceRecipe(Recipe recipe) {

		if (!Main.getInstance().serverVersionAtLeast(1, 14)) {
			logError("Error loading recipe. Your server version does not support BlastFurnace recipes!",
					recipe.getName());
			return null;
		}

		BlastingRecipe blastRecipe = new BlastingRecipe(createNamespacedKey(recipe), recipe.getResult(),
				findExactChoice(recipe, null), recipe.getExperience(), recipe.getCookTime());

		if (Main.getInstance().serverVersionAtLeast(1, 14))
			blastRecipe.setCategory(CookingBookCategory.valueOf(recipe.getBookCategory()));

		return blastRecipe;
	}

	private SmokingRecipe createSmokerRecipe(Recipe recipe) {
		if (!Main.getInstance().serverVersionAtLeast(1, 14)) {
			logError("Error loading recipe. Your server version does not support Smoker recipes!", recipe.getName());
			return null;
		}

		SmokingRecipe smokingRecipe = new SmokingRecipe(createNamespacedKey(recipe), recipe.getResult(),
				findExactChoice(recipe, null), recipe.getExperience(), recipe.getCookTime());

		if (Main.getInstance().serverVersionAtLeast(1, 14))
			smokingRecipe.setCategory(CookingBookCategory.valueOf(recipe.getBookCategory()));

		return smokingRecipe;
	}

	private StonecuttingRecipe createStonecuttingRecipe(Recipe recipe) {
		if (!Main.getInstance().serverVersionAtLeast(1, 14)) {
			logError("Error loading recipe. Your server version does not support Stonecutting recipes!",
					recipe.getName());
			return null;
		}

		return new StonecuttingRecipe(new NamespacedKey(Main.getInstance(), recipe.getKey()), recipe.getResult(),
				findExactChoice(recipe, null));
	}

	private NamespacedKey createNamespacedKey(Recipe recipe) {
		return new NamespacedKey(Main.getInstance(), recipe.getKey());
	}

	private boolean validMaterial(String recipe, String materialInput, Optional<XMaterial> type) {
		if (type == null || type.isEmpty() || !type.isPresent()) {
			logError("Error loading recipe..", recipe);
			logError("We are having trouble matching the material " + materialInput.toUpperCase()
					+ " to a minecraft item. Please double check you have inputted the correct material enum into the 'Item'"
					+ " section and try again. If this problem persists please contact Mehboss on Spigot!", recipe);
			return false;
		}
		return true;
	}

	private ExactChoice findExactChoice(Recipe recipe, Ingredient ingredient) {
		Ingredient item = ingredient;

		// if item is null, the converter will never be shaped or shapeless.
		if (item == null) {
			for (Ingredient ingredients : recipe.getIngredients()) {
				if (ingredients.isEmpty())
					continue;
				item = recipe.getSlot(ingredients.getSlot());
			}
		}

		ItemStack itemsAdder = handleItemAdderCheck(null, recipe.getName(), item.getIdentifier(), true);
		ItemStack mythicItem = handleMythicItemCheck(null, recipe.getName(), item.getIdentifier(), true);

		if (item.hasIdentifier() && recipeUtil.getRecipeFromKey(item.getIdentifier()) != null) {
			return new ExactChoice(recipeUtil.getRecipeFromKey(item.getIdentifier()).getResult());

		} else if (item.hasIdentifier() && itemsAdder != null) {
			return new ExactChoice(itemsAdder);

		} else if (item.hasIdentifier() && mythicItem != null) {
			return new ExactChoice(mythicItem);

		} else {
			ItemStack exactItem = new ItemStack(item.getMaterial());
			ItemMeta exactMeta = exactItem.getItemMeta();

			if (item.hasDisplayName())
				exactMeta.setDisplayName(item.getDisplayName());
			exactItem.setItemMeta(exactMeta);
			return new ExactChoice(exactItem);
		}
	}

	boolean isInt(String s) {
		try {
			Integer.parseInt(s);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

	private void logError(String st, String recipe) {
		Logger.getLogger("Minecraft").log(Level.WARNING,
				"[DEBUG][" + Main.getInstance().getName() + "][" + recipe + "] " + st);
	}

	private void logDebug(String st, String recipe) {
		if (Main.getInstance().debug)
			Logger.getLogger("Minecraft").log(Level.WARNING,
					"[DEBUG][" + Main.getInstance().getName() + "][" + recipe + "] " + st);
	}

	private FileConfiguration getConfig() {
		return recipeConfig;
	}
}
