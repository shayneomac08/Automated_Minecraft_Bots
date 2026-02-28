package com.shayneomac08.automated_minecraft_bots.bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BotRegistry {
    private static final Map<String, BotPair> BOTS = new ConcurrentHashMap<>();

    private BotRegistry() {}

    public static void put(String name, BotPair pair) {
        BOTS.put(key(name), pair);
    }

    public static BotPair get(String name) {
        return BOTS.get(key(name));
    }

    public static void replace(String name, BotPair pair) {
        BOTS.put(key(name), pair);
    }

    public static BotPair remove(String name) {
        return BOTS.remove(name.toLowerCase());
    }

    public static Iterable<BotPair> all() {
        return BOTS.values();
    }

    public static Iterable<String> getAllBotNames() {
        return BOTS.keySet();
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
