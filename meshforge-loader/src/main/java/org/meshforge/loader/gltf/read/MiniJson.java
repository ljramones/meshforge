package org.meshforge.loader.gltf.read;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for loader-side glTF parsing without adding dependencies.
 */
public final class MiniJson {
    private MiniJson() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = new Parser(json).parse();
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected root JSON object");
        }
        return (Map<String, Object>) map;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        Object parse() {
            skipWs();
            Object value = parseValue();
            skipWs();
            if (i != s.length()) {
                throw error("Trailing data");
            }
            return value;
        }

        private Object parseValue() {
            skipWs();
            if (i >= s.length()) {
                throw error("Unexpected end of JSON");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWs();
            Map<String, Object> out = new LinkedHashMap<>();
            if (peek('}')) {
                i++;
                return out;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                out.put(key, value);
                skipWs();
                if (peek('}')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return out;
        }

        private List<Object> parseArray() {
            expect('[');
            skipWs();
            List<Object> out = new ArrayList<>();
            if (peek(']')) {
                i++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWs();
                if (peek(']')) {
                    i++;
                    break;
                }
                expect(',');
            }
            return out;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        throw error("Invalid escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error("Invalid unicode escape");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw error("Unsupported escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) {
                i++;
            }
            consumeDigits();
            if (peek('.')) {
                i++;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                i++;
                if (peek('+') || peek('-')) {
                    i++;
                }
                consumeDigits();
            }
            String raw = s.substring(start, i);
            if (raw.isEmpty() || "-".equals(raw)) {
                throw error("Invalid number");
            }
            return Double.parseDouble(raw);
        }

        private Object parseLiteral(String literal, Object value) {
            if (i + literal.length() > s.length() || !s.regionMatches(i, literal, 0, literal.length())) {
                throw error("Expected literal: " + literal);
            }
            i += literal.length();
            return value;
        }

        private void consumeDigits() {
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (start == i) {
                throw error("Expected digits");
            }
        }

        private void expect(char c) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != c) {
                throw error("Expected '" + c + "'");
            }
            i++;
        }

        private boolean peek(char c) {
            return i < s.length() && s.charAt(i) == c;
        }

        private void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    break;
                }
            }
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " at index " + i);
        }
    }
}
