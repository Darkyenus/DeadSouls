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
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.darkyen.minecraft.Serialization.ZERO_UUID;
import static com.darkyen.minecraft.Serialization.deserializeObject;
import static com.darkyen.minecraft.Serialization.deserializeUUID;
import static com.darkyen.minecraft.Serialization.serializeObject;
import static com.darkyen.minecraft.Serialization.serializeUUID;
import static com.darkyen.minecraft.Util.saturatedAdd;

/**
 *
 */
public class SoulDatabase {

    private static final Logger LOG = Logger.getLogger("DeadSouls-ItemStore");

    @NotNull
    private final Plugin owner;
    private static final int SOUL_STORE_SCALE = 16;
    private final SpatialDatabase<@NotNull Soul> souls = new SpatialDatabase<>();
    private final ArrayList<@Nullable Soul> soulsById = new ArrayList<>();
    @NotNull
    private final Path databaseFile;

    public SoulDatabase(@NotNull Plugin owner, @NotNull Path databaseFile) throws IOException, Serialization.Exception {
        this.owner = owner;
        this.databaseFile = databaseFile;
        Files.createDirectories(databaseFile.getParent());

        try (DataInputChannel in = new DataInputChannel(Files.newByteChannel(databaseFile, StandardOpenOption.READ))) {
            while (in.hasRemaining()) {
                final Soul soul = deserializeSoul(in);
                soulsById.add(soul);
                souls.insert(soul);
            }
        } catch (NoSuchFileException ignored) {}
    }

    private final Object SAVE_LOCK = new Object();

    public void save() throws IOException {
        final ArrayList<@Nullable Soul> soulsCopy;
        synchronized (soulsById) {
            soulsCopy = new ArrayList<>(soulsById);
        }

        synchronized (SAVE_LOCK) {
            Exception exception = null;
            for (int i = 0; i < 10; i++) {
                final Path writeFile = databaseFile
                        .resolveSibling(databaseFile.getFileName().toString() + "." + (System.nanoTime() & 0xFFFFFF));
                try (DataOutputChannel out = new DataOutputChannel(Files
                        .newByteChannel(writeFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
                    for (final Soul soul : soulsCopy) {
                        if (soul != null) {
                            serializeSoul(soul, out);
                        }
                    }
                } catch (FileAlreadyExistsException alreadyExists) {
                    // Try again
                    exception = alreadyExists;
                    continue;
                }

                // File written, now we can replace the old one
                Files.move(writeFile, databaseFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            LOG.log(Level.SEVERE, "Failed to save souls", exception);
        }
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

        return fadedSouls;
    }

    public int addSoul(UUID owner, Location location, ItemStack[] contents, int xp) {
        final Soul soul = new Soul(owner, location, System.currentTimeMillis(), contents, xp);
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
        Bukkit.getScheduler().runTaskAsynchronously(this.owner, () -> {
            try {
                save();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to save ItemStore asynchronously", e);
            }
        });

        return soulId;
    }

    public void freeSoul(CommandSender sender, int soulId, long soulFreeAfterMs) {
        Soul soul;
        if (soulId < 0) {
            soul = null;
        } else {
            synchronized (soulsById) {
                if (soulId >= soulsById.size()) {
                    soul = null;
                } else {
                    soul = soulsById.get(soulId);
                }
            }
        }

        if (soul == null) {
            sender.sendMessage(ChatColor.AQUA+"This soul does not need freeing");
            return;
        }

        if (soul.owner == null) {
            sender.sendMessage(ChatColor.AQUA+"This soul is already free");
            return;
        }

        if (sender instanceof OfflinePlayer && !soul.owner.equals(((OfflinePlayer) sender).getUniqueId())) {
            sender.sendMessage(ChatColor.AQUA+"This soul is not yours to free");
            return;
        }

        if (soul.freeSoul(System.currentTimeMillis(), soulFreeAfterMs)) {
            sender.sendMessage(ChatColor.AQUA+"Soul has been set free");
        } else {
            sender.sendMessage(ChatColor.AQUA+"This soul is already free");
        }
    }

    public void removeSoul(Soul toRemove) {
        synchronized (soulsById) {
            final int i = soulsById.indexOf(toRemove);
            if (i == -1) {
                LOG.log(Level.WARNING, "Soul " + toRemove + " already removed from BY-ID");
            } else {
                soulsById.set(i, null);
            }
        }

        if (!souls.remove(toRemove)) {
            LOG.log(Level.WARNING, "Soul "+toRemove+" already removed from SOULS");
        }
    }

    public void findSouls(World world, int x, int z, int radius, Collection<Soul> out) {
        souls.query((x - radius) / SOUL_STORE_SCALE, (x + radius + SOUL_STORE_SCALE - 1) / SOUL_STORE_SCALE,
                (z - radius) / SOUL_STORE_SCALE, (z + radius + SOUL_STORE_SCALE - 1) / SOUL_STORE_SCALE, out);
        out.removeIf((soul) -> soul.location.getWorld() == null || soul.location.getWorld() != world);
    }

    static final class Soul implements SpatialDatabase.Entry {

        @Nullable
        UUID owner;
        final Location location;
        final long timestamp;
        /** Can be changed when collected */
        @NotNull
        ItemStack[] items;
        /** Can be changed when collected */
        int xp;

        Soul(@Nullable UUID owner, Location location, long timestamp, @NotNull ItemStack[] items, int xp) {
            this.owner = owner;
            this.location = location;
            this.timestamp = timestamp;
            this.items = items;
            this.xp = xp;
        }

        public boolean isAccessibleBy(OfflinePlayer player, long now, long soulFreeAfterMs) {
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
            return location.getBlockX() / SOUL_STORE_SCALE;
        }

        @Override
        public int y() {
            return location.getBlockZ() / SOUL_STORE_SCALE;
        }
    }

    static boolean serializeSoul(Soul soul, DataOutputChannel out) {
        final World world = soul.location.getWorld();
        if (world == null) {
            return false;
        }

        try {
            serializeUUID(world.getUID(), out);
            out.writeInt(soul.location.getBlockX());
            out.writeInt(soul.location.getBlockY());
            out.writeInt(soul.location.getBlockZ());
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
                    LOG.log(Level.SEVERE, "Failed to serialize item "+item);
                    out.position(itemPosition);
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
    static Soul deserializeSoul(DataInput in) throws IOException, Serialization.Exception {
        final UUID worldUUID = deserializeUUID(in);
        final int locationX = in.readInt();
        final int locationY = in.readInt();
        final int locationZ = in.readInt();
        final UUID ownerUUID = deserializeUUID(in);
        final long timestamp = in.readLong();
        final int xp = in.readInt();

        final int itemAmount = in.readShort() & 0xFFFF;
        final ItemStack[] items = new ItemStack[itemAmount];
        for (int i = 0; i < itemAmount; i++) {
            final int entries = in.readShort() & 0xFFFF;
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

        World world = Bukkit.getWorld(worldUUID);
        if (world == null) {
            LOG.log(Level.WARNING, "No world with UUID "+worldUUID+" found");
            world = Bukkit.getWorlds().get(0);
        }
        final Location location = new Location(world, locationX, locationY, locationZ);
        final UUID owner = ownerUUID.equals(ZERO_UUID) ? null : ownerUUID;
        return new Soul(owner, location, timestamp, items, xp);
    }
}
