package io.github.highwindbr.aaxp.runtime;

public final class RuntimeOverrideCodec {
    private RuntimeOverrideCodec() {}

    public static Object decode(String encoded, Class<?> type) {
        int split = encoded.indexOf(':');
        if (split <= 0) throw new IllegalArgumentException("Missing typed prefix");
        String declared = encoded.substring(0, split), value = encoded.substring(split + 1);
        if (type == boolean.class && declared.equals("BOOLEAN")) {
            if (!"true".equals(value) && !"false".equals(value))
                throw new IllegalArgumentException("Invalid BOOLEAN value");
            return Boolean.valueOf(value);
        }
        if (type == byte.class && declared.equals("INT")) return Byte.parseByte(value);
        if (type == short.class && declared.equals("INT")) return Short.parseShort(value);
        if (type == char.class && declared.equals("INT")) {
            int codepoint = Integer.parseInt(value);
            if (codepoint < Character.MIN_VALUE || codepoint > Character.MAX_VALUE)
                throw new IllegalArgumentException("INT value is outside char range");
            return (char) codepoint;
        }
        if (type == int.class && declared.equals("INT")) return Integer.parseInt(value);
        if (type == long.class && declared.equals("LONG")) return Long.parseLong(value);
        if (type == float.class && declared.equals("FLOAT")) {
            float result = Float.parseFloat(value);
            if (!Float.isFinite(result)) throw new IllegalArgumentException("Non-finite FLOAT");
            return result;
        }
        if (type == double.class && declared.equals("DOUBLE")) {
            double result = Double.parseDouble(value);
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite DOUBLE");
            return result;
        }
        if (type == String.class && declared.equals("STRING")) return value;
        throw new IllegalArgumentException("Override type does not match method return type");
    }
}
