package me.mehboss.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import com.cryptomorin.xseries.XMaterial;

import io.github.bananapuncher714.nbteditor.NBTEditor;
import me.clip.placeholderapi.PlaceholderAPI;

public class EditGUI implements Listener {

	// *** this menu is the GUI for adding a new recipe
	HashMap<UUID, Inventory> inventoryinstance = new HashMap<UUID, Inventory>();
	HashMap<UUID, String> editmeta = new HashMap<UUID, String>();
	HashMap<UUID, String> getr = new HashMap<UUID, String>();
	HashMap<UUID, String> getnewid = new HashMap<UUID, String>();

	List<String> lore = new ArrayList<>();

	private static EditGUI instance;

	Plugin config = Bukkit.getPluginManager().getPlugin("CustomRecipes");

	public EditGUI(Plugin p, String item) {
		Bukkit.getServer().getPluginManager().registerEvents(this, p);
		instance = this;
	}

	public static EditGUI getInstance() {
		return instance;
	}

	FileConfiguration getConfig(String recipeName) {
		File dataFolder = Main.getInstance().getDataFolder();
		File recipesFolder = new File(dataFolder, "recipes");
		File recipeFile = new File(recipesFolder, recipeName + ".yml");

		return YamlConfiguration.loadConfiguration(recipeFile);
	}

