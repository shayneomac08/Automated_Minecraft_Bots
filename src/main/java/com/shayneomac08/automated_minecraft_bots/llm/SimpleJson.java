package com.shayneomac08.automated_minecraft_bots.llm;

import com.shayneomac08.automated_minecraft_bots.agent.ActionPlan;

import java.util.*;

public final class SimpleJson {
    private SimpleJson() {}

    public static ActionPlan parseActionPlan(String json) {
        List<ActionPlan.Action> actions = new ArrayList<>();
        if (json == null) return new ActionPlan(actions);

        // TODO: Parse JSON properly
        String type = extractString(json, "\"type\"");
        String text = extractString(json, "\"text\"");
        String goal = extractString(json, "\"goal\"");
        Integer minutes = extractInt(json, "\"minutes\"");

        if (type != null) {
            actions.add(new ActionPlan.Action(type, text, null, null, null, null, null, goal, minutes != null ? minutes.doubleValue() : null));
        }

        return new ActionPlan(actions);
    }

    private static String extractString(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;

        int q1 = s.indexOf('"', i + key.length());
        if (q1 < 0) return null;

        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return null;

        return s.substring(q1 + 1, q2);
    }

    private static Double tryExtractDouble(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;

        int colon = s.indexOf(':', i + key.length());
        if (colon < 0) return null;

        int end = s.indexOf(',', colon);
        if (end < 0) end = s.indexOf('}', colon);
        if (end < 0) return null;

        String raw = s.substring(colon + 1, end).trim();
        raw = raw.replaceAll("[^0-9.\\-]", ""); // strip quotes/spaces
        if (raw.isBlank()) return null;

        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extract(String s, String key) {
        return extractString(s, key);
    }

    private static Double extractDouble(String s, String key) {
        String raw = extractNumberToken(s, key);
        if (raw == null) return null;
        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer extractInt(String s, String key) {
        String raw = extractNumberToken(s, key);
        if (raw == null) return null;
        try {
            // Some models may return "5.0" for ints; tolerate it
            double d = Double.parseDouble(raw);
            return (int) Math.round(d);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractNumberToken(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;

        int colon = s.indexOf(':', i + key.length());
        if (colon < 0) return null;

        int j = colon + 1;

        // skip whitespace
        while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;

        // allow quoted numbers too (rare but happens)
        boolean quoted = (j < s.length() && s.charAt(j) == '"');
        if (quoted) j++;

        int start = j;

        while (j < s.length()) {
            char ch = s.charAt(j);
            if (Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.') {
                j++;
                continue;
            }
            break;
        }

        int end = j;

        if (quoted) {
            // skip until next quote
            int q = s.indexOf('"', end);
            if (q > end) end = q;
        }

        if (end <= start) return null;

        return s.substring(start, end).trim();
    }

    /**
     * Simple JSON serialization for basic objects
     */
    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }

    /**
     * Simple JSON parsing for basic objects
     */
    public static Map<String, Object> parseObject(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return result;

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new RuntimeException("Invalid JSON object");
        }

        // Very simple parser - just extract key-value pairs
        String content = json.substring(1, json.length() - 1);

        // This is a simplified parser - for production use a proper JSON library
        // For now, just extract common patterns
        extractKeyValue(content, "obey", result);
        extractKeyValue(content, "response", result);
        extractKeyValue(content, "choices", result);
        extractKeyValue(content, "candidates", result);

        return result;
    }

    private static void extractKeyValue(String json, String key, Map<String, Object> result) {
        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex < 0) return;

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex < 0) return;

        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length()) return;

        char firstChar = json.charAt(valueStart);

        if (firstChar == '"') {
            // String value
            int endQuote = json.indexOf('"', valueStart + 1);
            if (endQuote > 0) {
                result.put(key, json.substring(valueStart + 1, endQuote));
            }
        } else if (firstChar == 't' || firstChar == 'f') {
            // Boolean value
            if (json.substring(valueStart).startsWith("true")) {
                result.put(key, true);
            } else if (json.substring(valueStart).startsWith("false")) {
                result.put(key, false);
            }
        } else if (firstChar == '[') {
            // Array value - extract the whole array
            int depth = 0;
            int arrayEnd = valueStart;
            for (int i = valueStart; i < json.length(); i++) {
                if (json.charAt(i) == '[') depth++;
                if (json.charAt(i) == ']') {
                    depth--;
                    if (depth == 0) {
                        arrayEnd = i;
                        break;
                    }
                }
            }
            if (arrayEnd > valueStart) {
                // For now, store as a list with a single map
                result.put(key, parseArray(json.substring(valueStart, arrayEnd + 1)));
            }
        } else if (Character.isDigit(firstChar) || firstChar == '-') {
            // Number value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '.' || json.charAt(valueEnd) == '-')) {
                valueEnd++;
            }
            String numStr = json.substring(valueStart, valueEnd);
            try {
                if (numStr.contains(".")) {
                    result.put(key, Double.parseDouble(numStr));
                } else {
                    result.put(key, Integer.parseInt(numStr));
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static List<Map<String, Object>> parseArray(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || !json.startsWith("[") || !json.endsWith("]")) {
            return result;
        }

        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) return result;

        // Simple array parser - split by objects
        int depth = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    String objStr = content.substring(start, i + 1).trim();
                    if (objStr.startsWith("{")) {
                        result.add(parseObject(objStr));
                    }
                    start = i + 1;
                }
            }
        }

        return result;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
