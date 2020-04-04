package com.darkyen.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.darkyen.minecraft.Serialization.ZERO_UUID;
import static com.darkyen.minecraft.Serialization.deserializeObject;
import static com.darkyen.minecraft.Serialization.deserializeUUID;
import static com.darkyen.minecraft.Serialization.serializeObject;
import static com.darkyen.minecraft.Serialization.serializeUUID;
import static com.darkyen.minecraft.Util.getWorld;
import static com.darkyen.minecraft.Util.saturatedAdd;

/**
 *
 */
@SuppressWarnings("WeakerAccess") // For tests
public class SoulDatabase {

    private static final Logger LOG = Logger.getLogger("DeadSouls-ItemStore");

    static final int CURRENT_DB_VERSION = 1;
    private static final int SOUL_STORE_SCALE = 16;

    @Nullable
    private final Plugin owner;
    @NotNull
    private final SpatialDatabase<@NotNull Soul> souls = new SpatialDatabase<>();
    @NotNull
    private final ArrayList<@Nullable Soul> soulsById = new ArrayList<>();
    @NotNull
    private final Path databaseFile;

    private boolean dirty = false;

	public SoulDatabase(@Nullable Plugin owner, @NotNull Path databaseFile) {
		this.owner = owner;
		this.databaseFile = databaseFile;

		try {
			for (Soul soul : load(databaseFile)) {
				soulsById.add(soul);
				souls.insert(soul);
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to load soul database, souls will not be present", e);
		}
	}

    /** NOTE: Synchronize on the collection before accessing */
    @NotNull
    public List<@Nullable Soul> getSoulsById() {
	    return soulsById;
    }

    public static final class SoulAndId {
        public final int id;
        public final Soul soul;

        public SoulAndId(int id, Soul soul) {
            this.id = id;
            this.soul = soul;
        }
    }

    public List<@NotNull SoulAndId> getSoulsByOwnerAndWorld(@Nullable UUID owner, @Nullable UUID world) {
        final ArrayList<SoulAndId> result = new ArrayList<>();
        synchronized (soulsById) {
            final ArrayList<@Nullable Soul> soulsById = this.soulsById;
            for (int i = 0; i < soulsById.size(); i++) {
                Soul soul = soulsById.get(i);
                if (soul == null) {
                    continue;
                }
                if ((owner == null || owner.equals(soul.owner)) && (world == null || world
                        .equals(soul.locationWorld))) {
                    result.add(new SoulAndId(i, soul));
                }
            }
        }
        return result;
    }

    @NotNull
    public static List<Soul> load(@NotNull Path databaseFile) throws IOException, Serialization.Exception {
        final ArrayList<Soul> result = new ArrayList<>();
        try (DataInputChannel in = new DataInputChannel(Files.newByteChannel(databaseFile, StandardOpenOption.READ))) {
            final int version = in.readInt();
            if (version > CURRENT_DB_VERSION || version < 0) {
                throw new Serialization.Exception("Invalid database version, please upgrade the plugin");
            }
            int soulCount = 0;
            while (in.hasRemaining()) {
                final Soul soul = deserializeSoul(in, version);
                result.add(soul);
                soulCount++;
            }

            LOG.log(Level.INFO, "Soul database loaded ("+soulCount+" souls, db version "+version+")");
        } catch (NoSuchFileException ignored) {}
        return result;
    }

    public void loadLegacy(@NotNull Path databaseFile) throws IOException, Serialization.Exception {
        int soulCount = 0;
        try (DataInputChannel in = new DataInputChannel(Files.newByteChannel(databaseFile, StandardOpenOption.READ))) {
            while (in.hasRemaining()) {
                final Soul soul = deserializeSoul(in, 0);
                soulsById.add(soul);
                souls.insert(soul);
                soulCount++;
            }
        } catch (NoSuchFileException ignored) {
            return;
        }

        // Loaded successfully, save and delete legacy
        if (save()) {
            Files.deleteIfExists(databaseFile);
            LOG.log(Level.INFO, "Soul database migrated ("+soulCount+" souls)");
        }
    }

    @NotNull
    private final Object SAVE_LOCK = new Object();

    public boolean save() throws IOException {
        final ArrayList<@Nullable Soul> soulsCopy;
        synchronized (soulsById) {
            soulsCopy = new ArrayList<>(soulsById);
        }

        try {
            Files.createDirectories(databaseFile.getParent());
        } catch (IOException io) {
            LOG.log(Level.WARNING, "Failed to create directories for soul database file, saving may fail", io);
        }

        synchronized (SAVE_LOCK) {
            Exception exception = null;
            for (int i = 0; i < 10; i++) {
                final Path writeFile = databaseFile
                        .resolveSibling(databaseFile.getFileName().toString() + "." + (System.nanoTime() & 0xFFFFFF));
                int failedWrites = 0;
                try (DataOutputChannel out = new DataOutputChannel(Files
                        .newByteChannel(writeFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                    out.writeInt(CURRENT_DB_VERSION);

                    for (final Soul soul : soulsCopy) {
                        if (soul != null) {
                            if (!serializeSoul(soul, out)) {
                                failedWrites++;
                            }
                        }
                    }
                } catch (FileAlreadyExistsException alreadyExists) {
                    // Try again
                    exception = alreadyExists;
                    continue;
                }

                // File written, now we can replace the old one
                Files.move(writeFile, databaseFile, StandardCopyOption.REPLACE_EXISTING);

                if (failedWrites > 0) {
                    LOG.log(Level.WARNING, failedWrites + " soul(s) failed to save");
                }
                return true;
            }
            LOG.log(Level.SEVERE, "Failed to save souls", exception);
        }

        return false;
    }

    public int removeFadedSouls(long soulFadesAfterMs) {
        final ArrayList<@Nullable Soul> soulsById = this.soulsById;
        int fadedSouls = 0;
        final long now = System.currentTimeMillis();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (soulsById) {
            for (int i = 0; i < soulsById.size(); i++) {
                final Soul soul = soulsById.get(i);
                if (soul == null)
                    continue;

                if (Util.saturatedAdd(soul.timestamp, soulFadesAfterMs) <= now) {
                    // Soul should expire
                    soulsById.set(i, null);
                    souls.remove(soul);
                    fadedSouls++;
                }
            }
        }

        if (fadedSouls > 0) {
            dirty = true;
        }

        return fadedSouls;
    }

    public int addSoul(@Nullable UUID owner, @NotNull UUID world, double x, double y, double z, @NotNull ItemStack[] contents, int xp) {
        final Soul soul = new Soul(owner, world, x, y, z, System.currentTimeMillis(), contents, xp);
        int soulId = -1;
        synchronized (soulsById) {
            final ArrayList<@Nullable Soul> soulsById = this.soulsById;
            for (int i = 0; i < soulsById.size(); i++) {
                if (soulsById.get(i) == null) {
                    soulsById.set(i, soul);
                    soulId = i;
                    break;
                }
            }
            if (soulId == -1) {
                soulId = soulsById.size();
                soulsById.add(soul);
            }
        }
        souls.insert(soul);
        dirty = true;
        return soulId;
    }

    public void markDirty() {
        dirty = true;
    }

    public void autoSave() {
        if (!dirty) {
            return;
        }

        if (this.owner != null) {
            dirty = false;
            Bukkit.getScheduler().runTaskAsynchronously(this.owner, () -> {
                boolean success = false;
                try {
                    if (save()) {
                        success = true;
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to save ItemStore asynchronously", e);
                } finally {
                    if (!success) {
                        dirty = true;
                    }
                }
            });
        } else {
            LOG.log(Level.INFO, "Saving synchronously");
            try {
                if (save()) {
                    dirty = false;
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to save ItemStore synchronously", e);
            }
        }
    }

    @Nullable
    public Soul getSoulById(int soulId) {
        Soul soul = null;
        if (soulId >= 0) {
            synchronized (soulsById) {
                if (soulId < soulsById.size()) {
                    soul = soulsById.get(soulId);
                }
            }
        }
        return soul;
    }

    public void freeSoul(@NotNull CommandSender sender, int soulId, long soulFreeAfterMs, boolean canFreeOwn, boolean canFreeAll) {
        final Soul soul = getSoulById(soulId);

        if (soul == null) {
            sender.sendMessage(ChatColor.AQUA+"This soul does not need freeing");
            return;
        }

        if (soul.owner == null) {
            sender.sendMessage(ChatColor.AQUA+"This soul is already free");
            return;
        }

        if (!canFreeAll) {
            final boolean ownSoul = soul.isOwnedBy(sender);
            if (ownSoul) {
                if (!canFreeOwn) {
                    sender.sendMessage(ChatColor.AQUA + "You cannot free your own soul");
                    return;
                }
            } else {
                sender.sendMessage(ChatColor.AQUA + "This soul is not yours to free");
                return;
            }
        }

        if (soul.freeSoul(System.currentTimeMillis(), soulFreeAfterMs)) {
            sender.sendMessage(ChatColor.AQUA+"Soul has been set free");
            dirty = true;
        } else {
            sender.sendMessage(ChatColor.AQUA+"This soul is already free");
        }
    }

    public void removeSoul(@NotNull Soul toRemove) {
        synchronized (soulsById) {
            final int i = soulsById.indexOf(toRemove);
            if (i == -1) {
                LOG.log(Level.WARNING, "Soul " + toRemove + " already removed from BY-ID");
            } else {
                soulsById.set(i, null);
                dirty = true;
            }
        }

        if (!souls.remove(toRemove)) {
            LOG.log(Level.WARNING, "Soul "+toRemove+" already removed from SOULS");
        }
    }

    public void findSouls(@NotNull World world, int x, int z, int radius, @NotNull Collection<Soul> out) {
        souls.query((x - radius) / SOUL_STORE_SCALE, (x + radius + SOUL_STORE_SCALE - 1) / SOUL_STORE_SCALE,
                (z - radius) / SOUL_STORE_SCALE, (z + radius + SOUL_STORE_SCALE - 1) / SOUL_STORE_SCALE, out);
        final UUID worldUID = world.getUID();
        out.removeIf((soul) -> !worldUID.equals(soul.locationWorld));
    }

    @SuppressWarnings("WeakerAccess")
    static final class Soul implements SpatialDatabase.Entry {

        @Nullable
        UUID owner;
        @NotNull
        final UUID locationWorld;
        final double locationX, locationY, locationZ;
        final long timestamp;
        /** Can be changed when collected */
        @NotNull
        ItemStack[] items;
        /** Can be changed when collected */
        int xp;

        Soul(@Nullable UUID owner, @NotNull UUID locationWorld, double x, double y, double z, long timestamp, @NotNull ItemStack[] items, int xp) {
            this.owner = owner;
            this.locationWorld = locationWorld;
            this.locationX = x;
            this.locationY = y;
            this.locationZ = z;
            this.timestamp = timestamp;
            this.items = items;
            this.xp = xp;
        }

        public boolean isOwnedBy(CommandSender commandSender) {
            final UUID owner = this.owner;
            return owner != null
                    && commandSender instanceof OfflinePlayer
                    && owner.equals(((OfflinePlayer) commandSender).getUniqueId());
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isAccessibleBy(@NotNull OfflinePlayer player, long now, long soulFreeAfterMs) {
            final UUID owner = this.owner;
            if (owner != null && !owner.equals(player.getUniqueId())) {
                // Soul of somebody else, not accessible unless expired
                if (saturatedAdd(timestamp, soulFreeAfterMs) <= now) {
                    // Soul should become free
                    this.owner = null;
                    return true;
                }
                return false;
            }
            return true;
        }

        /** @return true if free, false if already freed */
        public boolean freeSoul(long now, long soulFreeAfterMs) {
            if (this.owner == null) {
                return false;
            }

            this.owner = null;
            // Did soul become free on its own?
            return saturatedAdd(timestamp, soulFreeAfterMs) > now;

        }

        @Override
        public int x() {
            return NumberConversions.floor(locationX) / SOUL_STORE_SCALE;
        }

        @Override
        public int y() {
            return NumberConversions.floor(locationZ) / SOUL_STORE_SCALE;
        }

        @Nullable
        private transient Location locationCache = null;

        @Nullable
        public Location getLocation(@Nullable World worldHint) {
            Location locationCache = this.locationCache;
            if (locationCache != null && getWorld(locationCache) != null) {
                return locationCache;
            }
            World world;
            if (worldHint != null && locationWorld.equals(worldHint.getUID())) {
                world = worldHint;
            } else {
                world = Bukkit.getWorld(locationWorld);
            }
            if (world == null) {
                locationCache = null;
            } else {
                locationCache = new Location(world, locationX, locationY, locationZ);
            }

            this.locationCache = locationCache;
            return locationCache;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Soul soul = (Soul) o;

            if (Double.compare(soul.locationX, locationX) != 0) return false;
            if (Double.compare(soul.locationY, locationY) != 0) return false;
            if (Double.compare(soul.locationZ, locationZ) != 0) return false;
            if (timestamp != soul.timestamp) return false;
            if (xp != soul.xp) return false;
            if (owner != null ? !owner.equals(soul.owner) : soul.owner != null) return false;
            if (!locationWorld.equals(soul.locationWorld)) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(items, soul.items);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = owner != null ? owner.hashCode() : 0;
            result = 31 * result + locationWorld.hashCode();
            temp = Double.doubleToLongBits(locationX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(locationY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(locationZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + Arrays.hashCode(items);
            result = 31 * result + xp;
            return result;
        }
    }

    static boolean serializeSoul(@NotNull Soul soul, @NotNull DataOutputChannel out) {
        try {
            serializeUUID(soul.locationWorld, out);
            out.writeDouble(soul.locationX);
            out.writeDouble(soul.locationY);
            out.writeDouble(soul.locationZ);
            if (soul.owner == null) {
                serializeUUID(ZERO_UUID, out);
            } else {
                serializeUUID(soul.owner, out);
            }
            out.writeLong(soul.timestamp);
            out.writeInt(soul.xp);

            final long itemAmountPosition = out.position();
            out.writeShort(soul.items.length);
            int failed = 0;
            for (ItemStack item : soul.items) {
                final long itemPosition = out.position();
                try {
                    final Map<String, Object> map = item.serialize();
                    out.writeShort(map.size());
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        out.writeUTF(entry.getKey());
                        serializeObject(entry.getValue(), out);
                    }
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed to serialize item: "+Util.safeToString(item), e);
                    out.position(itemPosition);
                    out.truncate();
                    failed++;
                }
            }

            if (failed > 0) {
                final long endPosition = out.position();
                out.position(itemAmountPosition);
                out.writeShort(soul.items.length - failed);
                out.position(endPosition);
            }
        } catch (IOException io) {
            LOG.log(Level.SEVERE, "Failed to serialize: "+ Arrays.toString(soul.items), io);
            return false;
        }

        return true;
    }

    @NotNull
    static Soul deserializeSoul(@NotNull DataInput in, int version) throws IOException, Serialization.Exception {
        final UUID worldUUID = deserializeUUID(in);
        final double locationX = version == 0 ? in.readInt() : in.readDouble();
        final double locationY = version == 0 ? in.readInt() : in.readDouble();
        final double locationZ = version == 0 ? in.readInt() : in.readDouble();
        final UUID ownerUUID = deserializeUUID(in);
        final long timestamp = in.readLong();
        final int xp = in.readInt();

        final int itemAmount = in.readUnsignedShort();
        if (itemAmount > 100) {
            LOG.log(Level.WARNING, "Suspiciously high amount of items in the soul: "+itemAmount);
        }
        final ItemStack[] items = new ItemStack[itemAmount];
        for (int i = 0; i < itemAmount; i++) {
            final int entries = in.readUnsignedShort();
            if (entries > 100) {
                LOG.log(Level.WARNING, "Suspiciously high amount of entries in the soul: "+entries);
            }
            final HashMap<String, Object> itemMap = new HashMap<>(entries + entries / 2);
            for (int entryId = 0; entryId < entries; entryId++) {
                final String key = in.readUTF();
                final Object value = deserializeObject(in);
                itemMap.put(key, value);
            }
            try {
                items[i] = ItemStack.deserialize(itemMap);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to deserialize item "+i+": "+itemMap);
                items[i] = new ItemStack(Material.AIR);
            }
        }

        final UUID owner = ownerUUID.equals(ZERO_UUID) ? null : ownerUUID;
        return new Soul(owner, worldUUID, locationX, locationY, locationZ, timestamp, items, xp);
    }
}