	public void setItems(Boolean viewing, Inventory i, String invname, ItemStack item, OfflinePlayer p) {

		String configname = invname;

		List<String> loreList = new ArrayList<String>();

		int[] paneslot = { 0, 1, 2, 3, 4, 5, 6, 9, 13, 14, 15, 18, 22, 24, 27, 31, 32, 33, 34, 36, 37, 38, 39, 41, 42,
				43, 45, 46, 47, 51, 52 };
		ItemStack stained = XMaterial.BLACK_STAINED_GLASS_PANE.parseItem();
		ItemMeta stainedm = stained.getItemMeta();
		stainedm.setDisplayName(" ");
		stained.setItemMeta(stainedm);

		for (int slot : paneslot) {
			i.setItem(slot, stained);
		}

		int slot = 1;
		for (String materials : getMaterials(configname)) {

			if (materials == null || !XMaterial.matchXMaterial(materials).isPresent()) {
				slot++;
				continue;
			}

			boolean foundIdentifier = itemIngredients(configname, slot, p) != null ? true : false;
			ItemStack mat = foundIdentifier ? itemIngredients(configname, slot, p)
					: XMaterial.matchXMaterial(materials).get().parseItem();

			mat.setAmount(getAmounts(configname, slot));

			if (!foundIdentifier) {
				ItemMeta matm = mat.getItemMeta();

				if (getNames(configname, slot) != null)
					matm.setDisplayName(getNames(configname, slot));

				mat.setItemMeta(matm);
			}

			if (slot == 1)
				i.setItem(10, mat);
			if (slot == 2)
				i.setItem(11, mat);
			if (slot == 3)
				i.setItem(12, mat);
			if (slot == 4)
				i.setItem(19, mat);
			if (slot == 5)
				i.setItem(20, mat);
			if (slot == 6)
				i.setItem(21, mat);
			if (slot == 7)
				i.setItem(28, mat);
			if (slot == 8)
				i.setItem(29, mat);
			if (slot == 9)
				i.setItem(30, mat);

			slot++;

		}

		ItemStack result = NBTEditor.contains(item, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER")
				? Main.getInstance().identifier
						.get(NBTEditor.getString(item, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER"))
				: Main.getInstance().giveRecipe.get(configname.toLowerCase());

		ItemMeta resultM = result.getItemMeta();

		if (result.hasItemMeta() && resultM.hasLore() && hasPlaceholder()) {
			resultM.setLore(PlaceholderAPI.setPlaceholders(p, resultM.getLore()));
			result.setItemMeta(resultM);
		}

		i.setItem(23, result);

		if (viewing) {
			int[] emptySlots = { 7, 8, 16, 17, 25, 26, 35, 40, 44, 53 };

			ItemStack back = XMaterial.RED_STAINED_GLASS_PANE.parseItem();
			ItemMeta backm = back.getItemMeta();
			backm.setDisplayName(ChatColor.DARK_RED + "Back");
			back.setItemMeta(backm);

			i.setItem(48, back);
			i.setItem(49, back);
			i.setItem(50, back);

			for (int slots : emptySlots)
				i.setItem(slots, stained);

			return;
		}

		ItemStack identifier = XMaterial.PAPER.parseItem();
		ItemMeta identifierm = identifier.getItemMeta();
		identifierm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fIdentifier:&7 "));
		loreList.add(ChatColor.GRAY + getConfig(configname).getString(configname + ".Identifier"));
		identifierm.setLore(loreList);
		identifier.setItemMeta(identifierm);
		loreList.clear();

		ItemStack lore = XMaterial.BOOK.parseItem();
		ItemMeta lorem = lore.getItemMeta();
		lorem.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fLore"));

		if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
			lorem.setLore(item.getItemMeta().getLore());

			if (hasPlaceholder())
				lorem.setLore(PlaceholderAPI.setPlaceholders(p, item.getItemMeta().getLore()));
		}

		lore.setItemMeta(lorem);

		ItemStack effects = XMaterial.GHAST_TEAR.parseItem();
		ItemMeta effectsm = effects.getItemMeta();
		effectsm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fEffects"));
		if (getConfig(configname).getString(configname + ".Effects") != null) {
			for (String effectslore : getConfig(configname).getStringList(configname + ".Effects")) {
				loreList.add(ChatColor.GRAY + effectslore);
			}
			effectsm.setLore(loreList);
		}
		effects.setItemMeta(effectsm);
		loreList.clear();

		ItemStack enchants = XMaterial.EXPERIENCE_BOTTLE.parseItem();
		ItemMeta enchantsm = effects.getItemMeta();
		enchantsm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fEnchantments"));
		if (getConfig(configname).getStringList(configname + ".Enchantments") != null) {
			for (String enchantslore : getConfig(configname).getStringList(configname + ".Enchantments")) {
				loreList.add(ChatColor.GRAY + enchantslore);
			}
			enchantsm.setLore(loreList);
		}
		enchants.setItemMeta(enchantsm);
		loreList.clear();

		ItemStack permission = XMaterial.ENDER_PEARL.parseItem();
		ItemMeta permissionm = permission.getItemMeta();
		permissionm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fPermission: &7"));

		if (getConfig(configname).isSet(configname + ".Permission")
				&& !getConfig(configname).getString(configname + ".Permission").equals("none"))
			loreList.add(ChatColor.GRAY + getConfig(configname).getString(configname + ".Permission"));
		permissionm.setLore(loreList);
		permission.setItemMeta(permissionm);
		loreList.clear();

		ItemStack hideenchants = XMaterial.ENCHANTING_TABLE.parseItem();
		ItemMeta hideenchantsm = hideenchants.getItemMeta();
		Boolean hc = getConfig(configname).getBoolean(configname + ".Hide-Enchants");
		String ht = "&cfalse";

		if (hc == true)
			ht = "&atrue";

		hideenchantsm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fHide Enchants: &f" + ht));
		hideenchants.setItemMeta(hideenchantsm);

		ItemStack shapeless = XMaterial.CRAFTING_TABLE.parseItem();
		ItemMeta shapelessm = shapeless.getItemMeta();
		Boolean sc = getConfig(configname).getBoolean(configname + ".Shapeless");
		String toggle = "&cfalse";

		if (sc == true)
			toggle = "&atrue";

		shapelessm.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&fShapeless: &f" + toggle));
		shapeless.setItemMeta(shapelessm);

		ItemStack name = XMaterial.WRITABLE_BOOK.parseItem();
		ItemMeta namem = name.getItemMeta();
		namem.setDisplayName(ChatColor.WHITE + "Recipe Name: ");

		if (getConfig(configname).isSet(configname + ".Name")
				&& !getConfig(configname).getString(configname + ".Name").equals("none"))
			loreList.add(
					ChatColor.translateAlternateColorCodes('&', getConfig(configname).getString(configname + ".Name")));
		namem.setLore(loreList);
		name.setItemMeta(namem);
		loreList.clear();

		ItemStack amount = XMaterial.FEATHER.parseItem();
		ItemMeta amountm = amount.getItemMeta();
		amountm.setDisplayName(ChatColor.translateAlternateColorCodes('&',
				"&fAmount: &7" + getConfig(configname).getString(configname + ".Amount")));
		amount.setItemMeta(amountm);

		i.setItem(7, identifier);
		i.setItem(8, hideenchants);
		i.setItem(16, effects);
		i.setItem(17, enchants);
		i.setItem(25, permission);
		i.setItem(26, name);
		i.setItem(35, amount);
		i.setItem(44, lore);
		i.setItem(53, shapeless);

		ItemStack enabled = XMaterial.SLIME_BALL.parseItem();
		ItemMeta enabledm = enabled.getItemMeta();
		enabledm.setDisplayName(ChatColor.GREEN + "Enabled");
		enabled.setItemMeta(enabledm);
		if (getConfig(configname).getBoolean(configname + ".Enabled") == false) {
			enabled = XMaterial.SNOWBALL.parseItem();
			enabledm.setDisplayName(ChatColor.RED + "Disabled");
			enabled.setItemMeta(enabledm);
		}
		i.setItem(40, enabled);

		ItemStack cancel = XMaterial.RED_STAINED_GLASS_PANE.parseItem();
		ItemMeta cancelm = cancel.getItemMeta();
		cancelm.setDisplayName(ChatColor.DARK_RED + "Cancel");
		cancel.setItemMeta(cancelm);
		i.setItem(48, cancel);

		ItemStack mainmenu = XMaterial.WHITE_STAINED_GLASS_PANE.parseItem();
		ItemMeta mainmenum = mainmenu.getItemMeta();
		mainmenum.setDisplayName("Main Menu");
		mainmenu.setItemMeta(mainmenum);
		i.setItem(49, mainmenu);

		ItemStack update = XMaterial.GREEN_STAINED_GLASS_PANE.parseItem();
		ItemMeta updatem = update.getItemMeta();
		updatem.setDisplayName(ChatColor.DARK_GREEN + "Update");
		update.setItemMeta(updatem);
		i.setItem(50, update);

		ItemStack delete = XMaterial.BARRIER.parseItem();
		ItemMeta deletem = delete.getItemMeta();
		deletem.setDisplayName(ChatColor.DARK_RED + "Delete Recipe");
		delete.setItemMeta(deletem);
		i.setItem(45, delete);

	}

	public ArrayList<String> getMaterials(String recipename) {

		ArrayList<String> materials = new ArrayList<String>();
		HashMap<String, String> letter = new HashMap<String, String>();

		letter.put("X", "false");

		ConfigurationSection ingredientsSection = getConfig(recipename)
				.getConfigurationSection(recipename + ".Ingredients");
		if (ingredientsSection != null) {
			for (String ingredientKey : ingredientsSection.getKeys(false)) {
				ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(ingredientKey);
				if (ingredient != null) {
					String identifier = ingredient.getString("Material");
					letter.put(ingredientKey, identifier);
				}
			}
		}

		List<String> r = getConfig(recipename).getStringList(recipename + ".ItemCrafting");
		String row1 = r.get(0);
		String row2 = r.get(1);
		String row3 = r.get(2);

		String[] newsplit1 = row1.split("");
		String[] newsplit2 = row2.split("");
		String[] newsplit3 = row3.split("");

		try {
			materials.add(letter.get(newsplit1[0]));
			materials.add(letter.get(newsplit1[1]));
			materials.add(letter.get(newsplit1[2]));

			materials.add(letter.get(newsplit2[0]));
			materials.add(letter.get(newsplit2[1]));
			materials.add(letter.get(newsplit2[2]));

			materials.add(letter.get(newsplit3[0]));
			materials.add(letter.get(newsplit3[1]));
			materials.add(letter.get(newsplit3[2]));

		} catch (NullPointerException e) {
			Main.getInstance().getLogger().log(Level.SEVERE,
					"ERROR RETRIEVING ITEM MATERIALS FOR " + recipename.toUpperCase()
							+ ". PLEASE DOUBLE CHECK THAT YOUR MATERIALS HAVE BEEN INPUTTED INTO CONFIG CORRECTLY.");
			Main.getInstance().getLogger().log(Level.SEVERE,
					"IF THIS PROBLEM PERSISTS PLEASE CONTACT MEHBOSS ON SPIGOT FOR ASSISTANCE.");
		}
		return materials;
	}

	public int getAmounts(String recipename, int slot) {
		HashMap<String, Integer> letter = new HashMap<String, Integer>();
		letter.put("X", 0);

		ConfigurationSection ingredientsSection = getConfig(recipename)
				.getConfigurationSection(recipename + ".Ingredients");
		if (ingredientsSection != null) {
			for (String ingredientKey : ingredientsSection.getKeys(false)) {
				ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(ingredientKey);
				if (ingredient != null) {
					int identifier = ingredient.isSet("Amount") ? ingredient.getInt("Amount") : 1;
					letter.put(ingredientKey, identifier);
				}
			}
		}

		List<String> craftingRows = getConfig(recipename).getStringList(recipename + ".ItemCrafting");
		if (craftingRows.size() >= 3) {
			String row1 = craftingRows.get(0);
			String row2 = craftingRows.get(1);
			String row3 = craftingRows.get(2);

			String[] row1Split = row1.split("");
			String[] row2Split = row2.split("");
			String[] row3Split = row3.split("");

			if (slot == 1)
				return letter.get(row1Split[0]);
			if (slot == 2)
				return letter.get(row1Split[1]);
			if (slot == 3)
				return letter.get(row1Split[2]);
			if (slot == 4)
				return letter.get(row2Split[0]);
			if (slot == 5)
				return letter.get(row2Split[1]);
			if (slot == 6)
				return letter.get(row2Split[2]);
			if (slot == 7)
				return letter.get(row3Split[0]);
			if (slot == 8)
				return letter.get(row3Split[1]);
			if (slot == 9)
				return letter.get(row3Split[2]);
		}

		return 0;
	}

	String chatColor(String st) {
		if (st == null)
			return null;

		if (st.equalsIgnoreCase("false"))
			return st;

		return ChatColor.translateAlternateColorCodes('&', st);
	}

	ItemStack itemIngredients(String recipename, int slot, OfflinePlayer p) {

		ArrayList<String> ingLetters = new ArrayList<String>();

		List<String> craftingRows = getConfig(recipename).getStringList(recipename + ".ItemCrafting");
		if (craftingRows.size() >= 3) {
			String row1 = craftingRows.get(0);
			String row2 = craftingRows.get(1);
			String row3 = craftingRows.get(2);

			String[] row1Split = row1.split("");
			String[] row2Split = row2.split("");
			String[] row3Split = row3.split("");

			ingLetters.add(row1Split[0]);
			ingLetters.add(row1Split[1]);
			ingLetters.add(row1Split[2]);

			ingLetters.add(row2Split[0]);
			ingLetters.add(row2Split[1]);
			ingLetters.add(row2Split[2]);

			ingLetters.add(row3Split[0]);
			ingLetters.add(row3Split[1]);
			ingLetters.add(row3Split[2]);
		}

		String letter = ingLetters.get(slot - 1);
		ConfigurationSection ingredientsSection = getConfig(recipename)
				.getConfigurationSection(recipename + ".Ingredients." + letter);

		if (ingredientsSection == null || letter == null || letter.equalsIgnoreCase("X"))
			return null;

		if (ingredientsSection.isSet("Identifier")
				&& !ingredientsSection.getString("Identifier").equalsIgnoreCase("none")
				&& identifier().containsKey(ingredientsSection.getString("Identifier"))) {

			ItemStack item = identifier().get(ingredientsSection.getString("Identifier"));
			ItemMeta itemM = item.getItemMeta();

			if (item.hasItemMeta() && itemM.hasLore() && hasPlaceholder()) {
				itemM.setLore(PlaceholderAPI.setPlaceholders(p, itemM.getLore()));
				item.setItemMeta(itemM);
			}

			return item;
		}
		return null;
	}

	HashMap<String, ItemStack> identifier() {
		return Main.getInstance().identifier;
	}

	public String getNames(String recipename, int slot) {
		HashMap<String, String> letter = new HashMap<String, String>();
		letter.put("X", "false");

		ConfigurationSection ingredientsSection = getConfig(recipename)
				.getConfigurationSection(recipename + ".Ingredients");
		if (ingredientsSection != null) {
			for (String ingredientKey : ingredientsSection.getKeys(false)) {
				ConfigurationSection ingredient = ingredientsSection.getConfigurationSection(ingredientKey);
				if (ingredient != null) {
					String identifier = ingredient.isSet("Name") ? ingredient.getString("Name") : null;

					if (identifier != null && ingredient.getString("Name").equalsIgnoreCase("none"))
						identifier = null;

					letter.put(ingredientKey, identifier);
				}
			}
		}

		List<String> craftingRows = getConfig(recipename).getStringList(recipename + ".ItemCrafting");
		if (craftingRows.size() >= 3) {
			String row1 = craftingRows.get(0);
			String row2 = craftingRows.get(1);
			String row3 = craftingRows.get(2);

			String[] row1Split = row1.split("");
			String[] row2Split = row2.split("");
			String[] row3Split = row3.split("");

			if (slot == 1)
				return chatColor(letter.get(row1Split[0]));
			if (slot == 2)
				return chatColor(letter.get(row1Split[1]));
			if (slot == 3)
				return chatColor(letter.get(row1Split[2]));
			if (slot == 4)
				return chatColor(letter.get(row2Split[0]));
			if (slot == 5)
				return chatColor(letter.get(row2Split[1]));
			if (slot == 6)
				return chatColor(letter.get(row2Split[2]));
			if (slot == 7)
				return chatColor(letter.get(row3Split[0]));
			if (slot == 8)
				return chatColor(letter.get(row3Split[1]));
			if (slot == 9)
				return chatColor(letter.get(row3Split[2]));
		}

		return null;
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {

		if (!(e.getWhoClicked() instanceof Player) || e.getClickedInventory() == null
				|| e.getClickedInventory().getType() == InventoryType.PLAYER)
			return;

		Player p = (Player) e.getWhoClicked();
		InventoryView view = e.getView();
		String inventoryTitle = view.getTitle();

		if (e.getInventory() != null && inventoryTitle != null && inventoryTitle.contains("VIEWING: ")) {
			e.setCancelled(true);

			if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR || e.getRawSlot() == 23)
				return;

			if (NBTEditor.contains(e.getCurrentItem(), NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER")
					&& Main.getInstance().identifier.containsKey(
							NBTEditor.getString(e.getCurrentItem(), NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER"))) {

				String name = Main.getInstance().configName.containsKey(e.getCurrentItem())
						? Main.getInstance().configName.get(e.getCurrentItem())
						: null;

				Inventory edit = Bukkit.getServer().createInventory(null, 54,
						ChatColor.translateAlternateColorCodes('&', "&cVIEWING: " + name));

				setItems(true, edit, name, e.getCurrentItem(), p);

				Main.getInstance().saveInventory.put(p.getUniqueId(), e.getInventory());
				p.openInventory(edit);
			}

			if (e.getRawSlot() == 48 || e.getRawSlot() == 49 || e.getRawSlot() == 50) {

				if (Main.getInstance().saveInventory.containsKey(p.getUniqueId())) {
					p.openInventory(Main.getInstance().saveInventory.get(p.getUniqueId()));
					Main.getInstance().saveInventory.remove(p.getUniqueId());
				} else {
					Main.recipes.show(p);
				}
			}

			return;
		}

		if (e.getInventory() != null && inventoryTitle != null && inventoryTitle.contains("EDITING: ")) {

			String[] split = inventoryTitle.split("EDITING: ");
			String recipe = split[1];

			if (e.getRawSlot() != 10 && e.getRawSlot() != 11 && e.getRawSlot() != 12 && e.getRawSlot() != 19
					&& e.getRawSlot() != 20 && e.getRawSlot() != 21 && e.getRawSlot() != 28 && e.getRawSlot() != 29
					&& e.getRawSlot() != 30 && e.getRawSlot() != 23) {

				e.setCancelled(true);
			}

			if (e.getRawSlot() == 16)
				return;
			// effects button

			if (e.getRawSlot() == 17)
				return;
			// enchants button

			if (e.getRawSlot() == 35) {
				// amount button
				handleStringMessage(p, "Amount", e);
			}

			ItemStack toggle = e.getCurrentItem();

			if (toggle == null || toggle.getItemMeta() == null)
				return;

			ItemMeta togglem = toggle.getItemMeta();

			if (e.getRawSlot() == 8) {
				// hide enchants?

				if (togglem.getDisplayName().contains("true")) {
					togglem.setDisplayName("Hide Enchants: " + ChatColor.RED + "false");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(8, toggle);
					return;
				}

				if (togglem.getDisplayName().contains("false")) {
					togglem.setDisplayName("Hide Enchants: " + ChatColor.GREEN + "true");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(8, toggle);
					return;
				}
			}

			if (e.getRawSlot() == 40) {

				// recipe toggle enable/disable

				if (togglem.getDisplayName().contains("Enabled")) {
					togglem.setDisplayName(ChatColor.RED + "Disabled");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(40, toggle);
					e.getCurrentItem().setType(XMaterial.SNOWBALL.parseMaterial());
					return;
				}

				if (togglem.getDisplayName().contains("Disabled")) {
					togglem.setDisplayName(ChatColor.GREEN + "Enabled");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(40, toggle);
					e.getCurrentItem().setType(XMaterial.SLIME_BALL.parseMaterial());
					return;
				}
			}

			if (e.getRawSlot() == 44) {
				// item lore
				ItemStack item = e.getInventory().getItem(44);

				if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
					lore = item.getItemMeta().getLore();
				}

				inventoryinstance.put(p.getUniqueId(), p.getOpenInventory().getTopInventory());
				editmeta.put(p.getUniqueId(), "Lore");

				p.closeInventory();
				sendLoremsg(p);
			}

			if (e.getRawSlot() == 48) {
				// cancel button
				p.sendMessage(
						ChatColor.RED + "You have successfully cancelled any changes to the recipe '" + recipe + "'");
				p.closeInventory();
			}

			if (e.getRawSlot() == 49) {
				// main menu button
				p.openInventory(ManageGUI.inv);
			}

			if (e.getRawSlot() == 50) {
				// update button
				saveChanges(e.getClickedInventory(), p, recipe);
			}

			if (e.getRawSlot() == 53) {
				// shapeless?

				if (togglem.getDisplayName().contains("true")) {
					togglem.setDisplayName("Shapeless: " + ChatColor.RED + "false");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(53, toggle);
					return;
				}

				if (togglem.getDisplayName().contains("false")) {
					togglem.setDisplayName("Shapeless: " + ChatColor.GREEN + "true");
					toggle.setItemMeta(togglem);

					p.getOpenInventory().getTopInventory().setItem(53, toggle);
					return;
				}
			}

			if (e.getRawSlot() == 7) {
				// change identifier
				handleStringMessage(p, "Identifier", e);
			}

			if (e.getRawSlot() == 25) {
				// change permission
				handleStringMessage(p, "Permission", e);
			}

			if (e.getRawSlot() == 26) {
				// change recipe name
				handleStringMessage(p, "Name", e);
			}

			if (e.getRawSlot() == 45) {

				if (ChatColor.stripColor(e.getClickedInventory().getItem(45).getItemMeta().getDisplayName())
						.equals("Confirm Recipe Deletion")) {

					Main.getInstance().getConfig().set("Items." + recipe, null);
					Main.getInstance().saveConfig();
					Main.getInstance().reload();

					p.openInventory(ManageGUI.inv);
					return;
				}

				ItemStack confirm = e.getClickedInventory().getItem(45);
				ItemMeta confirmm = e.getClickedInventory().getItem(45).getItemMeta();
				confirmm.setDisplayName(ChatColor.DARK_RED + "Confirm Recipe Deletion");
				confirm.setItemMeta(confirmm);
			}
		}
	}

	void sendLoremsg(Player p) {

		int num = 1;

		p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
		p.sendMessage("Please type your new recipe lore");
		p.sendMessage("Current Lore: ");
		p.sendMessage(" ");

		if (lore != null) {
			for (String line : lore) {
				p.sendMessage(num + ": " + line);
				num++;
			}
		} else {
			p.sendMessage("NO LORE");
		}

		p.sendMessage(" ");
		p.sendMessage("Type 'LINE#: string' to edit/add a line to your lore.");
		p.sendMessage("Type 'LINE#: remove' to remove a line.");
		p.sendMessage("Type ONLY the line number for an empty space.");
		p.sendMessage("Type " + ChatColor.RED + "CANCEL " + ChatColor.WHITE + "to go back.");
		p.sendMessage("Type " + ChatColor.GREEN + "DONE " + ChatColor.WHITE + "when finished.");
		p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
	}

	boolean hasPlaceholder() {
		return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
	}

	public void handleLore(int slot, String string, Player p, AsyncPlayerChatEvent e) {

		Inventory inv = inventoryinstance.get(p.getUniqueId());
		ItemStack item = inv.getItem(44);
		ItemStack result = inv.getItem(23);

		if (!editmeta.containsKey(p.getUniqueId()))
			return;

		if (e.getMessage().equalsIgnoreCase("done")) {

			if (!lore.isEmpty() && item.hasItemMeta()) {
				ItemMeta itemm = item.getItemMeta();
				itemm.setLore(lore);

				if (hasPlaceholder())
					itemm.setLore(PlaceholderAPI.setPlaceholders(p, lore));

				item.setItemMeta(itemm);

				ItemMeta resultm = result.getItemMeta();
				resultm.setLore(lore);

				if (hasPlaceholder())
					resultm.setLore(PlaceholderAPI.setPlaceholders(p, lore));

				result.setItemMeta(resultm);
			}

			lore.clear();

			Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CustomRecipes"), () -> {
				p.openInventory(inv);
			});

			editmeta.remove(p.getUniqueId());
			inventoryinstance.remove(p.getUniqueId());
			return;
		}

		if (e.getMessage().equalsIgnoreCase("cancel")) {
			lore.clear();

			Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CustomRecipes"), () -> {
				p.openInventory(inv);
			});

			editmeta.remove(p.getUniqueId());
			inventoryinstance.remove(p.getUniqueId());
			return;
		}

		String[] spl = e.getMessage().split(": ");

		if (spl == null || !isInt(spl[0]) || Integer.parseInt(spl[0]) <= 0) {
			p.sendMessage(ChatColor.RED + "Wrong format! You must first define the line number!");
			return;
		}

		if (lore.isEmpty() || lore.size() < Integer.parseInt(spl[0])) {

			if (spl.length < 2) {
				lore.add(" ");
				sendLoremsg(p);
				return;
			}

			lore.add(ChatColor.translateAlternateColorCodes('&', spl[1]));
			sendLoremsg(p);
			return;
		}

		if (spl.length < 2) {
			lore.set(Integer.parseInt(spl[0]) - 1, " ");
			sendLoremsg(p);

		} else if (spl[1].equals("remove")) {
			lore.remove(Integer.parseInt(spl[0]) - 1);
			sendLoremsg(p);

		} else {
			lore.set(Integer.parseInt(spl[0]) - 1, ChatColor.translateAlternateColorCodes('&', spl[1]));
			sendLoremsg(p);
		}
	}

