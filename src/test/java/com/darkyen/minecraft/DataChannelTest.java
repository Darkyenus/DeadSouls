package com.darkyen.minecraft;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.darkyen.minecraft.Serialization.SerializedType.CONFIGURATION_SERIALIZABLE_BYTE;
import static com.darkyen.minecraft.Serialization.SerializedType.LIST_BYTE;
import static com.darkyen.minecraft.Serialization.SerializedType.MAP_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public final class DataChannelTest {

    @Test
    void primitives() throws IOException {
        final ByteBufferChannel byteChn = new ByteBufferChannel();

        StringBuilder largeStringBuilder = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            largeStringBuilder.append("QWERTZUIOPLKJHGFDSAYXCVBNM");
        }
        // Large string to trigger slow path by being longer than output and input buffer
        String largeString = largeStringBuilder.toString();

        try (DataOutputChannel out = new DataOutputChannel(byteChn, 128)){
            out.writeBoolean(true);
            out.writeBoolean(false);
            out.writeByte(1);
            out.writeByte(90);
            out.writeByte(250);
            out.writeChar('a');
            out.writeChar('@');
            out.writeChar('ž');
            out.writeShort(0);
            out.writeShort(1000);
            out.writeShort(-1000);
            out.writeInt(1000);
            out.writeInt(-1000);
            out.writeInt(Integer.MAX_VALUE);
            out.writeLong(Long.MIN_VALUE);
            out.writeLong(123456789012345L);
            out.writeFloat(1234f);
            out.writeDouble(1234.0);
            out.writeUTF(largeString);
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn, 128)) {
            assertTrue(in.readBoolean());
            assertFalse(in.readBoolean());
            assertEquals(1, in.readByte());
            assertEquals(90, in.readByte());
            assertEquals(250, in.readUnsignedByte());
            assertEquals('a', in.readChar());
            assertEquals('@', in.readChar());
            assertEquals('ž', in.readChar());
            assertEquals(0, in.readShort());
            assertEquals(1000, in.readShort());
            assertEquals(-1000, in.readShort());
            assertEquals(1000, in.readInt());
            assertEquals(-1000, in.readInt());
            assertEquals(Integer.MAX_VALUE, in.readInt());
            assertEquals(Long.MIN_VALUE, in.readLong());
            assertEquals(123456789012345L, in.readLong());
            assertEquals(1234f, in.readFloat());
            assertEquals(1234.0, in.readDouble());
            assertEquals(largeString, in.readUTF());
        }
    }

    @Test
    void string() throws IOException {
        final ByteBufferChannel byteChn = new ByteBufferChannel();

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            out.writeChars("123");
            out.writeBytes("123");
            out.writeUTF("123");
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            assertEquals('1', in.readChar());
            assertEquals('2', in.readChar());
            assertEquals('3', in.readChar());
            assertEquals('1', in.readByte());
            assertEquals('2', in.readByte());
            assertEquals('3', in.readByte());
            assertEquals("123", in.readUTF());
        }
    }

    @Test
    void byteStressTest() throws IOException {
        final int repeats = 1<<20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeByte(i);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals((byte)i, in.readByte());
            }
        }
    }

    @Test
    void longStressTest() throws IOException {
        final int repeats = 1<<20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * 8);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeLong(i);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals(i, in.readLong());
            }
        }
    }

    @Test
    void bytesStressTest() throws IOException {
        final int repeats = 1<<20;
        final byte[] bytes = new byte[13];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte)i;
        }
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * bytes.length);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.write(bytes);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                final byte[] newBytes = new byte[bytes.length];
                in.readFully(newBytes);
                assertArrayEquals(bytes, newBytes);
            }
        }
    }

    @Test
    void utfStressTest() throws IOException {
        final int repeats = 1<<8;
        final String utf = "1234567890ABC";
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * (utf.length() + 2));

        try (DataOutputChannel out = new DataOutputChannel(byteChn, 64)){
            for (int i = 0; i < repeats; i++) {
                out.writeUTF(utf);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn, 64)) {
            for (int i = 0; i < repeats; i++) {
                assertEquals(utf, in.readUTF());
            }
        }
    }

    @Test
    void stressTest() throws IOException {
        final int repeats = 1 << 20;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * 57);

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            for (int i = 0; i < repeats; i++) {
                out.writeBoolean(true);
                out.writeBoolean(false);
                out.writeByte(1);
                out.writeByte(90);
                out.writeByte(250);
                out.writeChar('a');
                out.writeChar('@');
                out.writeChar('ž');
                out.writeShort(0);
                out.writeShort(1000);
                out.writeShort(-1000);
                out.writeInt(1000);
                out.writeInt(-1000);
                out.writeInt(Integer.MAX_VALUE);
                out.writeLong(Long.MIN_VALUE);
                out.writeLong(123456789012345L);
                out.writeFloat(1234f);
                out.writeDouble(1234.0);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (int i = 0; i < repeats; i++) {
                assertTrue(in.readBoolean());
                assertFalse(in.readBoolean());
                assertEquals(1, in.readByte());
                assertEquals(90, in.readByte());
                assertEquals(250, in.readUnsignedByte());
                assertEquals('a', in.readChar());
                assertEquals('@', in.readChar());
                assertEquals('ž', in.readChar());
                assertEquals(0, in.readShort());
                assertEquals(1000, in.readShort());
                assertEquals(-1000, in.readShort());
                assertEquals(1000, in.readInt());
                assertEquals(-1000, in.readInt());
                assertEquals(Integer.MAX_VALUE, in.readInt());
                assertEquals(Long.MIN_VALUE, in.readLong());
                assertEquals(123456789012345L, in.readLong());
                assertEquals(1234f, in.readFloat());
                assertEquals(1234.0, in.readDouble());
            }
        }
    }

    @Test
    void positions() throws IOException {
        final int repeats = 1 << 20;
        final int blockSize = 100;
        final ByteBufferChannel byteChn = new ByteBufferChannel(repeats * blockSize);


        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            final byte[] zeroBlock = new byte[blockSize];

            for (int i = 0; i < repeats; i++) {
                assertEquals(i * blockSize, out.position());
                out.write(zeroBlock);
            }

            for (int i = 0; i < repeats; i++) {
                out.position(i * blockSize);
                out.writeInt(i);
            }
        }

        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            final byte[] readBlock = new byte[blockSize - Integer.BYTES];

            for (int i = 0; i < repeats; i++) {
                final int marker = in.readInt();
                assertEquals(i, marker);

                in.readFully(readBlock);
                for (byte b : readBlock) {
                    assertEquals(0, b);
                }
            }
        }
    }

    private Random random = new Random();
    private final Serialization.SerializedType[] SERIALIZED_TYPE_VALUES = Serialization.SerializedType.values();

    private boolean isBranching(Serialization.SerializedType type) {
        switch (type) {
            case NULL:
            case PRIMITIVE_BOOLEAN_TRUE:
            case PRIMITIVE_BOOLEAN_FALSE:
            case PRIMITIVE_BYTE:
            case PRIMITIVE_CHARACTER:
            case PRIMITIVE_SHORT:
            case PRIMITIVE_INT:
            case PRIMITIVE_LONG:
            case PRIMITIVE_FLOAT:
            case PRIMITIVE_DOUBLE:
            case STRING:
                return false;
            case LIST_BYTE:
            case LIST:
            case MAP_BYTE:
            case MAP:
            case CONFIGURATION_SERIALIZABLE_BYTE:
            case CONFIGURATION_SERIALIZABLE:
                return true;
            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    private Object generateObject(float branchChance) {
        Serialization.SerializedType type = SERIALIZED_TYPE_VALUES[random.nextInt(SERIALIZED_TYPE_VALUES.length)];

        if (random.nextFloat() > branchChance) {
            while (isBranching(type)) {
                type = SERIALIZED_TYPE_VALUES[random.nextInt(SERIALIZED_TYPE_VALUES.length)];
            }
        }

        switch (type) {
            case NULL:
                return null;
            case PRIMITIVE_BOOLEAN_TRUE:
                return true;
            case PRIMITIVE_BOOLEAN_FALSE:
                return false;
            case PRIMITIVE_BYTE:
                return (byte)random.nextInt();
            case PRIMITIVE_CHARACTER:
                return (char)random.nextInt();
            case PRIMITIVE_SHORT:
                return (short)random.nextInt();
            case PRIMITIVE_INT:
                return random.nextInt();
            case PRIMITIVE_LONG:
                return random.nextLong();
            case PRIMITIVE_FLOAT:
                return Float.intBitsToFloat(random.nextInt());
            case PRIMITIVE_DOUBLE:
                return Double.longBitsToDouble(random.nextLong());
            case STRING: {
                final int length = random.nextInt(500);
                char[] characters = new char[length];
                for (int i = 0; i < length; i++) {
                    characters[i] = (char)random.nextInt();
                }
                return new String(characters);
            }
            case LIST_BYTE:
            case LIST: {
                final int length = type == LIST_BYTE ? random.nextInt(256) : 256 + random.nextInt(10);
                final ArrayList<Object> resultList = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    resultList.add(generateObject(branchChance * 0.3f));
                }
                return resultList;
            }
            case MAP_BYTE:
            case MAP: {
                final int length = type == MAP_BYTE ? random.nextInt(256) : 256 + random.nextInt(10);
                final HashMap<String, Object> resultMap = new HashMap<>();
                for (int i = 0; i < length; i++) {
                    resultMap.put("KEY:"+i, generateObject(branchChance * 0.3f));
                }
                return resultMap;
            }
            case CONFIGURATION_SERIALIZABLE_BYTE:
            case CONFIGURATION_SERIALIZABLE: {
                final int length = type == CONFIGURATION_SERIALIZABLE_BYTE ? random.nextInt(256) : 256 + random.nextInt(10);
                final HashMap<String, Object> resultMap = new HashMap<>();
                for (int i = 0; i < length; i++) {
                    resultMap.put("KEY:"+i, generateObject(branchChance * 0.3f));
                }
                return new TestConfigurationSerializable(resultMap);
            }
            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    @SerializableAs("TestConfigurationSerializable")
    public static final class TestConfigurationSerializable implements ConfigurationSerializable {

        final Map<String, Object> value;

        public TestConfigurationSerializable(Map<String, Object> value) {
            this.value = value;
        }

        @Override
        public @NotNull Map<String, Object> serialize() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestConfigurationSerializable that = (TestConfigurationSerializable) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return "TestConfigurationSerializable{" +
                    "value=" + value +
                    '}';
        }
    }

    static {
        ConfigurationSerialization.registerClass(TestConfigurationSerializable.class);
    }

    @Test
    void objectStressTest() throws IOException, Serialization.Exception {
        final int capacity = 1 << 27;
        final ByteBufferChannel byteChn = new ByteBufferChannel(capacity);
        final ArrayList<Object> objects = new ArrayList<>();

        try (DataOutputChannel out = new DataOutputChannel(byteChn)){
            long maxObjectSize = 0;
            int removed = 0;
            while (out.position() + maxObjectSize * 2 < capacity) {
                final Object o = generateObject(0.3f);
                objects.add(o);
                final long posBefore = out.position();
                Serialization.serializeObject(o, out);
                final long objectSize = out.position() - posBefore;
                if (objectSize > maxObjectSize) {
                    maxObjectSize = objectSize;
                    //System.out.println("New max object size: "+objectSize);
                }

                if (random.nextInt(5) == 0) {
                    out.position(posBefore);
                    out.truncate();
                    objects.remove(objects.size() - 1);
                    removed++;
                }
            }
            System.out.println("Generated "+objects.size()+" objects, total size "+out.position()+", removed "+ removed);
        }


        byteChn.position(0);

        try (DataInputChannel in = new DataInputChannel(byteChn)) {
            for (Object expected : objects) {
                final Object received = Serialization.deserializeObject(in);

                assertDeepEquals(expected, received, "");
            }
            assertFalse(in.hasRemaining());
        }
    }

    private static void assertDeepEquals(Object expected, Object received, String prefix) {
        if (expected == received) {
            return;
        }
        if (expected instanceof List) {
            assertTrue(received instanceof List, prefix);
            final List expList = (List) expected;
            final List recList = (List) received;
            final int size = expList.size();
            assertEquals(size, recList.size(), prefix+".size");

            for (int i = 0; i < size; i++) {
                assertDeepEquals(expList.get(i), recList.get(i), prefix+"["+i+"]");
            }
        } else if (expected instanceof Map) {
            assertTrue(received instanceof Map, prefix);
            //noinspection unchecked
            final Map<String, Object> expMap = (Map<String,Object>) expected;
            //noinspection unchecked
            final Map<String, Object> recMap = (Map<String, Object>) received;

            for (Map.Entry<String, Object> entry : expMap.entrySet()) {
                assertTrue(recMap.containsKey(entry.getKey()), prefix+".contains("+entry.getKey()+")");
                assertDeepEquals(entry.getValue(), recMap.get(entry.getKey()), prefix+".get("+entry.getKey()+")");
            }
        } else if (expected instanceof TestConfigurationSerializable) {
            assertTrue(received instanceof TestConfigurationSerializable, prefix);
            final TestConfigurationSerializable expConfSer = (TestConfigurationSerializable) expected;
            final TestConfigurationSerializable recConfSer = (TestConfigurationSerializable) received;

            assertDeepEquals(expConfSer.value, recConfSer.value, "((ConfSer)"+prefix+").value");
        } else {
            assertEquals(expected, received, prefix);
        }
    }
}
