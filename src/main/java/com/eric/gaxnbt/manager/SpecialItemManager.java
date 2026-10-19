package com.eric.gaxnbt.manager;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

public final class SpecialItemManager implements Listener {
    private static final String ROOT = "GAxnbt";
    private static final String KEY_SOUL = "Soulbound";
    private static final String KEY_LOCKED = "Locked";
    private final com.eric.gaxnbt.GAxnbtPlugin plugin;
    private final LocalizationManager loc;
    private final Map<UUID, List<SavedSlot>> soulboundOnDeath = new ConcurrentHashMap<>();

    public SpecialItemManager(com.eric.gaxnbt.GAxnbtPlugin plugin, LocalizationManager loc) { this.plugin = plugin; this.loc = loc; }

    public boolean flag(ItemStack item, String key) {
        if (item == null || item.getType().isAir()) return false;
        return NBT.get(item, (Function<ReadableItemNBT, Boolean>) nbt -> {
            ReadableNBT c = nbt.getCompound(ROOT);
            return c != null && Boolean.TRUE.equals(c.getBoolean(key));
        });
    }
    public void setFlag(ItemStack item, String key, boolean value) {
        NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> {
            ReadWriteNBT c = nbt.getOrCreateCompound(ROOT);
            c.setBoolean(key, value);
            if (!value && !c.hasTag(KEY_SOUL) && !c.hasTag(KEY_LOCKED)) nbt.removeKey(ROOT);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (flag(e.getItemDrop().getItemStack(), KEY_LOCKED)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (flag(current, KEY_LOCKED) || flag(cursor, KEY_LOCKED)) { e.setCancelled(true); return; }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int button = e.getHotbarButton();
            if (button >= 0 && flag(e.getWhoClicked().getInventory().getItem(button), KEY_LOCKED)) e.setCancelled(true);
        }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && flag(e.getWhoClicked().getInventory().getItemInOffHand(), KEY_LOCKED)) e.setCancelled(true);
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                || e.getClick() == org.bukkit.event.inventory.ClickType.DROP
                || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP) {
            if (current != null && flag(current, KEY_LOCKED)) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (flag(e.getOldCursor(), KEY_LOCKED)) e.setCancelled(true);
        for (ItemStack i : e.getNewItems().values()) if (flag(i, KEY_LOCKED)) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        List<SavedSlot> save = new ArrayList<>();
        ItemStack[] contents = p.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (flag(item, KEY_SOUL)) {
                save.add(new SavedSlot(slot, item.clone()));
                Iterator<ItemStack> it = e.getDrops().iterator();
                while (it.hasNext()) {
                    ItemStack drop = it.next();
                    if (drop.isSimilar(item) && drop.getAmount() == item.getAmount()) { it.remove(); break; }
                }
            }
        }
        if (!save.isEmpty()) soulboundOnDeath.put(p.getUniqueId(), save);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        List<SavedSlot> save = soulboundOnDeath.remove(e.getPlayer().getUniqueId());
        if (save == null || save.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (SavedSlot s : save) e.getPlayer().getInventory().setItem(s.slot, s.item.clone());
        });
    }

    private record SavedSlot(int slot, ItemStack item) {}
}