	public void saveChanges(Inventory inv, Player p, String recipe) {
		// set all changes to config
		// reload plugin

		if (!hasSomething(inv)) {
			p.sendMessage(ChatColor.RED
					+ "Could not update recipe. The recipe ingredients were empty or it was missing an item in the result slot");
			return;
		}

		p.sendMessage(ChatColor.GREEN + "You have successfully updated changes to the recipe '" + recipe + "'");
		p.closeInventory();

	}

	@EventHandler
	public void onClose(PlayerQuitEvent e) {
		if (Main.getInstance().recipeBook.contains(e.getPlayer().getUniqueId()))
			Main.getInstance().recipeBook.remove(e.getPlayer().getUniqueId());

		if (Main.getInstance().saveInventory.containsKey(e.getPlayer().getUniqueId()))
			Main.getInstance().saveInventory.remove(e.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent e) {

		Player p = e.getPlayer();

		if (!editmeta.containsKey(p.getUniqueId()) || editmeta.get(p.getUniqueId()) == null)
			return;

		e.setCancelled(true);

		if (editmeta.get(p.getUniqueId()).equals("Lore")) {
			handleLore(44, "Lore", p, e);
			return;
		}

		if (editmeta.get(p.getUniqueId()).equals("identifier")) {
			handleString(7, "Identifier", p, e);
			return;
		}

		if (editmeta.get(p.getUniqueId()).equals("permission")) {
			handleString(25, "Permission", p, e);
			return;
		}

		if (editmeta.get(p.getUniqueId()).equals("name")) {
			handleString(26, "Name", p, e);
			return;
		}

		if (editmeta.get(p.getUniqueId()).equals("amount")) {
			handleString(35, "Amount", p, e);
			return;
		}
	}

	public void handleStringMessage(Player p, String s, InventoryClickEvent e) {
		inventoryinstance.put(p.getUniqueId(), p.getOpenInventory().getTopInventory());
		editmeta.put(p.getUniqueId(), s.toLowerCase());
		p.closeInventory();

		List<String> getid = null;
		String identifier = null;
		if (e.getCurrentItem().getItemMeta().hasLore()) {
			getid = e.getCurrentItem().getItemMeta().getLore();
			identifier = getid.get(0);
		} else {
			String[] split = e.getCurrentItem().getItemMeta().getDisplayName().split(": ");
			String amount = split[1];
			identifier = amount;
		}

		if (identifier == null)
			Main.getInstance().getLogger().log(Level.SEVERE,
					"Something went wrong while using the GUI! This was unexpected.. Please PM Mehboss on Spigot for further information regarding this issue. STRING: "
							+ s);

		String[] split = e.getView().getTitle().split("EDITING: ");
		String recipe = split[1];

		getr.put(p.getUniqueId(), recipe);
		p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
		p.sendMessage("Please type your new recipe " + s.toLowerCase());
		p.sendMessage("Current " + s + ": " + ChatColor.translateAlternateColorCodes('&', identifier));
		p.sendMessage("Type " + ChatColor.RED + "CANCEL " + ChatColor.WHITE + "to go back.");
		p.sendMessage("Type " + ChatColor.GREEN + "DONE " + ChatColor.WHITE + "when finished.");
		p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");

	}

	public boolean isInt(String text) {
		try {
			Integer.parseInt(text);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void handleString(int slot, String string, Player p, AsyncPlayerChatEvent e) {

		Inventory inv = inventoryinstance.get(p.getUniqueId());
		ItemStack id = inv.getItem(slot);
		ItemMeta idm = id.getItemMeta();

		if (!e.getMessage().equalsIgnoreCase("done") && !e.getMessage().equalsIgnoreCase("cancel")) {
			p.sendMessage(" ");
			p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");
			p.sendMessage(ChatColor.GRAY + ChatColor.translateAlternateColorCodes('&', e.getMessage()) + ChatColor.WHITE
					+ " has been selected as the new " + string.toLowerCase() + ".");
			p.sendMessage("Type " + ChatColor.GREEN + "DONE " + ChatColor.WHITE + "to confirm your selection!");
			p.sendMessage("Type " + ChatColor.RED + "CANCEL " + ChatColor.WHITE + "to cancel your selection!");
			p.sendMessage(ChatColor.GRAY + "Or retype selection for a different " + string.toLowerCase());
			p.sendMessage(ChatColor.DARK_GRAY + "---------------------------------------------");

			getnewid.put(p.getUniqueId(), e.getMessage());
		}

		if (getnewid.containsKey(p.getUniqueId()) && e.getMessage().equalsIgnoreCase("done")) {

			String newidf = getnewid.get(p.getUniqueId());
			List<String> loreList = new ArrayList<String>();

			if (!string.equals("Amount")) {
				loreList.add(ChatColor.GRAY + ChatColor.translateAlternateColorCodes('&', newidf));
				idm.setLore(loreList);
				id.setItemMeta(idm);

				inv.setItem(slot, id);
			}

			if (string.equals("Name")) {
				ItemStack item = inv.getItem(23);
				ItemMeta itemm = item.getItemMeta();

				itemm.setDisplayName(ChatColor.translateAlternateColorCodes('&', newidf));
				item.setItemMeta(itemm);
				inv.setItem(23, item);
				// slot 23
			}

			if (string.equals("Amount")) {

				if (!isInt(newidf)) {
					p.sendMessage(" ");
					p.sendMessage(ChatColor.RED + "Value must be in number format! Please try again.");
					return;
				}

				ItemStack item = inv.getItem(35);
				ItemMeta itemm = item.getItemMeta();

				itemm.setDisplayName(ChatColor.WHITE + "Amount: " + ChatColor.GRAY + newidf);
				inv.getItem(23).setAmount(Integer.valueOf(newidf));
				item.setItemMeta(itemm);
				inv.setItem(35, item);
				// slot 35
			}

			Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CustomRecipes"), () -> {
				p.openInventory(inv);
			});

			inventoryinstance.remove(p.getUniqueId());
			getr.remove(p.getUniqueId());
			editmeta.remove(p.getUniqueId());
			getnewid.remove(p.getUniqueId());
			return;
		}

		if (!getnewid.containsKey(p.getUniqueId()) || e.getMessage().equalsIgnoreCase("cancel")) {
			editmeta.remove(p.getUniqueId());
			getr.remove(p.getUniqueId());

			Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("CustomRecipes"), () -> {
				p.openInventory(inv);
			});

			inventoryinstance.remove(p.getUniqueId());

		}
	}

