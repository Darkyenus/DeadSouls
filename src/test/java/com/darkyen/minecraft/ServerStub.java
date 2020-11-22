package com.darkyen.minecraft;

import org.bukkit.BanList;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.StructureType;
import org.bukkit.Tag;
import org.bukkit.UnsafeValues;
import org.bukkit.Warning;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapView;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 *
 */
public class ServerStub implements Server {

	@Override
	public @NotNull String getName() { return "TEST"; }

	@Override
	public @NotNull String getVersion() { return "TEST_VER"; }

	@Override
	public @NotNull String getBukkitVersion() { return "BUKKIT_TEST_VER"; }

	@Override
	public @NotNull Collection<? extends Player> getOnlinePlayers() { return Collections.emptyList(); }

	@Override
	public int getMaxPlayers() { return 0; }

	@Override
	public int getPort() { return 0; }

	@Override
	public int getViewDistance() { return 0; }

	@Override
	public @NotNull String getIp() { return "0.0.0.0"; }

	@Override
	public @NotNull String getWorldType() { return ""; }

	@Override
	public boolean getGenerateStructures() { return false; }

	@Override
	public boolean getAllowEnd() { return false; }

	@Override
	public boolean getAllowNether() { return false; }

	@Override
	public boolean hasWhitelist() { return false; }

	@Override
	public void setWhitelist(boolean value) { }

	@Override
	public @NotNull Set<OfflinePlayer> getWhitelistedPlayers() { return Collections.emptySet(); }

	@Override
	public void reloadWhitelist() { }

	@Override
	public int broadcastMessage(@NotNull String message) { return 0; }

	@Override
	public @NotNull String getUpdateFolder() { return "server-stub-update-folder"; }

	@Override
	public @NotNull File getUpdateFolderFile() { return new File(getUpdateFolder()); }

	@Override
	public long getConnectionThrottle() { return 0; }

	@Override
	public int getTicksPerAnimalSpawns() { return 0; }

	@Override
	public int getTicksPerMonsterSpawns() { return 0; }

	@Override
	public int getTicksPerWaterSpawns() {
		return 0;
	}

	@Override
	public int getTicksPerWaterAmbientSpawns() {
		return 0;
	}

	@Override
	public int getTicksPerAmbientSpawns() {
		return 0;
	}

	@Override
	public @Nullable Player getPlayer(@NotNull String name) { return null; }

	@Override
	public @Nullable Player getPlayerExact(@NotNull String name) { return null; }

	@Override
	public @NotNull List<Player> matchPlayer(@NotNull String name) { return Collections.emptyList(); }

	@Override
	public @Nullable Player getPlayer(@NotNull UUID id) { return null; }

	@Override
	public @NotNull PluginManager getPluginManager() { return null; }

	@Override
	public @NotNull BukkitScheduler getScheduler() { return null; }

	@Override
	public @NotNull ServicesManager getServicesManager() { return null; }

	@Override
	public @NotNull List<World> getWorlds() {
		return Collections.emptyList();
	}

	@Override
	public @Nullable World createWorld(@NotNull WorldCreator creator) { return null; }

	@Override
	public boolean unloadWorld(@NotNull String name, boolean save) { return false; }

	@Override
	public boolean unloadWorld(@NotNull World world, boolean save) { return false; }

	@Override
	public @Nullable World getWorld(@NotNull String name) {
		return null;
	}

	@Override
	public @Nullable World getWorld(@NotNull UUID uid) {
		return null;
	}

	@Override
	public @Nullable MapView getMap(int id) {
		return null;
	}

	@Override
	public @NotNull MapView createMap(@NotNull World world) {
		return null;
	}

	@Override
	public @NotNull ItemStack createExplorerMap(@NotNull World world, @NotNull Location location, @NotNull StructureType structureType) {
		return null;
	}

	@Override
	public @NotNull ItemStack createExplorerMap(@NotNull World world, @NotNull Location location, @NotNull StructureType structureType, int radius, boolean findUnexplored) {
		return null;
	}

	@Override
	public void reload() { }

	@Override
	public void reloadData() { }

	private final Logger logger = Logger.getLogger("ServerStub");

	@Override
	public @NotNull Logger getLogger() { return logger; }

	@Override
	public @Nullable PluginCommand getPluginCommand(@NotNull String name) { return null; }

	@Override
	public void savePlayers() { }

	@Override
	public boolean dispatchCommand(@NotNull CommandSender sender, @NotNull String commandLine) throws CommandException { return false; }

	@Override
	public boolean addRecipe(@Nullable Recipe recipe) { return false; }

	@Override
	public @NotNull List<Recipe> getRecipesFor(@NotNull ItemStack result) { return Collections.emptyList(); }

	@Override
	public @Nullable Recipe getRecipe(@NotNull NamespacedKey namespacedKey) {
		return null;
	}

	@Override
	public @NotNull Iterator<Recipe> recipeIterator() { return Collections.emptyIterator(); }

