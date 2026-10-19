package com.eric.gaxnbt.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public final class HistoryManager {
    private static final int MAX = 20;
    private final Map<UUID, Deque<Entry>> undo = new HashMap<>();
    private final Map<UUID, Deque<Entry>> redo = new HashMap<>();
    private final com.eric.gaxnbt.GAxnbtPlugin plugin;

    public HistoryManager(com.eric.gaxnbt.GAxnbtPlugin plugin) { this.plugin = plugin; }

    public void record(Player player, Runnable undoAction, Runnable redoAction) {
        UUID id = player.getUniqueId();
        Deque<Entry> u = undo.computeIfAbsent(id, x -> new ArrayDeque<>());
        u.addLast(new Entry(undoAction, redoAction));
        while (u.size() > MAX) u.removeFirst();
        redo.computeIfAbsent(id, x -> new ArrayDeque<>()).clear();
    }

    public boolean undo(Player player) {
        Deque<Entry> u = undo.get(player.getUniqueId());
        if (u == null || u.isEmpty()) return false;
        Entry e = u.removeLast();
        e.undo.run();
        redo.computeIfAbsent(player.getUniqueId(), x -> new ArrayDeque<>()).addLast(e);
        return true;
    }
    public boolean redo(Player player) {
        Deque<Entry> r = redo.get(player.getUniqueId());
        if (r == null || r.isEmpty()) return false;
        Entry e = r.removeLast();
        e.redo.run();
        undo.computeIfAbsent(player.getUniqueId(), x -> new ArrayDeque<>()).addLast(e);
        return true;
    }
    public void clearAll() { undo.clear(); redo.clear(); }

    private record Entry(Runnable undo, Runnable redo) {}
}
