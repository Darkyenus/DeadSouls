package com.darkyen.minecraft;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

/**
 *
 */
@SuppressWarnings("unused")
public class DeadSoulsApiTest extends JavaPlugin {

	private static final int radius = 30;

	@Override
	public void onEnable() {
		try {
			final DeadSoulsAPI api = DeadSoulsAPI.instance();

			final Server server = getServer();
			final ArrayList<DeadSoulsAPI.Soul> souls = new ArrayList<>();
			final Location location = new Location(null, 0, 0, 0);
			final Location locationSoul = new Location(null, 0, 0, 0);
			final ArrayList<ItemStack> itemPool = new ArrayList<>();
			int[] xpPool = { 0 };

			server.getScheduler().runTaskTimer(this, () -> {

				int removedSoulCount = 0;
				int newSoulCount = 0;
				for (Player onlinePlayer : server.getOnlinePlayers()) {
					onlinePlayer.getLocation(location);

					// Remove every other soul around
					final UUID worldUUID = onlinePlayer.getWorld().getUID();
					api.getSoulsByLocation(souls, worldUUID, location.getBlockX(), location.getBlockZ(), radius);
					boolean remove = false;
					for (DeadSoulsAPI.Soul soul : souls) {
						if (remove) {
							Collections.addAll(itemPool, soul.getItems());
							xpPool[0] += soul.getExperiencePoints();
							api.removeSoul(soul);
							removedSoulCount++;
						}
						remove = !remove;
					}

					// Put down new ones
					for (int i = 0; i < 10; i++) {
						if (deriveNewSoulLocation(location, locationSoul)) {
							final int itemAmount = Math.min(Math.max((int) Math.round(random.nextGaussian() * itemPool.size() * 0.5f + 1f), 0), itemPool.size());
							final ItemStack[] items;
							if (itemAmount > 0) {
								items = new ItemStack[itemAmount];
								for (int itemI = 0; itemI < itemAmount; itemI++) {
									items[itemI] = itemPool.remove(itemPool.size() - 1);
								}
							} else if (random.nextInt(5) == 0) {
								// Random item
								final Material mat = randomItemMaterial();
								items = new ItemStack[]{ new ItemStack(mat, 1 + random.nextInt(mat.getMaxStackSize())) };
							} else {
								items = null;
							}

							int xp = Math.min(Math.max((int)(random.nextGaussian() * xpPool[0] * 0.1), 0), xpPool[0]);
							if (xp > 0) {
								xpPool[0] -= xp;
							} else if (random.nextInt(5) == 0) {
								xp = random.nextInt(10);
							}

							if (xp == 0 && items == null) {
								// So that the soul is visible
								xp = 1;
							}

							api.createSoul(null, worldUUID, locationSoul.getX(), locationSoul.getY(), locationSoul.getZ(), items, xp);
							newSoulCount++;
						}
					}
				}

				getLogger().info("Removed "+removedSoulCount+" souls, Added "+newSoulCount);

			}, 0, 20*2);

			server.getPluginManager().registerEvents(new Listener() {

				@EventHandler(ignoreCancelled = true)
				public void onSoulPickup(DeadSoulsAPI.SoulPickupEvent event) {
					if (random.nextBoolean()) {
						event.setCancelled(true);
						event.getPlayer().sendMessage("YOINKS! This delicious soul with "+event.getSoul().getExperiencePoints()+" XP and "+event.getSoul().getItems().length+" items is NOT FOR YOU!");
						api.removeSoul(event.getSoul());
						event.getPlayer().playSound(event.getSoul().getLocation(), "entity.piglin.jealous", 1f, 1f);
					}
				}

			}, this);
		} catch (NoClassDefFoundError e) {
			getLogger().info("DeadSouls is not installed");
		}
	}

	private static final Random random = new Random();

	private static boolean deriveNewSoulLocation(Location player, Location out) {
		// http://extremelearning.com.au/how-to-generate-uniformly-random-points-on-n-spheres-and-n-balls/
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = random.nextDouble() * radius * 0.75 + radius * 0.25;
		final int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
		final int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

		final World world = player.getWorld();
		if (world == null) return false;
		out.setWorld(world);
		out.setX(x + 0.5);
		out.setZ(z + 0.5);

		// Now that we have random position near us, lets find a nice height for the soul
		int passableY = -1;
		final int originY = player.getBlockY() + random.nextInt(radius) - radius/2;
		for (int offset = 1; offset < radius * 2; offset++) {
			// We are looking for an empty block
			if (world.getBlockAt(x, originY + offset, z).isPassable()) {
				passableY = originY + offset;
				break;
			}
			if (world.getBlockAt(x, originY - offset, z).isPassable()) {
				passableY = originY - offset;
				break;
			}
		}

		if (passableY < 0) {
			return false;
		}

		out.setY(passableY + 0.5);
		return true;
	}

	private static final Material[] MATERIALS = Material.values();
	private static Material randomItemMaterial() {
		while (true) {
			final Material mat = MATERIALS[random.nextInt(MATERIALS.length)];
			if (!mat.isLegacy() && mat.isItem()) {
				return mat;
			}
		}
	}
}