	@Override
	public void clearRecipes() { }

	@Override
	public void resetRecipes() { }

	@Override
	public boolean removeRecipe(@NotNull NamespacedKey namespacedKey) {
		return false;
	}

	@Override
	public @NotNull Map<String, String[]> getCommandAliases() { return Collections.emptyMap(); }

	@Override
	public int getSpawnRadius() { return 0; }

	@Override
	public void setSpawnRadius(int value) { }

	@Override
	public boolean getOnlineMode() {
		return false;
	}

	@Override
	public boolean getAllowFlight() {
		return false;
	}

	@Override
	public boolean isHardcore() {
		return false;
	}

	@Override
	public void shutdown() { }

	@Override
	public int broadcast(@NotNull String message, @NotNull String permission) {
		return 0;
	}

	@Override
	public @NotNull OfflinePlayer getOfflinePlayer(@NotNull String name) {
		return null;
	}

	@Override
	public @NotNull OfflinePlayer getOfflinePlayer(@NotNull UUID id) {
		return null;
	}

	@Override
	public @NotNull Set<String> getIPBans() {
		return Collections.emptySet();
	}

	@Override
	public void banIP(@NotNull String address) { }

	@Override
	public void unbanIP(@NotNull String address) { }

	@Override
	public @NotNull Set<OfflinePlayer> getBannedPlayers() {
		return Collections.emptySet();
	}

	@Override
	public @NotNull BanList getBanList(BanList.@NotNull Type type) {
		return null;
	}

	@Override
	public @NotNull Set<OfflinePlayer> getOperators() {
		return Collections.emptySet();
	}

	@Override
	public @NotNull GameMode getDefaultGameMode() {
		return GameMode.SURVIVAL;
	}

	@Override
	public void setDefaultGameMode(@NotNull GameMode mode) {

	}

	@Override
	public @NotNull ConsoleCommandSender getConsoleSender() {
		return null;
	}

	@Override
	public @NotNull File getWorldContainer() {
		return null;
	}

	@NotNull
	@Override
	public OfflinePlayer[] getOfflinePlayers() {
		return new OfflinePlayer[0];
	}

	@Override
	public @NotNull Messenger getMessenger() {
		return null;
	}

	@Override
	public @NotNull HelpMap getHelpMap() {
		return null;
	}

