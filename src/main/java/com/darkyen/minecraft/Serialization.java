package com.darkyen.minecraft;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
@SuppressWarnings("rawtypes")
public class Serialization {

    private static final Logger LOG = Logger.getLogger("DeadSouls-Serialization");

    public static void serializeObject(@Nullable Object object, @NotNull DataOutput out) throws IOException, Exception {
        if (object == null) {
            out.writeByte(SerializedType.NULL.ordinal());
        } else if (object instanceof Boolean) {
            if ((Boolean)object) {
                out.writeByte(SerializedType.PRIMITIVE_BOOLEAN_TRUE.ordinal());
            } else {
                out.writeByte(SerializedType.PRIMITIVE_BOOLEAN_FALSE.ordinal());
            }
        } else if (object instanceof Byte) {
            out.writeByte(SerializedType.PRIMITIVE_BYTE.ordinal());
            out.writeByte((Byte) object);
        } else if (object instanceof Character) {
            out.writeByte(SerializedType.PRIMITIVE_CHARACTER.ordinal());
            out.writeChar((Character) object);
        } else if (object instanceof Short) {
            out.writeByte(SerializedType.PRIMITIVE_SHORT.ordinal());
            out.writeShort((Short) object);
        } else if (object instanceof Integer) {
            out.writeByte(SerializedType.PRIMITIVE_INT.ordinal());
            out.writeInt((Integer) object);
        } else if (object instanceof Long) {
            out.writeByte(SerializedType.PRIMITIVE_LONG.ordinal());
            out.writeLong((Long) object);
        } else if (object instanceof Float) {
            out.writeByte(SerializedType.PRIMITIVE_FLOAT.ordinal());
            out.writeFloat((Float) object);
        } else if (object instanceof Double) {
            out.writeByte(SerializedType.PRIMITIVE_DOUBLE.ordinal());
            out.writeDouble((Double) object);
        } else if (object instanceof String) {
            out.writeByte(SerializedType.STRING.ordinal());
            out.writeUTF((String) object);
        } else if (object instanceof List) {
            final List list = (List) object;
            if (list.size() <= 0xFF) {
                out.writeByte(SerializedType.LIST_BYTE.ordinal());
                out.writeByte(list.size());
            } else {
                out.writeByte(SerializedType.LIST.ordinal());
                out.writeInt(list.size());
            }
            for (Object listItem : list) {
                serializeObject(listItem, out);
            }
        } else if (object instanceof Map) {
            final Map<?, ?> map = (Map) object;
            if (map.size() <= 0xFF) {
                out.writeByte(SerializedType.MAP_BYTE.ordinal());
                out.writeByte(map.size());
            } else {
                out.writeByte(SerializedType.MAP.ordinal());
                out.writeInt(map.size());
            }
            for (Map.Entry entry : map.entrySet()) {
                out.writeUTF((String)entry.getKey());
                serializeObject(entry.getValue(), out);
            }
        } else if (object instanceof ConfigurationSerializable) {
            final Map<String, Object> serialized = ((ConfigurationSerializable) object).serialize();
            if (serialized.size() <= 0xFF) {
                out.writeByte(SerializedType.CONFIGURATION_SERIALIZABLE_BYTE.ordinal());
                out.writeByte(serialized.size());
            } else {
                out.writeByte(SerializedType.CONFIGURATION_SERIALIZABLE.ordinal());
                out.writeInt(serialized.size());
            }
            //noinspection unchecked
            out.writeUTF(ConfigurationSerialization.getAlias((Class)object.getClass()));

            for (Map.Entry entry : serialized.entrySet()) {
                out.writeUTF((String)entry.getKey());
                serializeObject(entry.getValue(), out);
            }
        } else {
            throw new Exception("Can't serialize "+object+", unsupported type: "+object.getClass());
        }
    }

