package com.shayneomac08.automated_minecraft_bots.entity;

import java.util.*;

/**
 * Shared memory ledger for bot groups
 * Stores memories that persist across the session (in-memory only)
 */
public class GroupMemoryLedger {
    private final Map<String, List<String>> ledger = new HashMap<>();
    private static final int MAX_MEMORIES_PER_GROUP = 20; // Limit memory size

    public void remember(String group, String memory) {
        List<String> memories = ledger.computeIfAbsent(group, k -> new ArrayList<>());
        memories.add(memory);

        // Keep only the most recent memories to prevent memory bloat
        if (memories.size() > MAX_MEMORIES_PER_GROUP) {
            memories.remove(0);
        }
    }

    public List<String> getMemories(String group) {
        return ledger.getOrDefault(group, Collections.emptyList());
    }

    public void clear() {
        ledger.clear();
    }
}
