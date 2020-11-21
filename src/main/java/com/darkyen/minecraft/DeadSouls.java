package com.darkyen.minecraft;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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

    @Nullable
    private SoulDatabase soulDatabase;
    private long soulFreeAfterMs = Long.MAX_VALUE;
    private long soulFadesAfterMs = Long.MAX_VALUE;

    private long autoSaveMs = 0L;

    private float retainedXPPercent;
    private int retainedXPPerLevel;

    @NotNull
    private static final String DEFAULT_SOUND_SOUL_COLLECT_XP = "entity.experience_orb.pickup";
    @NotNull
    private String soundSoulCollectXp = DEFAULT_SOUND_SOUL_COLLECT_XP;
    @NotNull
    private static final String DEFAULT_SOUND_SOUL_COLLECT_ITEM = "item.trident.return";
    @NotNull
    private String soundSoulCollectItem = DEFAULT_SOUND_SOUL_COLLECT_ITEM;
    @NotNull
    private static final String DEFAULT_SOUND_SOUL_DEPLETED = "entity.generic.extinguish_fire";
    @NotNull
    private String soundSoulDepleted = DEFAULT_SOUND_SOUL_DEPLETED;
    @NotNull
    private static final String DEFAULT_SOUND_SOUL_CALLING = "block.beacon.ambient";
    @NotNull
    private String soundSoulCalling = DEFAULT_SOUND_SOUL_CALLING;
    private static final float DEFAULT_VOLUME_SOUL_CALLING = 16f;
    private float volumeSoulCalling = DEFAULT_VOLUME_SOUL_CALLING;
    @NotNull
    private static final String DEFAULT_SOUND_SOUL_DROPPED = "block.bell.resonate";
    @NotNull
    private String soundSoulDropped = DEFAULT_SOUND_SOUL_DROPPED;

    @NotNull
    private static final String DEFAULT_TEXT_FREE_MY_SOUL = "Free my soul";
    @Nullable
    private String textFreeMySoul = DEFAULT_TEXT_FREE_MY_SOUL;
    @NotNull
    private static final String DEFAULT_TEXT_FREE_MY_SOUL_TOOLTIP = "Allows other players to collect the soul immediately";
    @Nullable
    private String textFreeMySoulTooltip = DEFAULT_TEXT_FREE_MY_SOUL_TOOLTIP;

    private boolean soulFreeingEnabled = true;

    private boolean smartSoulPlacement = true;

    @NotNull
    private PvPBehavior pvpBehavior = PvPBehavior.NORMAL;

    @NotNull
    private static final Color DEFAULT_SOUL_DUST_COLOR_ITEMS = Color.WHITE;
    private static final float DEFAULT_SOUL_DUST_SIZE_ITEMS = 2f;
    @NotNull
    private static final Color DEFAULT_SOUL_DUST_COLOR_XP = Color.AQUA;
    private static final float DEFAULT_SOUL_DUST_SIZE_XP = 2f;
    @NotNull
    private static final Color DEFAULT_SOUL_DUST_COLOR_GONE = Color.YELLOW;
    private static final float DEFAULT_SOUL_DUST_SIZE_GONE = 3f;
    @NotNull
    private Particle.DustOptions soulDustOptionsItems = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_ITEMS, DEFAULT_SOUL_DUST_SIZE_ITEMS);
    @NotNull
    private Particle.DustOptions soulDustOptionsXp = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_XP, DEFAULT_SOUL_DUST_SIZE_XP);
    @NotNull
    private Particle.DustOptions soulDustOptionsGone = new Particle.DustOptions(DEFAULT_SOUL_DUST_COLOR_GONE, DEFAULT_SOUL_DUST_SIZE_GONE);

    private final EnumSet<EntityType> animalsWithSouls = EnumSet.noneOf(EntityType.class);

    private final ArrayList<Pattern> worldPatterns = new ArrayList<>();
    private final HashSet<UUID> enabledWorlds = new HashSet<>();

    @NotNull
    private final HashMap<Player, PlayerSoulInfo> watchedPlayers = new HashMap<>();
    private boolean refreshNearbySoulCache = false;

    private static final double COLLECTION_DISTANCE2 = NumberConversions.square(1);

    @NotNull
    private static final ItemStack[] NO_ITEM_STACKS = new ItemStack[0];
    @NotNull
    private final ComparatorSoulDistanceTo processPlayers_comparatorDistanceTo = new ComparatorSoulDistanceTo();
    @NotNull
    private final Location processPlayers_playerLocation = new Location(null, 0, 0, 0);
    @NotNull
    private final Random processPlayers_random = new Random();

    private long processPlayers_nextFadeCheck = 0;
    private long processPlayers_nextAutoSave = 0;

    private void processPlayers() {
        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase == null) {
            getLogger().log(Level.WARNING, "processPlayers: soulDatabase not loaded yet");
            return;
        }

        final long now = System.currentTimeMillis();

        if (now > processPlayers_nextFadeCheck && soulFadesAfterMs < Long.MAX_VALUE) {
            final int faded = soulDatabase.removeFadedSouls(soulFadesAfterMs);
            if (faded > 0) {
                this.refreshNearbySoulCache = true;
                getLogger().log(Level.FINE, "Removed "+faded+" faded soul(s)");
            }
            processPlayers_nextFadeCheck = now + 1000 * 60 * 5;// Check every 5 minutes
        }

        final boolean refreshNearbySoulCache = this.refreshNearbySoulCache;
        this.refreshNearbySoulCache = false;

        final boolean playCallingSounds = !soundSoulCalling.isEmpty() && volumeSoulCalling > 0f && this.processPlayers_random.nextInt(12) == 0;

        boolean databaseChanged = false;

        for (Map.Entry<Player, PlayerSoulInfo> entry : watchedPlayers.entrySet()) {
            final Player player = entry.getKey();
            final GameMode playerGameMode = player.getGameMode();
            final World world = player.getWorld();
            final PlayerSoulInfo info = entry.getValue();

            boolean searchNewSouls = refreshNearbySoulCache;

            // Update location
            final Location playerLocation = player.getLocation(processPlayers_playerLocation);
            if (!isNear(playerLocation, info.lastKnownLocation, 16)) {
                set(info.lastKnownLocation, playerLocation);
                searchNewSouls = true;
            }

            if (playerGameMode != GameMode.SPECTATOR) {
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
            final boolean canSeeAllSouls = playerGameMode == GameMode.SPECTATOR && player.hasPermission("com.darkyen.minecraft.deadsouls.spectatesouls");
            for (int i = 0; i < soulCount && remainingSoulsToShow > 0; i++) {
                final SoulDatabase.Soul soul = visibleSouls.get(i);
                if (!canSeeAllSouls && !soul.isAccessibleBy(player, now, soulFreeAfterMs)) {
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
            if (!player.isDead() && (playerGameMode == GameMode.SURVIVAL || playerGameMode == GameMode.ADVENTURE)) {
                //noinspection ForLoopReplaceableByForEach
                for (int soulI = 0; soulI < visibleSouls.size(); soulI++) {
                    final SoulDatabase.Soul closestSoul = visibleSouls.get(soulI);
                    if (!closestSoul.isAccessibleBy(player, now, soulFreeAfterMs)) {
                        // Soul of somebody else, do not collect
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
                            databaseChanged = true;
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
                                databaseChanged = true;
                            } else {
                                for (Map.Entry<Integer, ItemStack> overflowEntry : overflow.entrySet()) {
                                    if (!items[overflowEntry.getKey()].equals(overflowEntry.getValue())) {
                                        someCollected = true;
                                        databaseChanged = true;
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
                            this.refreshNearbySoulCache = true;

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

        if (databaseChanged) {
            soulDatabase.markDirty();
        }

        final long autoSaveMs = this.autoSaveMs;
        if (now > processPlayers_nextAutoSave) {
            processPlayers_nextAutoSave = now + autoSaveMs;
            soulDatabase.autoSave();
        }
    }

    @Override
    public void onEnable() {
        final FileConfiguration config = getConfig();
        final Logger LOG = getLogger();
        soulFreeAfterMs = parseTimeMs(config.getString("soul-free-after"), Long.MAX_VALUE, LOG);
        soulFadesAfterMs = parseTimeMs(config.getString("soul-fades-after"), Long.MAX_VALUE, LOG);
        autoSaveMs = parseTimeMs(config.getString("auto-save"), 0L, LOG);

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
                            LOG.log(Level.WARNING, "Invalid configuration: retained-xp percent must be between 0 and 1");
                        } else {
                            retainedXPPercent = number / 100f;
                            retainedXPPerLevel = 0;
                        }
                    } else {
                        if (number < 0) {
                            LOG.log(Level.WARNING, "Invalid configuration: retained-xp per level must be positive");
                        } else {
                            retainedXPPercent = -1f;
                            retainedXPPerLevel = number;
                        }
                    }
                } catch (NumberFormatException nfe) {
                    LOG.log(Level.WARNING, "Invalid configuration: retained-xp has invalid format");
                }
            }
        }

        soundSoulCollectXp = normalizeKey(config.getString("sound-soul-collect-xp", DEFAULT_SOUND_SOUL_COLLECT_XP));
        soundSoulCollectItem = normalizeKey(config.getString("sound-soul-collect-item", DEFAULT_SOUND_SOUL_COLLECT_ITEM));
        soundSoulDepleted = normalizeKey(config.getString("sound-soul-depleted", DEFAULT_SOUND_SOUL_DEPLETED));
        soundSoulCalling = normalizeKey(config.getString("sound-soul-calling", DEFAULT_SOUND_SOUL_CALLING));
        soundSoulDropped = normalizeKey(config.getString("sound-soul-dropped", DEFAULT_SOUND_SOUL_DROPPED));
        volumeSoulCalling = (float)config.getDouble("volume-soul-calling", DEFAULT_VOLUME_SOUL_CALLING);

        soulDustOptionsItems = new Particle.DustOptions(parseColor(config.getString("color-soul-items"), DEFAULT_SOUL_DUST_COLOR_ITEMS, LOG), DEFAULT_SOUL_DUST_SIZE_ITEMS);
        soulDustOptionsXp = new Particle.DustOptions(parseColor(config.getString("color-soul-xp"), DEFAULT_SOUL_DUST_COLOR_XP, LOG), DEFAULT_SOUL_DUST_SIZE_XP);
        soulDustOptionsGone = new Particle.DustOptions(parseColor(config.getString("color-soul-gone"), DEFAULT_SOUL_DUST_COLOR_GONE, LOG), DEFAULT_SOUL_DUST_SIZE_GONE);

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
                    LOG.log(Level.WARNING, sb.toString());
                }
            }
        }

        smartSoulPlacement = config.getBoolean("smart-soul-placement", true);

        animalsWithSouls.clear();
        for (String animalName : config.getStringList("animals-with-souls")) {
            final EntityType entityType;
            try {
                entityType = EntityType.valueOf(animalName);
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Ignoring animal type for soul \""+animalName+"\", no such entity name");
                continue;
            }
            animalsWithSouls.add(entityType);
        }

        worldPatterns.clear();
        for (String worlds : config.getStringList("worlds")) {
            worldPatterns.add(Util.compileSimpleGlob(worlds));
        }
        if (worldPatterns.isEmpty()) {
            LOG.warning("No world patterns specified, souls will not be created anywhere.");
        }

        refreshEnabledWorlds();

        saveDefaultConfig();

        final Server server = getServer();
        server.getPluginManager().registerEvents(this, this);

        // Run included tests
        for (String testClassName : new String[]{"com.darkyen.minecraft.ItemStoreTest"}) {
            try {
                final Class<?> testClass;
                try {
                    testClass = Class.forName(testClassName);
                } catch (ClassNotFoundException e) {
                    continue;
                }
                LOG.info("Found test class: " + testClassName);

                final Method runLiveTest = testClass.getMethod("runLiveTest", Plugin.class);
                runLiveTest.invoke(null, this);

                LOG.info("Test successful");
            } catch (Exception e) {
                LOG.log(Level.INFO, "Failed to run tests of " + testClassName, e);
            }
        }

        {
            final Path dataFolder = getDataFolder().toPath();
            final Path soulDb = dataFolder.resolve("soul-db.bin");
            soulDatabase = new SoulDatabase(this, soulDb);

            final Path legacySoulDb = dataFolder.resolve("souldb.bin");
            if (Files.exists(legacySoulDb)) {
                try {
                    soulDatabase.loadLegacy(legacySoulDb);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed to load legacy soul database, old souls will not be present", e);
                }
            }
        }

        refreshNearbySoulCache = true;

        for (Player onlinePlayer : server.getOnlinePlayers()) {
            watchedPlayers.put(onlinePlayer, new PlayerSoulInfo());
        }

        server.getScheduler().runTaskTimer(this, this::processPlayers, 20, 20);
    }

    private void refreshEnabledWorlds() {
        final HashSet<UUID> worlds = this.enabledWorlds;
        worlds.clear();

        for (World world : getServer().getWorlds()) {
            final String name = world.getName();
            final UUID uuid = world.getUID();
            final String uuidString = uuid.toString();
            for (Pattern pattern : this.worldPatterns) {
                if (pattern.matcher(name).matches() || pattern.matcher(uuidString).matches()) {
                    worlds.add(uuid);
                }
            }
        }

        if (!worldPatterns.isEmpty() && worlds.isEmpty()) {
            getLogger().warning("No worlds match, souls will not be created in any world.");
        }
    }

    @Override
    public void onDisable() {
        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase != null) {
            try {
                final int faded = soulDatabase.removeFadedSouls(soulFadesAfterMs);
                if (faded > 0) {
                    getLogger().log(Level.FINE, "Removed "+faded+" faded soul(s)");
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
        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase == null) {
            getLogger().log(Level.WARNING, "processPlayers: soulDatabase not loaded yet");
            return false;
        }

        if (!"souls".equalsIgnoreCase(command.getName())) {
            return false;
        }

        final String word = args.length >= 1 ? args[0] : "";
        int number;
        try {
            number = args.length >= 2 ? Integer.parseInt(args[1]) : -1;
        } catch (NumberFormatException nfe) {
            number = -1;
        }

        if ("free".equalsIgnoreCase(word)) {
            if (!soulFreeingEnabled) {
                sender.sendMessage(org.bukkit.ChatColor.RED+"This world does not understand the concept of freeing");
                return true;
            }

            soulDatabase.freeSoul(sender, number, soulFreeAfterMs,
                    sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.free"),
                    sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.free.all"));
            return true;
        }

        if ("goto".equalsIgnoreCase(word)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(org.bukkit.ChatColor.RED+"This sub-command is only accessible in-game");
                return true;
            }

            final SoulDatabase.Soul soul = soulDatabase.getSoulById(number);
            if (soul == null) {
                sender.sendMessage(org.bukkit.ChatColor.RED+"This soul does not exist");
                return true;
            }

            if (!sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.goto.all")) {
                if (soul.isOwnedBy(sender)) {
                    if (!sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.goto")) {
                        sender.sendMessage(org.bukkit.ChatColor.RED+"You are not allowed to do that");
                        return true;
                    }
                } else {
                    sender.sendMessage(org.bukkit.ChatColor.RED+"You are not allowed to do that");
                    return true;
                }
            }

            final World world = getServer().getWorld(soul.locationWorld);
            if (world == null) {
                sender.sendMessage(org.bukkit.ChatColor.RED+"The soul is not in any world");
                return true;
            }

            ((Player)sender).teleport(new Location(world, soul.locationX, soul.locationY, soul.locationZ), PlayerTeleportEvent.TeleportCause.COMMAND);
            sender.sendMessage(org.bukkit.ChatColor.AQUA+"Teleported");
            return true;
        }

        if ("reload".equalsIgnoreCase(word) && sender.isOp()) {
            sender.sendMessage(org.bukkit.ChatColor.RED+"----------------------------");
            sender.sendMessage(org.bukkit.ChatColor.RED+"Reloading plugin Dead Souls");
            sender.sendMessage(org.bukkit.ChatColor.RED+"RELOAD FUNCTIONALITY IS ONLY FOR TESTING AND EXPERIMENTING AND SHOULD NEVER BE USED ON A LIVE SERVER!!!");
            sender.sendMessage(org.bukkit.ChatColor.RED+"If you encounter any problems with the plugin after the reload, restart the server!");
            sender.sendMessage(org.bukkit.ChatColor.RED+"----------------------------");

            final Server server = getServer();
            server.getPluginManager().disablePlugin(this);
            reloadConfig();
            server.getPluginManager().enablePlugin(this);

            sender.sendMessage(org.bukkit.ChatColor.RED+" - Reload done - ");
            return true;
        }

        boolean listOwnSouls = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls");
        boolean listAllSouls = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.all");

        if (!listOwnSouls && !listAllSouls) {
            return false;
        }

        if (word.isEmpty()) {
            if (number < 0) {
                number = 0;
            }
        } else if (!"page".equalsIgnoreCase(word)) {
            return false;
        }

        final UUID senderUUID = (sender instanceof OfflinePlayer) ? ((OfflinePlayer) sender).getUniqueId() : null;

        if (!(sender instanceof Player)) {
            // Console output
            final List<SoulDatabase.@Nullable Soul> soulsById = soulDatabase.getSoulsById();
            int shownSouls = 0;
            synchronized (soulsById) {
                for (int id = 0; id < soulsById.size(); id++) {
                    final SoulDatabase.Soul soul = soulsById.get(id);
                    if (soul == null) {
                        continue;
                    }
                    shownSouls++;

                    final World world = getServer().getWorld(soul.locationWorld);
                    final String worldStr = world == null ? soul.locationWorld.toString() : world.getName();

                    final String ownerStr;
                    if (soul.owner == null) {
                        ownerStr = "<free>";
                    } else {
                        final OfflinePlayer ownerPlayer = getServer().getOfflinePlayer(soul.owner);
                        final String ownerPlayerName = ownerPlayer.getName();
                        if (ownerPlayerName == null) {
                            ownerStr = soul.owner.toString();
                        } else {
                            ownerStr = ownerPlayerName;
                        }
                    }

                    sender.sendMessage(String.format("%d) %s %.1f %.1f %.1f   %s", id, worldStr, soul.locationX, soul.locationY, soul.locationZ, ownerStr));
                }
            }
            sender.sendMessage(shownSouls+" souls");
        } else {
            // Normal player output
            final List<SoulDatabase.@NotNull SoulAndId> souls = soulDatabase.getSoulsByOwnerAndWorld(listAllSouls ? null : senderUUID, ((Player) sender).getWorld().getUID());
            final Location location = ((Player) sender).getLocation();
            souls.sort(Comparator.comparingLong(soulAndId -> -soulAndId.soul.timestamp));

            final boolean canFree = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.free");
            final boolean canFreeAll = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.free.all");
            final boolean canGoto = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.goto");
            final boolean canGotoAll = sender.hasPermission("com.darkyen.minecraft.deadsouls.souls.goto.all");

            final int soulsPerPage = 6;
            final long now = System.currentTimeMillis();
            for (int i = Math.max(soulsPerPage * number, 0), end = Math.min(i + soulsPerPage, souls.size()); i < end; i++) {
                final SoulDatabase.SoulAndId soulAndId = souls.get(i);
                final SoulDatabase.Soul soul = soulAndId.soul;
                final float distance = (float) Math.sqrt(distance2(soul, location, 1));

                final TextComponent baseText = new TextComponent((i+1)+" ");
                baseText.setColor(ChatColor.AQUA);
                baseText.setBold(true);

                long minutesOld = TimeUnit.MILLISECONDS.toMinutes(now - soul.timestamp);
                if (minutesOld >= 0) {
                    String age;
                    if (minutesOld <= 1) {
                        age = " Fresh";
                    } else if (minutesOld < 60 * 2) {
                        age = " " + minutesOld + " minutes old";
                    } else if (minutesOld < 24 * 60) {
                        age = " " + (minutesOld / 60) + " hours old";
                    } else if (minutesOld < 24 * 60 * 100) {
                        age = " " + (minutesOld / (24 * 60)) + " days old";
                    } else {
                        age = " Ancient";
                    }

                    final TextComponent ageText = new TextComponent(age);
                    ageText.setColor(ChatColor.WHITE);
                    ageText.setItalic(true);
                    baseText.addExtra(ageText);
                }

                if (sender.hasPermission("com.darkyen.minecraft.deadsouls.coordinates")) {
                    final TextComponent coords = new TextComponent(String.format(" %d / %d / %d", Math.round(soul.locationX), Math.round(soul.locationY), Math.round(soul.locationZ)));
                    coords.setColor(ChatColor.GRAY);
                    baseText.addExtra(coords);
                }

                if (sender.hasPermission("com.darkyen.minecraft.deadsouls.distance")) {
                    final TextComponent dist = new TextComponent(String.format(" %.1f m", distance));
                    dist.setColor(ChatColor.AQUA);
                    baseText.addExtra(dist);
                }


                final boolean ownSoul = soul.isOwnedBy(sender);

                if (soul.owner != null && (canFreeAll || (ownSoul && canFree))) {
                    final TextComponent freeButton = new TextComponent("Free");
                    freeButton.setColor(ChatColor.GREEN);
                    freeButton.setBold(true);
                    freeButton.setUnderlined(true);
                    freeButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls free "+soulAndId.id));
                    baseText.addExtra("  ");
                    baseText.addExtra(freeButton);
                }

                if (canGotoAll || (ownSoul && canGoto)) {
                    final TextComponent gotoButton = new TextComponent("Teleport");
                    gotoButton.setColor(ChatColor.GOLD);
                    gotoButton.setBold(true);
                    gotoButton.setUnderlined(true);
                    gotoButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls goto "+soulAndId.id));
                    baseText.addExtra("  ");
                    baseText.addExtra(gotoButton);
                }

                sender.spigot().sendMessage(baseText);
            }

            final boolean leftArrow = number > 0;
            final int pages = (souls.size() + soulsPerPage - 1) / soulsPerPage;
            final boolean rightArrow = number + 1 < pages;

            final TextComponent arrows = new TextComponent();
            final TextComponent left = new TextComponent(leftArrow ? "<<" : "  ");
            left.setColor(ChatColor.GRAY);
            if (leftArrow) {
                left.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls page " + (number - 1)));
            }
            arrows.addExtra(left);

            arrows.addExtra(" page "+(number + 1)+"/"+pages+" ");

            final TextComponent right = new TextComponent(rightArrow ? ">>" : "  ");
            right.setColor(ChatColor.GRAY);
            if (rightArrow) {
                right.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls page "+(number + 1)));
            }
            arrows.addExtra(right);
            arrows.addExtra(" ("+souls.size()+" souls total)");

            sender.spigot().sendMessage(arrows);
        }

        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWorldLoaded(WorldLoadEvent event) {
        refreshEnabledWorlds();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        if (!player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul")) {
            return;
        }

        final World world = player.getWorld();
        if (!enabledWorlds.contains(world.getUID())) {
            return;
        }

        final boolean pvp = player.getKiller() != null && !player.equals(player.getKiller());
        if (pvp && pvpBehavior == PvPBehavior.DISABLED) {
            return;
        }

        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase == null) {
            getLogger().log(Level.WARNING, "onPlayerDeath: soulDatabase not loaded yet");
            return;
        }

        // Actually clearing the drops is deferred to the end of the method:
        // in case of any bug that causes this method to crash, we don't want to just delete the items
        boolean clearItemDrops = false;
        boolean clearXPDrops = false;

        final ItemStack[] soulItems;
        if (event.getKeepInventory() || !player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul.items")) {
            // We don't modify drops for this death at all
            soulItems = NO_ITEM_STACKS;
        } else {
            final List<ItemStack> drops = event.getDrops();
            soulItems = drops.toArray(NO_ITEM_STACKS);
            clearItemDrops = true;
        }

        int soulXp;
        if (event.getKeepLevel() || !player.hasPermission("com.darkyen.minecraft.deadsouls.hassoul.xp")
                // Required because getKeepLevel is not set when world's KEEP_INVENTORY is set, but it has the same effect
                // See https://hub.spigotmc.org/jira/browse/SPIGOT-2222
                || Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY))) {
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
            clearXPDrops = true;
        }

        if (soulXp == 0 && soulItems.length == 0) {
            // Soul would be empty
            return;
        }

        Location soulLocation = null;
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
        }
        if (soulLocation == null) {
            soulLocation = player.getLocation();
        }

        final UUID owner;
        if ((pvp && pvpBehavior == PvPBehavior.FREE) || soulFreeAfterMs <= 0) {
            owner = null;
        } else {
            owner = player.getUniqueId();
        }

        final int soulId = soulDatabase.addSoul(owner, world.getUID(),
                soulLocation.getX(), soulLocation.getY(), soulLocation.getZ(), soulItems, soulXp);
        refreshNearbySoulCache = true;

        // Show coordinates if the player has poor taste
        if (player.hasPermission("com.darkyen.minecraft.deadsouls.coordinates")) {
            final TextComponent skull = new TextComponent("☠");
            skull.setColor(ChatColor.BLACK);
            final TextComponent coords = new TextComponent(String.format(" %d / %d / %d ", Math.round(soulLocation.getX()), Math.round(soulLocation.getY()), Math.round(soulLocation.getZ())));
            coords.setColor(ChatColor.GRAY);
            player.spigot().sendMessage(skull, coords, skull);
        }

        // Do not offer to free the soul if it will be free sooner than the player can click the button
        if (owner != null && soulFreeAfterMs > 1000
                && soulFreeingEnabled && textFreeMySoul != null && !textFreeMySoul.isEmpty()
                && (player.hasPermission("com.darkyen.minecraft.deadsouls.souls.free")
                    || player.hasPermission("com.darkyen.minecraft.deadsouls.souls.free.all"))) {
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
            freeMySoul.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/souls free " + soulId));
            player.spigot().sendMessage(ChatMessageType.CHAT, star, freeMySoul, star);
        }

        if (!soundSoulDropped.isEmpty()) {
            world.playSound(soulLocation, soundSoulDropped, SoundCategory.MASTER, 1.1f, 1.7f);
        }

        // No need to set setKeepInventory/Level to false, because if we got here, it already is false
        if (clearItemDrops) {
            event.getDrops().clear();
        }
        if (clearXPDrops) {
            event.setNewExp(0);
            event.setNewLevel(0);
            event.setNewTotalExp(0);
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        final LivingEntity entity = event.getEntity();

        if (entity instanceof Player || !animalsWithSouls.contains(entity.getType())) {
            return;
        }

        final ItemStack[] soulItems = event.getDrops().toArray(NO_ITEM_STACKS);
        final int soulXp = event.getDroppedExp();

        if (soulXp == 0 && soulItems.length == 0) {
            // Soul would be empty
            return;
        }

        final SoulDatabase soulDatabase = this.soulDatabase;
        if (soulDatabase == null) {
            getLogger().log(Level.WARNING, "onEntityDeath: soulDatabase not loaded yet");
            return;
        }

        final Location soulLocation = entity.getLocation();

        final World world = entity.getWorld();
        soulDatabase.addSoul(null, world.getUID(), soulLocation.getX(), soulLocation.getY(), soulLocation.getZ(), soulItems, soulXp);
        refreshNearbySoulCache = true;

        if (!soundSoulDropped.isEmpty()) {
            world.playSound(soulLocation, soundSoulDropped, SoundCategory.MASTER, 1.1f, 1.7f);
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
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

        @NotNull
        Location findSafeSoulSpawnLocation(@NotNull Player player) {
            final Location playerLocation = player.getLocation();
            if (isNear(lastSafeLocation, playerLocation, 20)) {
                set(playerLocation, lastSafeLocation);
                playerLocation.setY(playerLocation.getY() + SOUL_HOVER_OFFSET);
                return playerLocation;
            }

            // Too far, now we have to find a better location
            return findFallbackSoulSpawnLocation(player, playerLocation, true);
        }

        @NotNull
        static Location findFallbackSoulSpawnLocation(@NotNull Player player, @NotNull Location playerLocation, boolean improve) {
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

        private double distanceTo(@NotNull SoulDatabase.Soul s) {
            final double x = toX - s.locationX;
            final double y = toY - s.locationY;
            final double z = toZ - s.locationZ;
            return x*x + y*y + z*z;
        }

        @Override
        public int compare(@NotNull SoulDatabase.Soul o1, @NotNull SoulDatabase.Soul o2) {
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
