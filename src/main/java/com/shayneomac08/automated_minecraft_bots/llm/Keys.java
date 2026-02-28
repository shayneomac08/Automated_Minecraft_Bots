package com.shayneomac08.automated_minecraft_bots.llm;

import java.io.InputStream;
import java.util.Properties;

public final class Keys {
    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private Keys() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        try (InputStream in = Keys.class.getClassLoader()
                .getResourceAsStream("automated_minecraft_bots.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception ignored) {}
    }

    public static String get(String key) {
        load();
        return PROPS.getProperty(key, "").trim();
    }
}
