package com.darkyen.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * The Dead Souls plugin implements this interface and it is recommended to only interact with it through it.
 * Methods in this interface are considered stable and will not change across versions.
 *
 * Most methods are designed to be efficient and allocation free.
 *
 * Unless specified otherwise, the methods are not thread safe and MUST be called
 * from the main thread (the one from which standard Spigot callbacks are called).
 *
 * Reference of this interface when the plugin is not present on the server will result
 * in a {@link NoClassDefFoundError}, so be prepared to handle that.
 *
 * @since 1.6
 */
@SuppressWarnings("unused")
public interface DeadSoulsAPI {

	/**
	 * Entry point for this API.
	 * @return instance of the plugin
	 * @throws NoClassDefFoundError when the plugin is not installed (actually, Java throws that when it can't load this interface)
	 * @throws IllegalStateException when DeadSouls plugin exists, but is not loaded
	 */
	@NotNull
	static DeadSoulsAPI instance() throws NoClassDefFoundError, IllegalStateException {
		final Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("DeadSouls");
		if (plugin == null) {
			throw new IllegalStateException("DeadSouls plugin classes are loaded, but the plugin is not");
		}
		return (DeadSoulsAPI) plugin;
	}

	/** Get all souls which exist.
	 * @param out a collection into which all souls will be added (after being cleared).
	 * This method does zero allocations and is thread safe. */
	void getSouls(@NotNull Collection<@NotNull Soul> out);

	/** Same as {@link #getSouls(Collection)}, but only return souls which currently belong to a certain player.
	 * @param playerUUID null means find all souls which belong to on one. */
	void getSoulsByPlayer(@NotNull Collection<@NotNull Soul> out, @Nullable UUID playerUUID);

	/** Same as {@link #getSouls(Collection)}, but only return souls which belong to a certain world. */
	void getSoulsByWorld(@NotNull Collection<@NotNull Soul> out, @NotNull UUID worldUUID);

	/** Same as {@link #getSouls(Collection)}, but only return souls which belong to a certain world.
	 * @param playerUUID null means find all souls which belong to on one. */
	void getSoulsByPlayerAndWorld(@NotNull Collection<@NotNull Soul> out, @Nullable UUID playerUUID, @NotNull UUID worldUUID);

	/** Similar to {@link #getSouls(Collection)}, but only return souls which belong to a certain world and are located inside
	 * a cylinder of infinite height, centered at (x, z) and having the given radius.
	 * NOTE: For efficiency, returned souls might actually be outside of the radius.
	 * If you care about the exact distance, compute it yourself.
	 * NOTE: This method is NOT thread safe. */
	void getSoulsByLocation(@NotNull Collection<@NotNull Soul> out, @NotNull UUID worldUUID, int x, int z, int radius);

	/** Free the soul (remove its owner), if not free yet. */
	void freeSoul(@NotNull Soul soul);

	/** Set the items of the soul, replaces any old ones.
	 * The passed in array is used as is and therefore MUST NOT be further modified, including any item modifications. */
	void setSoulItems(@NotNull Soul soul, @NotNull ItemStack @NotNull[] items);

	/** Set the amount of experience point stored, replacing the old one. */
	void setSoulExperiencePoints(@NotNull Soul soul, int xp);

	/** Remove the soul from the world. */
	void removeSoul(@NotNull Soul soul);

	/** Return whether the soul still exists.
	 * Soul may disappear, for example, by player collecting it, it fading away or explicit {@link #removeSoul(Soul)}.
	 * Note that all other methods still work correctly even if the soul does not exist anymore. */
	boolean soulExists(@NotNull Soul soul);

	/** Create a new soul and add it into the world. Parameters correspond to the getters of {@link Soul}.
	 * @param contents similarly to {@link #setSoulItems(Soul, ItemStack[])}, DO NOT MODIFY the contents of the array after it is passed in */
	@NotNull Soul createSoul(@Nullable UUID owner, @NotNull UUID world, double x, double y, double z, @Nullable ItemStack[] contents, int xp);

	/**
	 * A soul representation.
	 * All methods are thread safe, unless specified otherwise.
	 * Custom implementations are not allowed.
	 */
	interface Soul {
		/** Get the {@link Player#getUniqueId()} of the player which owns this soul or null if already released. */
		@Nullable UUID getOwner();

		/** Get the {@link World#getUID()} of the world in which this soul is. */
		@NotNull UUID getWorld();

		/** Get the X coordinate of the soul in the world. */
		double getLocationX();
		/** Get the Y coordinate of the soul in the world. */
		double getLocationY();
		/** Get the Z coordinate of the soul in the world. */
		double getLocationZ();

		/** Get the location in the Bukkit format. May return null if the world does not exist.
		 * Not thread safe, call only from main thread (because of {@link org.bukkit.Bukkit#getWorld(UUID)}). */
		@Nullable Location getLocation();

		/** Get the timestamp of when the soul was created. (Using the semantics of {@link System#currentTimeMillis()}.) */
		long getCreationTimestamp();

		/** Get the items which are stored in the soul.
		 * DO NOT MODIFY THE ARRAY, NOR THE ItemStacks! */
		@NotNull ItemStack @NotNull[] getItems();

		/** Get the experience points stored in the soul. */
		int getExperiencePoints();
	}

	/**
	 * Event that is triggered when a {@link Player} collects a {@link Soul}.
	 *
	 * NOTE: The event instance is reused, so do not store it or access it outside of the event handler.
	 *
	 * Pickups by players that are not normally eligible for soul pickups
	 * (i.e. those in incompatible game modes) start off by being cancelled.
	 */
	final class SoulPickupEvent extends Event implements Cancellable {

		private static final HandlerList HANDLERS = new HandlerList();

		Player player;
		Soul soul;
		boolean cancelled;

		SoulPickupEvent() {}

		public @NotNull HandlerList getHandlers() {
			return HANDLERS;
		}

		public static @NotNull HandlerList getHandlerList() {
			return HANDLERS;
		}

		/** The player that is picking up the soul. */
		@NotNull
		public Player getPlayer() {
			return player;
		}

		/** The soul that is being picked up. */
		@NotNull
		public Soul getSoul() {
			return soul;
		}

		/** Whether or not this soul will be picked up. */
		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public void setCancelled(boolean c) {
			this.cancelled = c;
		}
	}
}
