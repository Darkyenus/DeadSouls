package com.darkyen.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
class ItemStoreTest {

    private static final Logger LOG = Logger.getLogger(ItemStoreTest.class.getName());
    private static final Random random = new Random();

    private static void testSerialization(ItemStack item) throws Exception {
        final SoulDatabase.Soul soul = new SoulDatabase.Soul(
                random.nextBoolean() ? Bukkit.getOfflinePlayers()[0].getUniqueId() : null,
                Bukkit.getWorlds().get(0).getUID(),
                (double) random.nextInt(5000) - 2500,
                (double) random.nextInt(250),
                (double) random.nextInt(5000) - 2500,
                random.nextLong(),
                new ItemStack[]{item, item},
                random.nextInt(1000)
        );

        final ByteBufferChannel byteBufferChannel = new ByteBufferChannel();
        try (DataOutputChannel channel = new DataOutputChannel(byteBufferChannel)) {
            Assertions.assertTrue(SoulDatabase.serializeSoul(soul, channel));
        }
        byteBufferChannel.position(0L);
        final SoulDatabase.Soul soulFromHell = SoulDatabase.deserializeSoul(new DataInputChannel(byteBufferChannel), SoulDatabase.CURRENT_DB_VERSION);

        Assertions.assertEquals(soul.locationWorld, soulFromHell.locationWorld);
        Assertions.assertEquals(soul.locationX, soulFromHell.locationX);
        Assertions.assertEquals(soul.locationY, soulFromHell.locationY);
        Assertions.assertEquals(soul.locationZ, soulFromHell.locationZ);
        Assertions.assertEquals(soul.timestamp, soulFromHell.timestamp);
        Assertions.assertEquals(soul.xp, soulFromHell.xp);
        Assertions.assertEquals(soul.owner, soulFromHell.owner);
        Assertions.assertArrayEquals(soul.items, soulFromHell.items);
    }

    private static void savingTest() throws IOException, Serialization.Exception {
        final Path temporaryDatabaseFile = Paths.get("testingdb.bin").toAbsolutePath();
        final SoulDatabase soulDatabase = new SoulDatabase(null, temporaryDatabaseFile);
        final Random random = new Random();

        for (int iteration = 0; iteration < 200; iteration++) {
            final ItemStack[] itemStacks = new ItemStack[random.nextInt(100)];
            for (int item = 0; item < itemStacks.length; item++) {
                itemStacks[item] = new ItemStack(Material.DIAMOND_SWORD, 1);
                itemStacks[item].addEnchantment(Enchantment.DAMAGE_ALL, 3);
                final ItemMeta itemMeta = itemStacks[item].getItemMeta();
                assert itemMeta != null;
                itemMeta.setDisplayName("BLAH"+item);
                itemMeta.setUnbreakable(true);
                itemStacks[item].setItemMeta(itemMeta);
            }
            soulDatabase.addSoul(UUID.randomUUID(), UUID.randomUUID(), random.nextDouble(), random.nextDouble(), random.nextDouble(), itemStacks, random.nextInt(100000));

            final List<SoulDatabase.Soul> loaded = SoulDatabase.load(temporaryDatabaseFile);
            final List<SoulDatabase.@Nullable Soul> expected = soulDatabase.getSoulsById();

            if (loaded.size() != expected.size()) {
                throw new AssertionError(loaded + " " +expected);
            }

            for (int i = 0; i < loaded.size(); i++) {
                final SoulDatabase.Soul loadedSoul = loaded.get(i);
                final SoulDatabase.Soul expectedSoul = expected.get(i);

                if (!expectedSoul.equals(loadedSoul)) {
                    throw new AssertionError(loadedSoul + " " +expectedSoul);
                }
            }
        }
        Files.delete(temporaryDatabaseFile);
    }

    public static void runLiveTest(Plugin plugin) throws Exception {
        for (Material value : Material.values()) {
            if (!value.isItem() || value.getMaxStackSize() <= 0) {
                continue;
            }

            final ItemStack item = new ItemStack(value, Math.min(random.nextInt(64) + 1, value.getMaxStackSize()));

            while (random.nextBoolean()) {
                try {
                    for (Enchantment enchantment : Enchantment.values()) {
                        if (enchantment.canEnchantItem(item)) {
                            item.addUnsafeEnchantment(enchantment, 1 + random.nextInt(enchantment.getMaxLevel()));
                        }
                    }
                } catch (Exception ignored) {}
            }

            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (random.nextBoolean()) {
                    for (ItemFlag flag : ItemFlag.values()) {
                        if (random.nextBoolean()) {
                            meta.addItemFlags(flag);
                        }
                    }
                }

                if (random.nextBoolean()) {
                    meta.setDisplayName("Nombre: "+random.nextInt());
                }

                if (random.nextBoolean()) {
                    meta.addAttributeModifier(Attribute.GENERIC_LUCK, new AttributeModifier("foo", 3.14, AttributeModifier.Operation.ADD_NUMBER));
                }

                if (random.nextBoolean()) {
                    meta.setUnbreakable(random.nextBoolean());
                }

                if (random.nextBoolean()) {
                    meta.setLocalizedName("Localized Nombre: "+random.nextInt());
                }

                if (random.nextBoolean()) {
                    meta.setLore(Arrays.asList("Hello", "This is lore"));
                }

                item.setItemMeta(meta);
            }

            try {
                testSerialization(item);
            } catch (Throwable t) {
                System.out.println("Failed: "+item.getType());
                t.printStackTrace();
            }
        }

        try {
            savingTest();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Development test failed (sync)", e);
        }

        Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                savingTest();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Development test failed (async)", e);
            }
        });
    }
}
