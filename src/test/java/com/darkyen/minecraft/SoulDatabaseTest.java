package com.darkyen.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class SoulDatabaseTest {

	@BeforeAll
	static void setup() {
		Bukkit.setServer(new ServerStub() {

		});
	}

	@Test
	void brokenItemTest() throws IOException, Serialization.Exception {
		final ItemStack goodItem1 = new ItemStack(Material.DIRT, 5);
		final ItemStack goodItem2 = new ItemStack(Material.COBBLESTONE, 50);

		final ItemStack badItem = new ItemStack(Material.DIAMOND_SWORD, 1) {
			@Override
			public @NotNull Map<String, Object> serialize() {
				throw new RuntimeException("this item is not serializable, sorry (not error)");
			}
		};

		final ItemStack[] brokenItems = {goodItem1, badItem, goodItem2};
		final ItemStack[] goodItems = {goodItem1, goodItem2};
		final SoulDatabase.Soul soul = new SoulDatabase.Soul(UUID.randomUUID(), UUID.randomUUID(), 1.0, 2.0, 3.0, 1234567890L, brokenItems, 98765);

		final ByteBufferChannel byteBufferChannel = new ByteBufferChannel();
		try (DataOutputChannel channel = new DataOutputChannel(byteBufferChannel)) {
			Assertions.assertTrue(SoulDatabase.serializeSoul(soul, channel));
		}
		byteBufferChannel.position(0L);

		final SoulDatabase.Soul deserializedSoul = SoulDatabase.deserializeSoul(new DataInputChannel(byteBufferChannel), SoulDatabase.CURRENT_DB_VERSION);

		assertEquals(soul.owner, deserializedSoul.owner);
		assertEquals(soul.locationWorld, deserializedSoul.locationWorld);
		assertEquals(soul.locationX, deserializedSoul.locationX);
		assertEquals(soul.locationY, deserializedSoul.locationY);
		assertEquals(soul.locationZ, deserializedSoul.locationZ);
		assertEquals(soul.xp, deserializedSoul.xp);
		assertEquals(soul.timestamp, deserializedSoul.timestamp);
		assertArrayEquals(goodItems, deserializedSoul.items);

		assertEquals(byteBufferChannel.size(), byteBufferChannel.position());
	}

}
