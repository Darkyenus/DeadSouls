package com.darkyen.minecraft;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import static com.darkyen.minecraft.Util.distance2;
import static com.darkyen.minecraft.Util.getTotalExperience;
import static com.darkyen.minecraft.Util.isNear;
import static com.darkyen.minecraft.Util.parseTimeMs;
import static com.darkyen.minecraft.Util.set;

/**
 *
 */
public class DeadSouls extends JavaPlugin implements Listener {

    private SoulDatabase soulDatabase;
    private long soulFreeAfterMs = Long.MAX_VALUE;
    private long soulFadesAfterMs = Long.MAX_VALUE;

    private float retainedXPPercent;
    private int retainedXPPerLevel;

    private final HashMap<Player, PlayerSoulInfo> watchedPlayers = new HashMap<>();
    private boolean soulDatabaseChanged = false;

    private static final double COLLECTION_DISTANCE2 = NumberConversions.square(1);

    private static final Particle.DustOptions SOUL_DUST_OPTIONS_ITEMS = new Particle.DustOptions(Color.WHITE, 2f);
    private static final Particle.DustOptions SOUL_DUST_OPTIONS_XP = new Particle.DustOptions(Color.AQUA, 2f);
    private static final Particle.DustOptions SOUL_DUST_OPTIONS_GONE = new Particle.DustOptions(Color.YELLOW, 3f);

    private static final ItemStack[] NO_ITEM_STACKS = new ItemStack[0];
    private final ComparatorSoulDistanceTo processPlayers_comparatorDistanceTo = new ComparatorSoulDistanceTo();
    private final Location processPlayers_playerLocation = new Location(null, 0, 0, 0);
    private final Random processPlayers_random = new Random();

    private long processPlayers_nextFadeCheck = 0;