	@Override
	public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, @NotNull InventoryType type) {
		return null;
	}

	@Override
	public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, @NotNull InventoryType type, @NotNull String title) {
		return null;
	}

	@Override
	public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, int size) throws IllegalArgumentException {
		return null;
	}

	@Override
	public @NotNull Inventory createInventory(@Nullable InventoryHolder owner, int size, @NotNull String title) throws IllegalArgumentException {
		return null;
	}

	@Override
	public @NotNull Merchant createMerchant(@Nullable String title) {
		return null;
	}

	@Override
	public int getMonsterSpawnLimit() {
		return 0;
	}

	@Override
	public int getAnimalSpawnLimit() {
		return 0;
	}

	@Override
	public int getWaterAnimalSpawnLimit() {
		return 0;
	}

	@Override
	public int getWaterAmbientSpawnLimit() {
		return 0;
	}

	@Override
	public int getAmbientSpawnLimit() {
		return 0;
	}

	@Override
	public boolean isPrimaryThread() {
		return false;
	}

	@Override
	public @NotNull String getMotd() {
		return null;
	}

	@Override
	public @Nullable String getShutdownMessage() {
		return null;
	}

	@Override
	public Warning.@NotNull WarningState getWarningState() {
		return null;
	}

	private final ItemFactory itemFactory = new ItemFactory() {
		@Override
		public @Nullable ItemMeta getItemMeta(@NotNull Material material) {
			return null;
		}

		@Override
		public boolean isApplicable(@Nullable ItemMeta meta, @Nullable ItemStack stack) throws IllegalArgumentException {
			return false;
		}

		@Override
		public boolean isApplicable(@Nullable ItemMeta meta, @Nullable Material material) throws IllegalArgumentException {
			return false;
		}

		@Override
		public boolean equals(@Nullable ItemMeta meta1, @Nullable ItemMeta meta2) throws IllegalArgumentException {
			return Objects.equals(meta1, meta2);
		}

		@Override
		public @Nullable ItemMeta asMetaFor(@NotNull ItemMeta meta, @NotNull ItemStack stack) throws IllegalArgumentException {
			return null;
		}

		@Override
		public @Nullable ItemMeta asMetaFor(@NotNull ItemMeta meta, @NotNull Material material) throws IllegalArgumentException {
			return null;
		}

		@Override
		public @NotNull Color getDefaultLeatherColor() {
			return Color.FUCHSIA;
		}

		@Override
		public @NotNull Material updateMaterial(@NotNull ItemMeta meta, @NotNull Material material) throws IllegalArgumentException {
			return material;
		}
	};

	@Override
	public @NotNull ItemFactory getItemFactory() {
		return itemFactory;
	}

	@Override
	public @Nullable ScoreboardManager getScoreboardManager() {
		return null;
	}

	@Override
	public @Nullable CachedServerIcon getServerIcon() {
		return null;
	}

	@Override
	public @NotNull CachedServerIcon loadServerIcon(@NotNull File file) throws IllegalArgumentException, Exception {
		return null;
	}

	@Override
	public @NotNull CachedServerIcon loadServerIcon(@NotNull BufferedImage image) throws IllegalArgumentException, Exception {
		return null;
	}

	@Override
	public void setIdleTimeout(int threshold) { }

	@Override
	public int getIdleTimeout() {
		return 0;
	}

	@Override
	public ChunkGenerator.@NotNull ChunkData createChunkData(@NotNull World world) {
		return null;
	}

	@Override
	public @NotNull BossBar createBossBar(@Nullable String title, @NotNull BarColor color, @NotNull BarStyle style, @NotNull BarFlag... flags) {
		return null;
	}

	@Override
	public @NotNull KeyedBossBar createBossBar(@NotNull NamespacedKey key, @Nullable String title, @NotNull BarColor color, @NotNull BarStyle style, @NotNull BarFlag... flags) {
		return null;
	}

	@Override
	public @NotNull Iterator<KeyedBossBar> getBossBars() {
		return Collections.emptyIterator();
	}

	@Override
	public @Nullable KeyedBossBar getBossBar(@NotNull NamespacedKey key) {
		return null;
	}

	@Override
	public boolean removeBossBar(@NotNull NamespacedKey key) {
		return false;
	}

	@Override
	public @Nullable Entity getEntity(@NotNull UUID uuid) {
		return null;
	}

	@Override
	public @Nullable Advancement getAdvancement(@NotNull NamespacedKey key) {
		return null;
	}

	@Override
	public @NotNull Iterator<Advancement> advancementIterator() {
		return Collections.emptyIterator();
	}

	@Override
	public @NotNull BlockData createBlockData(@NotNull Material material) {
		return null;
	}

	@Override
	public @NotNull BlockData createBlockData(@NotNull Material material, @Nullable Consumer<BlockData> consumer) {
		return null;
	}

	@Override
	public @NotNull BlockData createBlockData(@NotNull String data) throws IllegalArgumentException {
		return null;
	}

	@Override
	public @NotNull BlockData createBlockData(@Nullable Material material, @Nullable String data) throws IllegalArgumentException {
		return null;
	}

	@Override
	public @Nullable <T extends Keyed> Tag<T> getTag(@NotNull String registry, @NotNull NamespacedKey tag, @NotNull Class<T> clazz) {
		return null;
	}

	@Override
	public @NotNull <T extends Keyed> Iterable<Tag<T>> getTags(@NotNull String registry, @NotNull Class<T> clazz) {
		return Collections.emptyList();
	}

	@Override
	public @Nullable LootTable getLootTable(@NotNull NamespacedKey key) {
		return null;
	}

	@Override
	public @NotNull List<Entity> selectEntities(@NotNull CommandSender sender, @NotNull String selector) throws IllegalArgumentException {
		return Collections.emptyList();
	}

	private final UnsafeValues unsafeValues = new UnsafeValues() {
		@Override
		public Material toLegacy(Material material) {
			return null;
		}

		@Override
		public Material fromLegacy(Material material) {
			return null;
		}

		@Override
		public Material fromLegacy(MaterialData material) {
			return material.getItemType();
		}

		@Override
		public Material fromLegacy(MaterialData material, boolean itemPriority) {
			return material.getItemType();
		}

		@Override
		public BlockData fromLegacy(Material material, byte data) {
			return null;
		}

		@Override
		public Material getMaterial(String material, int version) {
			return Material.getMaterial(material);
		}

		@Override
		public int getDataVersion() {
			return 1234;
		}

		@Override
		public ItemStack modifyItemStack(ItemStack stack, String arguments) {
			return null;
		}

		@Override
		public void checkSupported(PluginDescriptionFile pdf) throws InvalidPluginException { }

		@Override
		public byte[] processClass(PluginDescriptionFile pdf, String path, byte[] clazz) {
			return new byte[0];
		}

		@Override
		public Advancement loadAdvancement(NamespacedKey key, String advancement) {
			return null;
		}

		@Override
		public boolean removeAdvancement(NamespacedKey key) { return false; }
	};

	@Override
	public @NotNull UnsafeValues getUnsafe() {
		return unsafeValues;
	}

	@Override
	public @NotNull Spigot spigot() {
		return null;
	}

	@Override
	public void sendPluginMessage(@NotNull Plugin source, @NotNull String channel, @NotNull byte[] message) { }

	@Override
	public @NotNull Set<String> getListeningPluginChannels() {
		return Collections.emptySet();
	}

}
