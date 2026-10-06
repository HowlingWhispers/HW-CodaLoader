package dev.howlingwhispers.codaloader.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny JSON parser so the loader foundation has zero external runtime dependencies.
 * It supports the complete JSON value model, but is intentionally not a general-purpose library.
 */
public final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        Object value = parser.value();
        parser.ws();
        if (!parser.end()) throw parser.error("Trailing data");
        return value;
    }

    private Object value() {
        ws();
        if (end()) throw error("Expected JSON value");
        return switch (peek()) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        take('{');
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        ws();
        if (tryTake('}')) return map;
        while (true) {
            ws();
            String key = string();
            ws();
            take(':');
            Object val = value();
            map.put(key, val);
            ws();
            if (tryTake('}')) return map;
            take(',');
        }
    }

    private List<Object> array() {
        take('[');
        ArrayList<Object> list = new ArrayList<>();
        ws();
        if (tryTake(']')) return list;
        while (true) {
            list.add(value());
            ws();
            if (tryTake(']')) return list;
            take(',');
        }
    }

    private String string() {
        take('"');
        StringBuilder out = new StringBuilder();
        while (!end()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                if (c < 0x20) throw error("Control character in string");
                out.append(c);
                continue;
            }
            if (end()) throw error("Unterminated escape");
            char e = text.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(unicode());
                default -> throw error("Bad escape: \\" + e);
            }
        }
        throw error("Unterminated string");
    }

    private char unicode() {
        if (pos + 4 > text.length()) throw error("Incomplete unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos++), 16);
            if (digit < 0) throw error("Invalid unicode escape");
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') pos++;
        digits();
        boolean decimal = false;
        if (!end() && peek() == '.') {
            decimal = true;
            pos++;
            digits();
        }
        if (!end() && (peek() == 'e' || peek() == 'E')) {
            decimal = true;
            pos++;
            if (!end() && (peek() == '+' || peek() == '-')) pos++;
            digits();
        }
        String raw = text.substring(start, pos);
        try {
            return decimal ? Double.parseDouble(raw) : Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw error("Invalid number: " + raw);
        }
    }

    private void digits() {
        int start = pos;
        while (!end() && Character.isDigit(peek())) pos++;
        if (start == pos) throw error("Expected digit");
    }

    private Object literal(String raw, Object value) {
        if (!text.startsWith(raw, pos)) throw error("Expected " + raw);
        pos += raw.length();
        return value;
    }

    private void ws() {
        while (!end() && Character.isWhitespace(peek())) pos++;
    }

    private void take(char expected) {
        ws();
        if (end() || text.charAt(pos) != expected) throw error("Expected '" + expected + "'");
        pos++;
    }

    private boolean tryTake(char c) {
        ws();
        if (!end() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private char peek() {
        return text.charAt(pos);
    }

    private boolean end() {
        return pos >= text.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + pos);
    }
}