    private void processPlayers() {
        final long now = System.currentTimeMillis();

        if (now > processPlayers_nextFadeCheck && soulFadesAfterMs < Long.MAX_VALUE) {
            final int faded = soulDatabase.removeFadedSouls(soulFadesAfterMs);
            if (faded > 0) {
                this.soulDatabaseChanged = true;
                getLogger().log(Level.INFO, "Removed "+faded+" faded soul(s)");
            }
            processPlayers_nextFadeCheck = now + 1000 * 60;// Check every minute
        }

        final boolean soulDatabaseChanged = this.soulDatabaseChanged;
        this.soulDatabaseChanged = false;

        final boolean playSounds = this.processPlayers_random.nextInt(8) == 0;

        for (Map.Entry<Player, PlayerSoulInfo> entry : watchedPlayers.entrySet()) {
            final Player player = entry.getKey();
            final PlayerSoulInfo info = entry.getValue();

            boolean searchNewSouls = soulDatabaseChanged;

            // Update location
            final Location playerLocation = player.getLocation(processPlayers_playerLocation);
            if (!isNear(playerLocation, info.lastKnownLocation, 16)) {
                set(info.lastKnownLocation, playerLocation);
                searchNewSouls = true;
            }

            final World world = playerLocation.getWorld();
            if (world != null) {
                final Block underPlayer =
                        world.getBlockAt(playerLocation.getBlockX(), playerLocation.getBlockY() - 1, playerLocation.getBlockZ());
                if (underPlayer.getType().isSolid()) {
                    final Block atPlayer =
                            world.getBlockAt(playerLocation.getBlockX(), playerLocation.getBlockY(), playerLocation.getBlockZ());
                    if (atPlayer.getType() != Material.LAVA) {
                        // Do not spawn souls in air or in lava
                        set(info.lastSafeLocation, playerLocation);
                    }
                }
            }

            // Update visible souls
            final ArrayList<SoulDatabase.Soul> visibleSouls = info.visibleSouls;
            if (searchNewSouls) {
                visibleSouls.clear();
                soulDatabase.findSouls(playerLocation.getWorld(), playerLocation.getBlockX(), playerLocation.getBlockZ(), 100, visibleSouls);
            }

            if (visibleSouls.isEmpty()) {
                continue;
            }

            { // Sort souls
                final ComparatorSoulDistanceTo comparator = this.processPlayers_comparatorDistanceTo;
                comparator.toX = playerLocation.getBlockX();
                comparator.toY = playerLocation.getBlockY();
                comparator.toZ = playerLocation.getBlockZ();
                visibleSouls.sort(comparator);
            }

            // Send particles
            final int soulCount = visibleSouls.size();
            int remainingSoulsToShow = 16;
            for (int i = 0; i < soulCount && remainingSoulsToShow > 0; i++) {
                final SoulDatabase.Soul soul = visibleSouls.get(i);
                if (!soul.isAccessibleBy(player, now, soulFreeAfterMs)) {
                    // Soul of somebody else, do not show nor collect
                    continue;
                }

                // Show this soul!
                if (soul.xp > 0 && soul.items.length > 0) {
                    player.spawnParticle(Particle.REDSTONE, soul.location, 10, 0.1, 0.1, 0.1, SOUL_DUST_OPTIONS_ITEMS);
                    player.spawnParticle(Particle.REDSTONE, soul.location, 10, 0.12, 0.12, 0.12, SOUL_DUST_OPTIONS_XP);
                } else if (soul.xp > 0) {
                    // Only xp
                    player.spawnParticle(Particle.REDSTONE, soul.location, 20, 0.1, 0.1, 0.1, SOUL_DUST_OPTIONS_XP);
                } else {
                    // Only items
                    player.spawnParticle(Particle.REDSTONE, soul.location, 20, 0.1, 0.1, 0.1, SOUL_DUST_OPTIONS_ITEMS);
                }
                remainingSoulsToShow--;
            }

            // Process collisions
            if (!player.isDead() && player.getGameMode() == GameMode.SURVIVAL) {
                //noinspection ForLoopReplaceableByForEach
                for (int soulI = 0; soulI < visibleSouls.size(); soulI++) {
                    final SoulDatabase.Soul closestSoul = visibleSouls.get(soulI);
                    if (!closestSoul.isAccessibleBy(player, now, soulFreeAfterMs)) {
                        // Soul of somebody else, do not show nor collect
                        continue;
                    }

                    final double dst2 = distance2(closestSoul.location, playerLocation, 0.4);
                    if (dst2 < COLLECTION_DISTANCE2) {
                        // Collect it!
                        if (closestSoul.xp > 0) {
                            player.giveExp(closestSoul.xp);
                            closestSoul.xp = 0;
                            player.playSound(closestSoul.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        }

                        final @NotNull ItemStack[] items = closestSoul.items;
                        if (items.length > 0) {
                            final HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(items);
                            if (overflow.isEmpty()) {
                                closestSoul.items = NO_ITEM_STACKS;
                            } else {
                                closestSoul.items = overflow.values().toArray(NO_ITEM_STACKS);
                            }

                            boolean someCollected = false;
                            if (overflow.size() < items.length) {
                                someCollected = true;
                            } else {
                                for (Map.Entry<Integer, ItemStack> overflowEntry : overflow.entrySet()) {
                                    if (!items[overflowEntry.getKey()].equals(overflowEntry.getValue())) {
                                        someCollected = true;
                                        break;
                                    }
                                }
                            }

                            if (someCollected) {
                                player.playSound(closestSoul.location, Sound.ITEM_TRIDENT_RETURN, 1f, 0.5f);
                            }
                        }

                        if (closestSoul.xp <= 0 && closestSoul.items.length <= 0) {
                            // Soul is depleted
                            soulDatabase.removeSoul(closestSoul);
                            this.soulDatabaseChanged = true;

                            // Do some fancy effect
                            player.playSound(closestSoul.location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.1f, 0.5f);
                            player.spawnParticle(Particle.REDSTONE, closestSoul.location, 20, 0.2, 0.2, 0.2, SOUL_DUST_OPTIONS_GONE);
                        }
                    } else if (playSounds) {
                        player.playSound(closestSoul.location, Sound.BLOCK_BEACON_AMBIENT, 16f, 0.75f);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        if (!Bukkit.getBukkitVersion().startsWith("1.13")) {
            getLogger().log(Level.WARNING, "This is a special version for 1.13 servers and this does not seem to be a 1.13 server.");
            getLogger().log(Level.WARNING, "If you are running a lower version, nothing is guaranteed to work.");
            getLogger().log(Level.WARNING, "If you are running a higher version, please update to the mainline release.");
        }

        soulFreeAfterMs = parseTimeMs(getConfig().getString("soul-free-after"), Long.MAX_VALUE, getLogger());
        soulFadesAfterMs = parseTimeMs(getConfig().getString("soul-fades-after"), Long.MAX_VALUE, getLogger());

        {
            this.retainedXPPercent = 90;
            this.retainedXPPerLevel = 0;
            final String retainedXp = getConfig().getString("retained-xp");
            if (retainedXp != null) {
                String sanitizedRetainedXp = retainedXp.replaceAll("\\s", "");
                boolean percent = false;
                if (sanitizedRetainedXp.endsWith("%")) {
                    percent = true;
                    sanitizedRetainedXp = sanitizedRetainedXp.substring(0, sanitizedRetainedXp.length() - 1);
                }
                try {
                    final int number = Integer.parseInt(sanitizedRetainedXp);
                    if (percent) {
                        if (number < 0 || number > 100) {
                            getLogger().log(Level.WARNING, "Invalid configuration: retained-xp percent must be between 0 and 1");
                        } else {
                            retainedXPPercent = number / 100f;
                            retainedXPPerLevel = 0;
                        }
                    } else {
                        if (number < 0) {
                            getLogger().log(Level.WARNING, "Invalid configuration: retained-xp per level must be positive");
                        } else {
                            retainedXPPercent = -1f;
                            retainedXPPerLevel = number;
                        }
                    }
                } catch (NumberFormatException nfe) {
                    getLogger().log(Level.WARNING, "Invalid configuration: retained-xp has invalid format");
                }
            }
        }

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(this, this);

        // Run included tests
        for (String testClassName : new String[]{"com.darkyen.minecraft.ItemStoreTest"}) {
            try {
                final Class<?> testClass;
                try {
                    testClass = Class.forName(testClassName);
                } catch (ClassNotFoundException e) {
                    continue;
                }
                getLogger().info("Found test class: " + testClassName);

                final Method runLiveTest = testClass.getMethod("runLiveTest");
                runLiveTest.invoke(null);

                getLogger().info("Test successful");
            } catch (Exception e) {
                getLogger().log(Level.INFO, "Failed to run tests of " + testClassName, e);
            }
        }

        try {
            soulDatabase = new SoulDatabase(this, getDataFolder().toPath().resolve("souldb.bin"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load soul database", e);
        }

        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
            watchedPlayers.put(onlinePlayer, new PlayerSoulInfo());
        }

        getServer().getScheduler().runTaskTimer(this, this::processPlayers, 20, 20);
    }

    @Override
    public void onDisable() {
        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase != null) {
            try {
                final int faded = soulDatabase.removeFadedSouls(soulFadesAfterMs);
                if (faded > 0) {
                    getLogger().log(Level.INFO, "Removed "+faded+" faded soul(s)");
                }
                soulDatabase.save();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to save soul database", e);
            }
            this.soulDatabase = null;
        }

        watchedPlayers.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if ("dead_souls_free_soul".equals(command.getName()) && args.length == 1) {
            final int soulId;
            try {
                soulId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                return false;
            }

            soulDatabase.freeSoul(sender, soulId, soulFreeAfterMs);
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory() && event.getKeepLevel()) {
            return;
        }

        final Player player = event.getEntity();
        if (!player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul")) {
            return;
        }

        final ItemStack[] soulItems;
        if (!event.getKeepInventory() || event.getDrops().isEmpty()) {
            soulItems = event.getDrops().toArray(NO_ITEM_STACKS);
            event.getDrops().clear();
        } else {
            soulItems = NO_ITEM_STACKS;
        }


        int soulXp;
        if (!event.getKeepLevel()) {
            final int totalExperience = getTotalExperience(player);
            if (retainedXPPercent >= 0) {
                soulXp = Math.round(totalExperience * retainedXPPercent);
            } else {
                soulXp = retainedXPPerLevel * player.getLevel();
            }
            soulXp = Util.clamp(soulXp, 0, totalExperience);
            event.setNewExp(0);
            event.setNewLevel(0);
            event.setNewTotalExp(0);
            event.setDroppedExp(0);
        } else {
            soulXp = 0;
        }

        if (soulXp == 0 && soulItems.length == 0) {
            // Soul would be empty
            return;
        }

        PlayerSoulInfo info = watchedPlayers.get(player);
        if (info == null) {
            getLogger().log(Level.WARNING, "Player "+player+" was not watched!");
            info = new PlayerSoulInfo();
            watchedPlayers.put(player, info);
        }
        final Location soulLocation = info.findSafeSoulSpawnLocation(player);
        final int soulId = soulDatabase.addSoul(player.getUniqueId(), soulLocation, soulItems, soulXp);
        soulDatabaseChanged = true;

        final TextComponent star = new TextComponent("✦");
        star.setColor(ChatColor.YELLOW);
        final TextComponent freeMySoul = new TextComponent(" Free my soul ");
        freeMySoul.setColor(ChatColor.GOLD);
        freeMySoul.setBold(true);
        freeMySoul.setUnderlined(true);
        freeMySoul.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent("Allows other players to collect the soul immediately")}));
        freeMySoul.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dead_souls_free_soul "+soulId));
        player.spigot().sendMessage(ChatMessageType.CHAT, star, freeMySoul, star);

        player.getWorld().playSound(soulLocation, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.1f, 1.7f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        watchedPlayers.put(event.getPlayer(), new PlayerSoulInfo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerLeave(PlayerQuitEvent event) {
        watchedPlayers.remove(event.getPlayer());
    }

    private static final class PlayerSoulInfo {
        @NotNull
        final Location lastKnownLocation = new Location(null, 0, 0, 0);

        @NotNull
        final Location lastSafeLocation = new Location(null, 0, 0, 0);

        final ArrayList<SoulDatabase.Soul> visibleSouls = new ArrayList<>();

        Location findSafeSoulSpawnLocation(Player player) {
            final double soulHoverOffset = 1.2;

            final Location playerLocation = player.getLocation();
            if (isNear(lastSafeLocation, playerLocation, 20)) {
                set(playerLocation, lastSafeLocation);
                playerLocation.setY(playerLocation.getY() + soulHoverOffset);
                return playerLocation;
            }
            // Too far, now we have to find a better location
            final World world = player.getWorld();
            final int x = playerLocation.getBlockX();
            int y = playerLocation.getBlockY();
            final int z = playerLocation.getBlockZ();
            while (true) {
                final Block blockAt = world.getBlockAt(x, y, z);
                if (blockAt.getType() == Material.LAVA) {
                    y++;
                } else {
                    break;
                }
            }

            playerLocation.setY(y + soulHoverOffset);
            return playerLocation;
        }
    }

    private static final class ComparatorSoulDistanceTo implements Comparator<SoulDatabase.Soul> {

        int toX, toY, toZ;

        private int distanceTo(SoulDatabase.Soul s) {
            final int x = toX - s.location.getBlockX();
            final int y = toY - s.location.getBlockY();
            final int z = toZ - s.location.getBlockZ();
            return x*x + y*y + z*z;
        }

        @Override
        public int compare(SoulDatabase.Soul o1, SoulDatabase.Soul o2) {
            return Integer.compare(distanceTo(o1), distanceTo(o2));
        }
    }
}