    @Nullable
    public static Object deserializeObject(@NotNull DataInput in) throws IOException, Exception {
        final int typeByte = in.readUnsignedByte();
        if (typeByte > SerializedType.VALUES.length) {
            throw new Exception("Unknown type: "+typeByte);
        }
        final SerializedType type = SerializedType.VALUES[typeByte];
        switch (type) {
            case NULL:
                return null;
            case PRIMITIVE_BOOLEAN_TRUE:
                return Boolean.TRUE;
            case PRIMITIVE_BOOLEAN_FALSE:
                return Boolean.FALSE;
            case PRIMITIVE_BYTE:
                return in.readByte();
            case PRIMITIVE_CHARACTER:
                return in.readChar();
            case PRIMITIVE_SHORT:
                return in.readShort();
            case PRIMITIVE_INT:
                return in.readInt();
            case PRIMITIVE_LONG:
                return in.readLong();
            case PRIMITIVE_FLOAT:
                return in.readFloat();
            case PRIMITIVE_DOUBLE:
                return in.readDouble();
            case STRING:
                return in.readUTF();
            case LIST_BYTE:
            case LIST: {
                final int length = type == SerializedType.LIST_BYTE ? in.readUnsignedByte() : in.readInt();
                if (length == 0)
                    return Collections.emptyList();
                final ArrayList<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(deserializeObject(in));
                }
                return list;
            }
            case MAP_BYTE:
            case MAP: {
                final int length = type == SerializedType.MAP_BYTE ? in.readUnsignedByte() : in.readInt();
                if (length == 0)
                    return Collections.emptyMap();
                final HashMap<String, Object> map = new HashMap<>(length + length/2);
                for (int i = 0; i < length; i++) {
                    final String key = in.readUTF();
                    final Object value = deserializeObject(in);
                    map.put(key, value);
                }
                return map;
            }
            case CONFIGURATION_SERIALIZABLE_BYTE:
            case CONFIGURATION_SERIALIZABLE: {
                final int size = type == SerializedType.CONFIGURATION_SERIALIZABLE_BYTE ? in.readUnsignedByte() : in.readInt();
                final String alias = in.readUTF();
                final HashMap<String, Object> map = new HashMap<>(size + size / 2);
                for (int i = 0; i < size; i++) {
                    final String key = in.readUTF();
                    final Object value = deserializeObject(in);
                    map.put(key, value);
                }

                try {
                    Class<? extends ConfigurationSerializable> serializedClass = ConfigurationSerialization
                            .getClassByAlias(alias);
                    if (serializedClass == null) {
                        //noinspection unchecked
                        serializedClass = (Class<? extends ConfigurationSerializable>) Class.forName(alias);
                    }

                    final ConfigurationSerializable result = ConfigurationSerialization
                            .deserializeObject(map, serializedClass);
                    if (result == null) {
                        LOG.log(Level.WARNING, "Failed to deserialize "+alias+": "+map);
                    }
                    return result;
                } catch (java.lang.Exception e) {
                    LOG.log(Level.WARNING, "Failed to deserialize "+alias+": "+map, e);
                    return null;
                }
            }
            default:
                LOG.log(Level.SEVERE, "deserializeObject: Branch for type "+type+" is missing!");
                return null;
        }
    }

    @NotNull
    public static final UUID ZERO_UUID = new UUID(0L, 0L);

    public static void serializeUUID(@NotNull UUID uuid, @NotNull DataOutput out) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    @NotNull
    public static UUID deserializeUUID(@NotNull DataInput in) throws IOException {
        final long most = in.readLong();
        final long least = in.readLong();
        if (most == 0L && least == 0L) {
            return ZERO_UUID;
        }
        return new UUID(most, least);
    }

    enum SerializedType {
        NULL,
        PRIMITIVE_BOOLEAN_TRUE,
        PRIMITIVE_BOOLEAN_FALSE,
        PRIMITIVE_BYTE,
        PRIMITIVE_CHARACTER,
        PRIMITIVE_SHORT,
        PRIMITIVE_INT,
        PRIMITIVE_LONG,
        PRIMITIVE_FLOAT,
        PRIMITIVE_DOUBLE,
        STRING,
        /** List with a small number of entries whose amount fit into a byte. */
        LIST_BYTE,
        LIST,
        /** Map with a small number of entries whose amount fit into a byte. */
        MAP_BYTE,
        MAP,
        /** ConfigurationSerializable whose root map has a small number of entries whose amount fit into a byte. */
        CONFIGURATION_SERIALIZABLE_BYTE,
        CONFIGURATION_SERIALIZABLE;

        static final SerializedType[] VALUES = values();
    }

    public static final class Exception extends java.lang.Exception {
        Exception(@NotNull String message) {
            super(message);
        }

        Exception(@NotNull String message, @NotNull Throwable cause) {
            super(message, cause);
        }
    }

}
