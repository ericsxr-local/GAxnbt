package com.eric.gaxnbt.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class LocalizationManager {
    private final JavaPlugin plugin;
    private final Map<String, String> enchants = new LinkedHashMap<>();
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Map<String, String> operations = new LinkedHashMap<>();
    private final Map<String, String> slots = new LinkedHashMap<>();

    public LocalizationManager(JavaPlugin plugin) { this.plugin = plugin; load(); }

    public void reload() { load(); }

    private void load() {
        enchants.clear(); attributes.clear(); operations.clear(); slots.clear();
        ConfigurationSection e = plugin.getConfig().getConfigurationSection("enchants");
        if (e != null) e.getKeys(false).forEach(k -> enchants.put(k.toLowerCase(Locale.ROOT), e.getString(k, k)));
        ConfigurationSection a = plugin.getConfig().getConfigurationSection("attributes");
        if (a != null) a.getKeys(false).forEach(k -> attributes.put(k.toUpperCase(Locale.ROOT), a.getString(k, k)));
        ConfigurationSection o = plugin.getConfig().getConfigurationSection("attribute-operations");
        if (o != null) o.getKeys(false).forEach(k -> operations.put(k.toUpperCase(Locale.ROOT), o.getString(k, k)));
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("slots");
        if (s != null) s.getKeys(false).forEach(k -> slots.put(k.toUpperCase(Locale.ROOT), s.getString(k, k)));
    }

    public String msg(String path, Map<String, String> vars) {
        String value = plugin.getConfig().getString("messages." + path, path);
        if (vars != null) for (var e : vars.entrySet()) value = value.replace("%" + e.getKey() + "%", e.getValue());
        return color(plugin.getConfig().getString("settings.messages-prefix", "") + value);
    }
    public String msg(String path) { return msg(path, Map.of()); }
    public List<String> help(String page) {
        return plugin.getConfig().getStringList("help." + page).stream().map(LocalizationManager::color).collect(Collectors.toList());
    }
    public String enchantId(String input) {
        String q = input.toLowerCase(Locale.ROOT);
        if (enchants.containsKey(q)) return q;
        for (var e : enchants.entrySet()) if (e.getValue().equalsIgnoreCase(input)) return e.getKey();
        return null;
    }
    public List<String> enchantNames() { return new ArrayList<>(enchants.values()); }
    public List<String> enchantIds() { return new ArrayList<>(enchants.keySet()); }
    public String enchantName(String id) { return enchants.getOrDefault(id.toLowerCase(Locale.ROOT), id); }
    public String attributeId(String input) {
        String q = input.toUpperCase(Locale.ROOT);
        if (attributes.containsKey(q)) return q;
        for (var e : attributes.entrySet()) if (e.getValue().equalsIgnoreCase(input)) return e.getKey();
        return null;
    }
    public List<String> attributeNames() { return new ArrayList<>(attributes.values()); }
    public String attributeName(String id) { return attributes.getOrDefault(id.toUpperCase(Locale.ROOT), id); }
    public String operationId(String input) {
        String q = input.toUpperCase(Locale.ROOT);
        if (operations.containsKey(q)) return q;
        for (var e : operations.entrySet()) if (e.getValue().equalsIgnoreCase(input)) return e.getKey();
        if (q.equals("ADD") || q.equals("加法")) return "ADD_NUMBER";
        if (q.equals("BASE") || q.equals("基础乘法")) return "ADD_SCALAR";
        if (q.equals("TOTAL") || q.equals("总乘法")) return "MULTIPLY_SCALAR_1";
        return null;
    }
    public List<String> operationNames() { return new ArrayList<>(operations.values()); }
    public String slotId(String input) {
        String q = input.toUpperCase(Locale.ROOT);
        if (slots.containsKey(q)) return q;
        for (var e : slots.entrySet()) if (e.getValue().equalsIgnoreCase(input)) return e.getKey();
        return null;
    }
    public List<String> slotNames() { return new ArrayList<>(slots.values()); }
    public String slotName(String id) { return slots.getOrDefault(id.toUpperCase(Locale.ROOT), id); }

    public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
