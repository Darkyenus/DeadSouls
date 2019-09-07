package com.darkyen.minecraft;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import static com.darkyen.minecraft.Util.distance2;
import static com.darkyen.minecraft.Util.getTotalExperience;
import static com.darkyen.minecraft.Util.isNear;
import static com.darkyen.minecraft.Util.normalizeKey;
import static com.darkyen.minecraft.Util.parseColor;
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

    private static final String DEFAULT_SOUND_SOUL_COLLECT_XP = "entity.experience_orb.pickup";
    private String soundSoulCollectXp = DEFAULT_SOUND_SOUL_COLLECT_XP;
    private static final String DEFAULT_SOUND_SOUL_COLLECT_ITEM = "item.trident.return";
    private String soundSoulCollectItem = DEFAULT_SOUND_SOUL_COLLECT_ITEM;
    private static final String DEFAULT_SOUND_SOUL_DEPLETED = "entity.generic.extinguish_fire";
    private String soundSoulDepleted = DEFAULT_SOUND_SOUL_DEPLETED;
    private static final String DEFAULT_SOUND_SOUL_CALLING = "block.beacon.ambient";
    private String soundSoulCalling = DEFAULT_SOUND_SOUL_CALLING;
    private static final float DEFAULT_VOLUME_SOUL_CALLING = 16f;
    private float volumeSoulCalling = DEFAULT_VOLUME_SOUL_CALLING;
    private static final String DEFAULT_SOUND_SOUL_DROPPED = "block.bell.resonate";
    private String soundSoulDropped = DEFAULT_SOUND_SOUL_DROPPED;

    private static final String DEFAULT_TEXT_FREE_MY_SOUL = "Free my soul";
    private String textFreeMySoul = DEFAULT_TEXT_FREE_MY_SOUL;
    private static final String DEFAULT_TEXT_FREE_MY_SOUL_TOOLTIP = "Allows other players to collect the soul immediately";
    private String textFreeMySoulTooltip = DEFAULT_TEXT_FREE_MY_SOUL_TOOLTIP;

    private boolean soulFreeingEnabled = true;

    private boolean smartSoulPlacement = true;

    private PvPBehavior pvpBehavior = PvPBehavior.NORMAL;

    private static final Color DEFAULT_SOUL_DUST_COLOR_ITEMS = Color.WHITE;
    private static final float DEFAULT_SOUL_DUST_SIZE_ITEMS = 2f;
    private static final Color DEFAULT_SOUL_DUST_COLOR_XP = Color.AQUA;
    private static final float DEFAULT_SOUL_DUST_SIZE_XP = 2f;
    private static final Color DEFAULT_SOUL_DUST_COLOR_GONE = Color.YELLOW;
    private static final float DEFAULT_SOUL_DUST_SIZE_GONE = 3f;
    @NotNull
    private Particle.DustOptions soulDustOptionsItems = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_ITEMS, DEFAULT_SOUL_DUST_SIZE_ITEMS);
    @NotNull
    private Particle.DustOptions soulDustOptionsXp = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_XP, DEFAULT_SOUL_DUST_SIZE_XP);
    @NotNull
    private Particle.DustOptions soulDustOptionsGone = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_GONE, DEFAULT_SOUL_DUST_SIZE_GONE);

    private final HashMap<Player, PlayerSoulInfo> watchedPlayers = new HashMap<>();
    private boolean soulDatabaseChanged = false;

    private static final double COLLECTION_DISTANCE2 = NumberConversions.square(1);

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

        final boolean playCallingSounds = !soundSoulCalling.isEmpty() && volumeSoulCalling > 0f && this.processPlayers_random.nextInt(12) == 0;

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

            final World world = player.getWorld();

            {
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
                soulDatabase.findSouls(world, playerLocation.getBlockX(), playerLocation.getBlockZ(), 100, visibleSouls);
            }

            if (visibleSouls.isEmpty()) {
                continue;
            }

            { // Sort souls
                final ComparatorSoulDistanceTo comparator = this.processPlayers_comparatorDistanceTo;
                comparator.toX = playerLocation.getX();
                comparator.toY = playerLocation.getY();
                comparator.toZ = playerLocation.getZ();
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

                final Location soulLocation = soul.getLocation(player.getWorld());
                if (soulLocation == null) {
                    continue;
                }

                // Show this soul!
                if (soul.xp > 0 && soul.items.length > 0) {
                    player.spawnParticle(Particle.REDSTONE, soulLocation, 10, 0.1, 0.1, 0.1, soulDustOptionsItems);
                    player.spawnParticle(Particle.REDSTONE, soulLocation, 10, 0.12, 0.12, 0.12, soulDustOptionsXp);
                } else if (soul.xp > 0) {
                    // Only xp
                    player.spawnParticle(Particle.REDSTONE, soulLocation, 20, 0.1, 0.1, 0.1, soulDustOptionsXp);
                } else {
                    // Only items
                    player.spawnParticle(Particle.REDSTONE, soulLocation, 20, 0.1, 0.1, 0.1, soulDustOptionsItems);
                }
                remainingSoulsToShow--;
            }

            // Process collisions
            final GameMode gameMode = player.getGameMode();
            if (!player.isDead() && (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE)) {
                //noinspection ForLoopReplaceableByForEach
                for (int soulI = 0; soulI < visibleSouls.size(); soulI++) {
                    final SoulDatabase.Soul closestSoul = visibleSouls.get(soulI);
                    if (!closestSoul.isAccessibleBy(player, now, soulFreeAfterMs)) {
                        // Soul of somebody else, do not show nor collect
                        continue;
                    }

                    final double dst2 = distance2(closestSoul, playerLocation, 0.4);
                    final Location closestSoulLocation = closestSoul.getLocation(player.getWorld());

                    if (dst2 < COLLECTION_DISTANCE2) {
                        // Collect it!
                        if (closestSoul.xp > 0) {
                            player.giveExp(closestSoul.xp);
                            closestSoul.xp = 0;
                            if (!soundSoulCollectXp.isEmpty() && closestSoulLocation != null) {
                                player.playSound(closestSoulLocation, soundSoulCollectXp, 1f, 1f);
                            }
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

                            if (someCollected && !soundSoulCollectItem.isEmpty() && closestSoulLocation != null) {
                                player.playSound(closestSoulLocation, soundSoulCollectItem, 1f, 0.5f);
                            }
                        }

                        if (closestSoul.xp <= 0 && closestSoul.items.length <= 0) {
                            // Soul is depleted
                            soulDatabase.removeSoul(closestSoul);
                            this.soulDatabaseChanged = true;

                            // Do some fancy effect
                            if (closestSoulLocation != null) {
                                if (!soundSoulDepleted.isEmpty()) {
                                    player.playSound(closestSoulLocation, soundSoulDepleted, 0.1f, 0.5f);
                                }
                                player.spawnParticle(Particle.REDSTONE, closestSoulLocation, 20, 0.2, 0.2, 0.2, soulDustOptionsGone);
                            }
                        }
                    } else if (playCallingSounds && closestSoulLocation != null) {
                        player.playSound(closestSoulLocation, soundSoulCalling, volumeSoulCalling, 0.75f);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onEnable() {
        final FileConfiguration config = getConfig();
        soulFreeAfterMs = parseTimeMs(config.getString("soul-free-after"), Long.MAX_VALUE, getLogger());
        soulFadesAfterMs = parseTimeMs(config.getString("soul-fades-after"), Long.MAX_VALUE, getLogger());

        {
            this.retainedXPPercent = 90;
            this.retainedXPPerLevel = 0;
            final String retainedXp = config.getString("retained-xp");
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

        soundSoulCollectXp = normalizeKey(config.getString("sound-soul-collect-xp", DEFAULT_SOUND_SOUL_COLLECT_XP));
        soundSoulCollectItem = normalizeKey(config.getString("sound-soul-collect-item", DEFAULT_SOUND_SOUL_COLLECT_ITEM));
        soundSoulDepleted = normalizeKey(config.getString("sound-soul-depleted", DEFAULT_SOUND_SOUL_DEPLETED));
        soundSoulCalling = normalizeKey(config.getString("sound-soul-calling", DEFAULT_SOUND_SOUL_CALLING));
        soundSoulDropped = normalizeKey(config.getString("sound-soul-dropped", DEFAULT_SOUND_SOUL_DROPPED));
        volumeSoulCalling = (float)config.getDouble("volume-soul-calling", DEFAULT_VOLUME_SOUL_CALLING);

        soulDustOptionsItems = new Particle.DustOptions(parseColor(config.getString("color-soul-items"), DEFAULT_SOUL_DUST_COLOR_ITEMS, getLogger()), DEFAULT_SOUL_DUST_SIZE_ITEMS);
        soulDustOptionsXp = new Particle.DustOptions(parseColor(config.getString("color-soul-xp"), DEFAULT_SOUL_DUST_COLOR_XP, getLogger()), DEFAULT_SOUL_DUST_SIZE_XP);
        soulDustOptionsGone = new Particle.DustOptions(parseColor(config.getString("color-soul-gone"), DEFAULT_SOUL_DUST_COLOR_GONE, getLogger()), DEFAULT_SOUL_DUST_SIZE_GONE);

        textFreeMySoul = config.getString("text-free-my-soul", DEFAULT_TEXT_FREE_MY_SOUL);
        textFreeMySoulTooltip = config.getString("text-free-my-soul-tooltip", DEFAULT_TEXT_FREE_MY_SOUL_TOOLTIP);
        soulFreeingEnabled = textFreeMySoul != null && !textFreeMySoul.isEmpty();

        {
            String pvpBehaviorString = config.getString("pvp-behavior");
            if (pvpBehaviorString == null) {
                pvpBehavior = PvPBehavior.NORMAL;
            } else {
                try {
                    pvpBehavior = PvPBehavior.valueOf(pvpBehaviorString.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    pvpBehavior = PvPBehavior.NORMAL;
                    final StringBuilder sb = new StringBuilder(128);
                    sb.append("Unrecognized pvp-behavior: '").append(pvpBehaviorString).append("'. ");
                    sb.append("Allowed values are: ");
                    for (PvPBehavior value : PvPBehavior.values()) {
                        sb.append(value.name().toLowerCase()).append(", ");
                    }
                    sb.setLength(sb.length() - 2);
                    getLogger().log(Level.WARNING, sb.toString());
                }
            }
        }

        smartSoulPlacement = config.getBoolean("smart-soul-placement", true);

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
            final Path dataFolder = getDataFolder().toPath();
            final Path soulDb = dataFolder.resolve("soul-db.bin");
            soulDatabase = new SoulDatabase(this, soulDb);

            final Path legacySoulDb = dataFolder.resolve("souldb.bin");
            if (Files.exists(legacySoulDb)) {
                soulDatabase.loadLegacy(legacySoulDb);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load soul database", e);
        }
        soulDatabaseChanged = true;

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
            if (!soulFreeingEnabled) {
                return true;
            }

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

        final boolean pvp = player.getKiller() != null && !player.equals(player.getKiller());
        if (pvp && pvpBehavior == PvPBehavior.DISABLED) {
            return;
        }

        final ItemStack[] soulItems;
        if (event.getKeepInventory() || !player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul.items")) {
            // We don't modify drops for this death at all
            soulItems = NO_ITEM_STACKS;
        } else {
            final List<ItemStack> drops = event.getDrops();
            soulItems = drops.toArray(NO_ITEM_STACKS);
            drops.clear();
        }

        int soulXp;
        if (event.getKeepLevel() || !player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul.xp")) {
            // We don't modify XP for this death at all
            soulXp = 0;
        } else {
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
        }

        if (soulXp == 0 && soulItems.length == 0) {
            // Soul would be empty
            return;
        }

        Location soulLocation;
        try {
            if (smartSoulPlacement) {
                PlayerSoulInfo info = watchedPlayers.get(player);
                if (info == null) {
                    getLogger().log(Level.WARNING, "Player " + player + " was not watched!");
                    info = new PlayerSoulInfo();
                    watchedPlayers.put(player, info);
                }
                soulLocation = info.findSafeSoulSpawnLocation(player);
                info.lastSafeLocation.setWorld(null); // Reset it, so it isn't used twice
            } else {
                soulLocation = PlayerSoulInfo.findFallbackSoulSpawnLocation(player, player.getLocation(), false);
            }
        } catch (Exception bugException) {
            // Should never happen, but just in case!
            getLogger().log(Level.SEVERE, "Failed to find soul location, defaulting to player location!", bugException);
            soulLocation = player.getLocation();
        }

        final UUID owner;
        if ((pvp && pvpBehavior == PvPBehavior.FREE) || soulFreeAfterMs <= 0) {
            owner = null;
        } else {
            owner = player.getUniqueId();
        }

        final int soulId = soulDatabase.addSoul(owner, player.getWorld().getUID(),
                soulLocation.getX(), soulLocation.getY(), soulLocation.getZ(), soulItems, soulXp);
        soulDatabaseChanged = true;

        // Do not offer to free the soul if it will be free sooner than the player can click the button
        if (owner != null && soulFreeAfterMs > 1000 && soulFreeingEnabled && textFreeMySoul != null && !textFreeMySoul.isEmpty()) {
            final TextComponent star = new TextComponent("✦");
            star.setColor(ChatColor.YELLOW);
            final TextComponent freeMySoul = new TextComponent(" "+textFreeMySoul+" ");
            freeMySoul.setColor(ChatColor.GOLD);
            freeMySoul.setBold(true);
            freeMySoul.setUnderlined(true);
            if (textFreeMySoulTooltip != null && !textFreeMySoulTooltip.isEmpty()) {
                freeMySoul.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new BaseComponent[]{new TextComponent(textFreeMySoulTooltip)}));
            }
            freeMySoul.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dead_souls_free_soul " + soulId));
            player.spigot().sendMessage(ChatMessageType.CHAT, star, freeMySoul, star);
        }

        if (!soundSoulDropped.isEmpty()) {
            player.getWorld().playSound(soulLocation, soundSoulDropped, SoundCategory.MASTER, 1.1f, 1.7f);
        }
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
        static final double SOUL_HOVER_OFFSET = 1.2;

        @NotNull
        final Location lastKnownLocation = new Location(null, 0, 0, 0);

        @NotNull
        final Location lastSafeLocation = new Location(null, 0, 0, 0);

        final ArrayList<SoulDatabase.Soul> visibleSouls = new ArrayList<>();

        Location findSafeSoulSpawnLocation(Player player) {
            final Location playerLocation = player.getLocation();
            if (isNear(lastSafeLocation, playerLocation, 20)) {
                set(playerLocation, lastSafeLocation);
                playerLocation.setY(playerLocation.getY() + SOUL_HOVER_OFFSET);
                return playerLocation;
            }

            // Too far, now we have to find a better location
            return findFallbackSoulSpawnLocation(player, playerLocation, true);
        }

        static Location findFallbackSoulSpawnLocation(Player player, Location playerLocation, boolean improve) {
            final World world = player.getWorld();

            final int x = playerLocation.getBlockX();
            int y = Util.clamp(playerLocation.getBlockY(), 0, world.getMaxHeight());
            final int z = playerLocation.getBlockZ();

            if (improve) {
                int yOff = 0;
                while (true) {
                    final Material type = world.getBlockAt(x, y + yOff, z).getType();
                    if (type.isSolid()) {
                        // Soul either started in a block or ended in it, do not want
                        yOff = 0;
                        break;
                    } else if (type == Material.LAVA) {
                        yOff++;

                        if (yOff > 8) {
                            // Probably dead in a lava column, we don't want to push it up
                            yOff = 0;
                            break;
                        }
                        // continue
                    } else {
                        // This place looks good
                        break;
                    }
                }

                y += yOff;
            }

            playerLocation.setY(y + SOUL_HOVER_OFFSET);
            return playerLocation;
        }
    }

    private static final class ComparatorSoulDistanceTo implements Comparator<SoulDatabase.Soul> {

        double toX, toY, toZ;

        private double distanceTo(SoulDatabase.Soul s) {
            final double x = toX - s.locationX;
            final double y = toY - s.locationY;
            final double z = toZ - s.locationZ;
            return x*x + y*y + z*z;
        }

        @Override
        public int compare(SoulDatabase.Soul o1, SoulDatabase.Soul o2) {
            return Double.compare(distanceTo(o1), distanceTo(o2));
        }
    }

    private enum PvPBehavior {
        /** no change */
        NORMAL,
        /** souls are not created in PvP, items and XP drops like in vanilla Minecraft */
        DISABLED,
        /** created souls are immediately free and can be collected by anyone */
        FREE
    }
}