	public boolean hasSomething(Inventory i) {

		if ((i.getItem(10) != null || i.getItem(11) != null || i.getItem(12) != null || i.getItem(19) != null
				|| i.getItem(20) != null || i.getItem(21) != null || i.getItem(28) != null || i.getItem(29) != null
				|| i.getItem(30) != null) && i.getItem(23) != null) {
			return true;
		}
		return false;
	}

	String getItemName(ItemStack itemStack) {
		return (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName())
				? itemStack.getItemMeta().getDisplayName()
				: "none";
	}

	int getItemAmount(ItemStack itemStack) {
		return (itemStack != null) ? itemStack.getAmount() : 1;
	}

	String getItemLore(ItemStack itemStack) {
		return (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore())
				? String.join("\n", itemStack.getItemMeta().getLore())
				: "";
	}

	String getCustomIdentifier(ItemStack itemStack) {
		if (itemStack != null && NBTEditor.contains(itemStack, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER")) {
			return NBTEditor.getString(itemStack, NBTEditor.CUSTOM_DATA, "CUSTOM_ITEM_IDENTIFIER");
		}
		return "none";
	}

	Map<String, Object> convertToRecipeConfig(Inventory inventory) {
		Map<String, Object> recipeConfig = new HashMap<>();

		int[] ingredientSlots = { 10, 11, 12, 19, 20, 21, 28, 29, 30 };

		Map<String, Map<String, Object>> ingredients = new HashMap<>();
		for (int i = 0; i < ingredientSlots.length; i++) {
			ItemStack itemStack = inventory.getItem(ingredientSlots[i]);
			if (itemStack != null && itemStack.getType() != org.bukkit.Material.AIR) {
				String itemName = getItemName(itemStack);
				String identifier = getCustomIdentifier(itemStack);
				int amount = getItemAmount(itemStack);

				Map<String, Object> ingredient = new HashMap<>();
				ingredient.put("Name", itemName);
				ingredient.put("Identifier", identifier);
				ingredient.put("Amount", amount);
				ingredients.put(String.valueOf(i), ingredient);
			}
		}
		recipeConfig.put("Ingredients", ingredients);

		List<String> itemCrafting = generateItemCrafting(ingredients);
		recipeConfig.put("ItemCrafting", itemCrafting);

		ItemStack resultItem = inventory.getItem(23);
		if (resultItem != null && resultItem.getType() != org.bukkit.Material.AIR) {
			recipeConfig.put("Item", resultItem.getType().name());
			recipeConfig.put("Amount", getItemAmount(resultItem));
			recipeConfig.put("Name", getItemName(resultItem));
			recipeConfig.put("Identifier", getCustomIdentifier(resultItem));
			recipeConfig.put("Lore", getItemLore(resultItem));
		}

		return recipeConfig;
	}

	List<String> generateItemCrafting(Map<String, Map<String, Object>> ingredients) {
		List<String> itemCrafting = new ArrayList<>();
		Set<String> usedLetters = new HashSet<>();
		Map<String, Map<String, Object>> usedIngredients = new HashMap<>(); // Initialize usedIngredients map

		// Initialize the crafting pattern
		for (int i = 0; i < 3; i++) {
			itemCrafting.add("XXX");
		}
		for (int i = 0; i < 9; i++) {
			String slotNumber = String.valueOf(i);
			if (!ingredients.containsKey(slotNumber)) {
				continue;
			}
			Map<String, Object> ingredientData = ingredients.get(slotNumber);

			if (ingredientData.isEmpty()) {
				continue;
			}
			String letter = getLetterForIngredient(ingredientData, usedLetters, usedIngredients);

			// Update the crafting pattern with the assigned letter
			for (int j = 0; j < 3; j++) {
				String row = itemCrafting.get(j).replaceFirst("X", letter);
				itemCrafting.set(j, row);
			}

			usedLetters.add(letter);
			usedIngredients.put(letter, ingredientData); // Add the used ingredient to usedIngredients
		}

		return itemCrafting;
	}

	String getLetterForIngredient(Map<String, Object> ingredientData, Set<String> usedLetters,
			Map<String, Map<String, Object>> usedIngredients) {
		String name = (String) ingredientData.get("Name");
		String identifier = (String) ingredientData.get("Identifier");
		int amount = (int) ingredientData.get("Amount");

		String firstLetter = name.substring(0, 1).toUpperCase();
		if (!firstLetter.matches("[A-Z]")) {
			return "X";
		}

		for (char letter = 'A'; letter <= 'Z'; letter++) {
			String nextLetter = String.valueOf(letter);
			if (!usedLetters.contains(nextLetter)
					&& !isCombinationUsed(nextLetter, identifier, amount, usedLetters, usedIngredients)) {
				return nextLetter;
			}
		}

		return "X";
	}

	boolean isCombinationUsed(String letter, String identifier, int amount, Set<String> usedLetters,
			Map<String, Map<String, Object>> usedIngredients) {
		for (String usedLetter : usedLetters) {
			Map<String, Object> usedIngredientData = usedIngredients.get(usedLetter);
			if (usedIngredientData != null) {
				String usedIdentifier = (String) usedIngredientData.get("Identifier");
				int usedAmount = (int) usedIngredientData.get("Amount");
				if (usedIdentifier.equals(identifier) && usedAmount == amount) {
					return true;
				}
			}
		}
		return false;
	}
}