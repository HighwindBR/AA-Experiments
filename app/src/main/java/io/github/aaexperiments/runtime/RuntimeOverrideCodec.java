package io.github.aaexperiments.runtime;

public final class RuntimeOverrideCodec {
    private RuntimeOverrideCodec() {}

    public static Object decode(String encoded, Class<?> type) {
        int split = encoded.indexOf(':');
        if (split <= 0) throw new IllegalArgumentException("Missing typed prefix");
        String declared = encoded.substring(0, split), value = encoded.substring(split + 1);
        if (type == boolean.class && declared.equals("BOOLEAN")) return Boolean.parseBoolean(value);
        if (type == byte.class && declared.equals("INT")) return Byte.parseByte(value);
        if (type == short.class && declared.equals("INT")) return Short.parseShort(value);
        if (type == char.class && declared.equals("INT")) return (char) Integer.parseInt(value);
        if (type == int.class && declared.equals("INT")) return Integer.parseInt(value);
        if (type == long.class && declared.equals("LONG")) return Long.parseLong(value);
        if (type == float.class && declared.equals("FLOAT")) return Float.parseFloat(value);
        if (type == double.class && declared.equals("DOUBLE")) return Double.parseDouble(value);
        if (type == String.class && declared.equals("STRING")) return value;
        throw new IllegalArgumentException("Override type does not match method return type");
    }
}
