package com.eric.gaxnbt.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NbtEditorManager {
    public static final String ROOT = "GAxnbt";
    private final com.eric.gaxnbt.GAxnbtPlugin plugin;
    private final LocalizationManager loc;
    private final HistoryManager history;

    public NbtEditorManager(com.eric.gaxnbt.GAxnbtPlugin plugin, LocalizationManager loc, HistoryManager history) {
        this.plugin = plugin; this.loc = loc; this.history = history;
    }

    public ItemStack mainHand(Player p) { return p.getInventory().getItemInMainHand(); }
    public void setMainHand(Player p, ItemStack item) { p.getInventory().setItemInMainHand(item); }

    public boolean enchant(Player p, ItemStack item, String enchantId, int level) {
        if (item == null || item.getType().isAir()) return false;
        ItemStack before = item.clone();
        if (!enchantNoHistory(item, enchantId, level)) return false;
        recordItem(p, before, item.clone());
        return true;
    }

    /** 修改物品附魔，但不写入撤销历史，用于编辑其他玩家/实体装备。 */
    public boolean enchantNoHistory(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType().isAir()) return false;
        if (level < 0 || level > Short.MAX_VALUE) return false;
        try {
            NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> {
                var list = nbt.getCompoundList("Enchantments");
                int found = -1;
                for (int i = 0; i < list.size(); i++) {
                    var c = list.get(i);
                    String id = c.getString("id");
                    if (id != null && id.equals("minecraft:" + enchantId)) { found = i; break; }
                }
                if (found >= 0) {
                    list.get(found).setShort("lvl", (short) level);
                } else {
                    ReadWriteNBT c = list.addCompound();
                    c.setString("id", "minecraft:" + enchantId);
                    c.setShort("lvl", (short) level);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public boolean removeEnchant(Player p, ItemStack item, String id) {
        if (item == null || item.getType().isAir()) return false;
        ItemStack before = item.clone();
        NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> {
            var list = nbt.getCompoundList("Enchantments");
            for (int i = list.size() - 1; i >= 0; i--) {
                String e = list.get(i).getString("id");
                if (e != null && e.equals("minecraft:" + id)) list.remove(i);
            }
        });
        recordItem(p, before, item.clone());
        return true;
    }

    public boolean setName(Player p, ItemStack item, String name) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LocalizationManager.color(name)); item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }
    public boolean loreAdd(Player p, ItemStack item, String text) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(LocalizationManager.color(text)); meta.setLore(lore); item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }
    public boolean loreSet(Player p, ItemStack item, int line, String text) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (line < 1 || line > lore.size()) return false;
        lore.set(line - 1, LocalizationManager.color(text)); meta.setLore(lore); item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }
    public boolean loreRemove(Player p, ItemStack item, int line) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (line < 1 || line > lore.size()) return false;
        lore.remove(line - 1); meta.setLore(lore); item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }
    public boolean loreClear(Player p, ItemStack item) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta(); meta.setLore(null); item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }

    public boolean unbreakable(Player p, ItemStack item, boolean v) { return metaFlag(p, item, "Unbreakable", v); }
    public boolean specialFlag(Player p, ItemStack item, String key, boolean v) { return metaOrNbtFlag(p, item, key, v); }

    private boolean metaFlag(Player p, ItemStack item, String key, boolean v) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta(); meta.setUnbreakable(v); item.setItemMeta(meta); recordItem(p, before, item.clone()); return true;
    }
    private boolean metaOrNbtFlag(Player p, ItemStack item, String key, boolean v) {
        ItemStack before = item.clone();
        NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> { ReadWriteNBT c = nbt.getOrCreateCompound(ROOT); c.setBoolean(key, v); if (!v && c.getKeys().size() <= 1) nbt.removeKey(ROOT); });
        recordItem(p, before, item.clone()); return true;
    }

    public boolean setAttribute(Player p, ItemStack item, String attrId, double amount, String opId, String slotId) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        Attribute attribute;
        try { attribute = Attribute.valueOf(attrId); } catch (IllegalArgumentException ex) { return false; }
        AttributeModifier.Operation operation = switch (opId) {
            case "ADD_SCALAR" -> AttributeModifier.Operation.ADD_SCALAR;
            case "MULTIPLY_SCALAR_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
        EquipmentSlot slot = null;
        if (slotId != null) {
            try { slot = EquipmentSlot.valueOf(slotId); } catch (IllegalArgumentException ignored) {}
        } else {
            slot = inferEquipmentSlot(item.getType());
        }
        // The user asked for editing, so remove previous XNBT modifiers for this attribute/slot, then add one.
        for (AttributeModifier m : new ArrayList<>(meta.getAttributeModifiers(attribute) == null ? List.of() : meta.getAttributeModifiers(attribute))) {
            if (m.getName().startsWith("XNBT_")) meta.removeAttributeModifier(attribute, m);
        }
        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), "XNBT_" + attribute.name(), amount, operation, slot);
        meta.addAttributeModifier(attribute, modifier);
        item.setItemMeta(meta);
        recordItem(p, before, item.clone()); return true;
    }

    public boolean removeAttribute(Player p, ItemStack item, String attrId) {
        ItemStack before = item.clone(); ItemMeta meta = item.getItemMeta();
        Attribute attribute;
        try { attribute = Attribute.valueOf(attrId); } catch (IllegalArgumentException ex) { return false; }
        var mods = meta.getAttributeModifiers(attribute);
        if (mods != null) for (AttributeModifier m : new ArrayList<>(mods)) if (m.getName().startsWith("XNBT_")) meta.removeAttributeModifier(attribute, m);
        item.setItemMeta(meta); recordItem(p, before, item.clone()); return true;
    }

    public boolean rawSet(Player p, ItemStack item, String path, String type, String rawValue) {
        ItemStack before = item.clone();
        try {
            NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> setPath(nbt, path, type, rawValue));
        } catch (Exception ex) { return false; }
        recordItem(p, before, item.clone()); return true;
    }
    public boolean rawRemove(Player p, ItemStack item, String path) {
        ItemStack before = item.clone();
        NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> removePath(nbt, path));
        recordItem(p, before, item.clone()); return true;
    }

    private void setPath(ReadWriteNBT root, String path, String type, String raw) {
        String[] parts = path.split("\\.");
        if (parts.length == 0) throw new IllegalArgumentException("path");
        ReadWriteNBT target = root;
        for (int i = 0; i < parts.length - 1; i++) {
            target = target.getOrCreateCompound(parts[i]);
        }
        String key = parts[parts.length - 1];
        switch (type.toLowerCase(Locale.ROOT)) {
            case "string" -> target.setString(key, raw);
            case "byte" -> target.setByte(key, Byte.parseByte(raw));
            case "short" -> target.setShort(key, Short.parseShort(raw));
            case "int", "integer" -> target.setInteger(key, Integer.parseInt(raw));
            case "long" -> target.setLong(key, Long.parseLong(raw));
            case "float" -> target.setFloat(key, Float.parseFloat(raw));
            case "double" -> target.setDouble(key, Double.parseDouble(raw));
            case "boolean", "bool" -> target.setBoolean(key, Boolean.parseBoolean(raw));
            case "byte_array" -> target.setByteArray(key, parseByteArray(raw));
            case "int_array" -> target.setIntArray(key, parseIntArray(raw));
            case "long_array" -> target.setLongArray(key, parseLongArray(raw));
            case "uuid" -> target.setUUID(key, UUID.fromString(raw));
            case "compound" -> {
                ReadWriteNBT parsed = NBT.parseNBT(raw);
                target.removeKey(key);
                target.getOrCreateCompound(key).mergeCompound(parsed);
            }
            case "string_list", "int_list", "long_list", "float_list", "double_list", "compound_list" -> setTypedList(target, key, type.toLowerCase(Locale.ROOT), raw);
            case "list" -> setTypedList(target, key, detectListType(raw), raw);
            default -> throw new IllegalArgumentException("unknown type");
        }
    }

    private String detectListType(String raw) {
        String x=raw.trim();
        if(x.startsWith("[{")) return "compound_list";
        if(x.contains("\"") || x.contains("'")) return "string_list";
        if(x.matches("\\s*\\[\\s*-?\\d+[lL](?:\\s*,.*)?\\]\\s*")) return "long_list";
        if(x.matches("\\s*\\[\\s*-?\\d+(?:\\s*,.*)?\\]\\s*")) return "int_list";
        if(x.contains(".") || x.toLowerCase(Locale.ROOT).contains("f")) return "double_list";
        return "int_list";
    }

    private void setTypedList(ReadWriteNBT target, String key, String type, String raw) {
        String wrapper = "{v:" + raw + "}";
        ReadWriteNBT parsed = NBT.parseNBT(wrapper);
        target.removeKey(key);
        switch (type) {
            case "string_list" -> {
                var dst = target.getStringList(key);
                for (String value : parsed.getStringList("v")) dst.add(value);
            }
            case "int_list" -> {
                var dst = target.getIntegerList(key);
                for (Integer value : parsed.getIntegerList("v")) dst.add(value);
            }
            case "long_list" -> {
                var dst = target.getLongList(key);
                for (Long value : parsed.getLongList("v")) dst.add(value);
            }
            case "float_list" -> {
                var dst = target.getFloatList(key);
                for (Float value : parsed.getFloatList("v")) dst.add(value);
            }
            case "double_list" -> {
                var dst = target.getDoubleList(key);
                for (Double value : parsed.getDoubleList("v")) dst.add(value);
            }
            case "compound_list" -> {
                var dst = target.getCompoundList(key);
                for (var value : parsed.getCompoundList("v")) dst.addCompound(value);
            }
            default -> throw new IllegalArgumentException("unknown list type");
        }
    }

    private void removePath(ReadWriteNBT root, String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 1) { root.removeKey(parts[0]); return; }
        ReadWriteNBT target = root.resolveCompound(String.join(".", Arrays.copyOf(parts, parts.length - 1)));
        if (target != null) target.removeKey(parts[parts.length - 1]);
    }

    private byte[] parseByteArray(String s) { return Arrays.stream(s.replace("[", "").replace("]", "").split(",")).filter(x -> !x.isBlank()).map(String::trim).mapToInt(Integer::parseInt).collect(() -> new ByteList(), ByteList::addInt, ByteList::merge).toArray(); }
    private int[] parseIntArray(String s) { return Arrays.stream(s.replace("[", "").replace("]", "").split(",")).filter(x -> !x.isBlank()).map(String::trim).mapToInt(Integer::parseInt).toArray(); }
    private long[] parseLongArray(String s) { return Arrays.stream(s.replace("[", "").replace("]", "").split(",")).filter(x -> !x.isBlank()).map(String::trim).mapToLong(Long::parseLong).toArray(); }
    private static final class ByteList { byte[] a = new byte[0]; void addInt(int n) { a = Arrays.copyOf(a, a.length + 1); a[a.length - 1] = (byte)n; } void merge(ByteList o) { for(byte b:o.a) addInt(b); } byte[] toArray(){return a;} }

    private EquipmentSlot inferEquipmentSlot(Material material) {
        String n = material.name();
        if (n.endsWith("_HELMET") || Set.of("CARVED_PUMPKIN","PLAYER_HEAD","CREEPER_HEAD","ZOMBIE_HEAD","SKELETON_SKULL","WITHER_SKELETON_SKULL","DRAGON_HEAD","PIGLIN_HEAD").contains(n)) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE") || n.equals("ELYTRA")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || Set.of("BOW","CROSSBOW","TRIDENT","FISHING_ROD","FLINT_AND_STEEL","SHEARS","SHIELD").contains(n)) return EquipmentSlot.HAND;
        return null;
    }

    public Entity findTarget(Player p, double maxDistance) {
        Entity target = p.getTargetEntity((int) Math.round(maxDistance));
        if (target == null) return null;
        if (target instanceof org.bukkit.entity.Item || target instanceof org.bukkit.entity.ExperienceOrb) return null;
        return target;
    }
    public Entity findEntityByToken(String token) {
        try {
            UUID id = UUID.fromString(token);
            for (var world : plugin.getServer().getWorlds()) for (Entity e : world.getEntities()) if (e.getUniqueId().equals(id)) return e;
        } catch (IllegalArgumentException ignored) {}
        Player player = plugin.getServer().getPlayerExact(token); return player;
    }

    public boolean setEntityHealth(Player actor, LivingEntity target, double hp) {
        double before = target.getHealth(); target.setHealth(Math.max(0, Math.min(hp, Objects.requireNonNull(target.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue())));
        double after = target.getHealth(); actor.sendMessage(loc.msg("changed")); history.record(actor, () -> target.setHealth(Math.min(before, maxHealth(target))), () -> target.setHealth(Math.min(after, maxHealth(target)))); return true;
    }
    public boolean setEntityAttribute(Player actor, LivingEntity target, String attrId, double value) {
        Attribute attr; try { attr = Attribute.valueOf(attrId); } catch (Exception e) { return false; }
        var instance = target.getAttribute(attr); if (instance == null) return false;
        double before = instance.getBaseValue(); instance.setBaseValue(value); history.record(actor, () -> instance.setBaseValue(before), () -> instance.setBaseValue(value)); return true;
    }
    public boolean setEntityState(Player actor, Entity target, String state, boolean value) {
        Runnable undo; Runnable redo;
        switch (state.toLowerCase(Locale.ROOT)) {
            case "invulnerable", "无敌" -> { boolean b = target.isInvulnerable(); target.setInvulnerable(value); undo=()->target.setInvulnerable(b); redo=()->target.setInvulnerable(value); }
            case "gravity", "重力" -> { boolean b = target.hasGravity(); target.setGravity(value); undo=()->target.setGravity(b); redo=()->target.setGravity(value); }
            case "silent", "静音" -> { boolean b = target.isSilent(); target.setSilent(value); undo=()->target.setSilent(b); redo=()->target.setSilent(value); }
            case "glowing", "发光" -> { boolean b = target.isGlowing(); target.setGlowing(value); undo=()->target.setGlowing(b); redo=()->target.setGlowing(value); }
            case "ai" -> { if (!(target instanceof LivingEntity le)) return false; boolean b = le.hasAI(); le.setAI(value); undo=()->le.setAI(b); redo=()->le.setAI(value); }
            default -> { return false; }
        }
        history.record(actor, undo, redo); return true;
    }
    public boolean setEntityName(Player actor, Entity target, String name) {
        String before = target.getCustomName(); boolean b = target.isCustomNameVisible(); target.setCustomName(LocalizationManager.color(name)); target.setCustomNameVisible(true);
        history.record(actor, ()->{target.setCustomName(before); target.setCustomNameVisible(b);}, ()->{target.setCustomName(LocalizationManager.color(name));target.setCustomNameVisible(true);}); return true;
    }
    private double maxHealth(LivingEntity e) { var a=e.getAttribute(Attribute.GENERIC_MAX_HEALTH); return a==null?20:a.getValue(); }

    public String entityNbt(Entity entity) { return NBT.get(entity, (Function<ReadableNBT, String>) ReadableNBT::toString); }
    public boolean rawSetEntity(Player actor, Entity entity, String path, String type, String rawValue) {
        String before = entityNbt(entity);
        try { NBT.modify(entity, (Consumer<ReadWriteNBT>) nbt -> setPath(nbt, path, type, rawValue)); } catch (Exception ex) { return false; }
        String after = entityNbt(entity);
        history.record(actor, () -> restoreEntityNbt(entity, before), () -> restoreEntityNbt(entity, after));
        return true;
    }
    public boolean rawRemoveEntity(Player actor, Entity entity, String path) {
        String before = entityNbt(entity);
        try { NBT.modify(entity, (Consumer<ReadWriteNBT>) nbt -> removePath(nbt, path)); } catch (Exception ex) { return false; }
        String after = entityNbt(entity);
        history.record(actor, () -> restoreEntityNbt(entity, before), () -> restoreEntityNbt(entity, after));
        return true;
    }
    public void restoreEntityNbt(Entity entity, String snbt) {
        ReadWriteNBT parsed = NBT.parseNBT(snbt);
        NBT.modify(entity, (Consumer<ReadWriteNBT>) nbt -> { nbt.clearNBT(); nbt.mergeCompound(parsed); });
    }

    private void recordItem(Player p, ItemStack before, ItemStack after) { history.record(p, () -> setMainHand(p,before.clone()), () -> setMainHand(p,after.clone())); }
}
