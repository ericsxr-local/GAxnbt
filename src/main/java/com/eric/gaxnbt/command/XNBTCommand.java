package com.eric.gaxnbt.command;

import com.eric.gaxnbt.GAxnbtPlugin;
import com.eric.gaxnbt.manager.HistoryManager;
import com.eric.gaxnbt.manager.LocalizationManager;
import com.eric.gaxnbt.manager.NbtEditorManager;
import com.eric.gaxnbt.manager.SpecialItemManager;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class XNBTCommand implements CommandExecutor, TabCompleter {
    private final GAxnbtPlugin plugin;
    private final LocalizationManager loc;
    private final NbtEditorManager editor;
    private final HistoryManager history;
    private final SpecialItemManager special;

    public XNBTCommand(GAxnbtPlugin plugin, LocalizationManager loc, NbtEditorManager editor, HistoryManager history, SpecialItemManager special) {
        this.plugin = plugin; this.loc = loc; this.editor = editor; this.history = history; this.special = special;
    }

    private boolean perm(CommandSender s, String node) { return s.hasPermission("xnbt.admin") || s.hasPermission(node); }
    private void send(CommandSender s, String key) { s.sendMessage(loc.msg(key)); }
    private void send(CommandSender s, String key, String k, String v) { s.sendMessage(loc.msg(key, Map.of(k,v))); }
    private boolean player(CommandSender s) { if (s instanceof Player) return true; send(s,"player-only"); return false; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] raw) {
        List<String> a = Arrays.stream(raw).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
        if (a.isEmpty()) { return help(sender, "main"); }
        String sub = a.get(0).toLowerCase(Locale.ROOT);
        try {
            return switch (sub) {
                case "help", "?", "帮助" -> help(sender, a.size()>1 ? a.get(1) : "main");
                case "reload" -> reload(sender);
                case "undo" -> undo(sender);
                case "redo" -> redo(sender);
                case "item" -> item(sender, a.subList(1,a.size()));
                case "enchant" -> item(sender, concat("enchant", a.subList(1,a.size())));
                case "attr", "attribute" -> item(sender, concat("attr", a.subList(1,a.size())));
                case "name" -> item(sender, concat("name", a.subList(1,a.size())));
                case "lore" -> item(sender, concat("lore", a.subList(1,a.size())));
                case "unbreakable" -> item(sender, concat("unbreakable", a.subList(1,a.size())));
                case "soulbound" -> item(sender, concat("soulbound", a.subList(1,a.size())));
                case "locked" -> item(sender, concat("locked", a.subList(1,a.size())));
                case "get" -> item(sender, concat("get", a.subList(1,a.size())));
                case "set" -> item(sender, concat("set", a.subList(1,a.size())));
                case "remove" -> item(sender, concat("remove", a.subList(1,a.size())));
                case "mob" -> mob(sender, a.subList(1,a.size()));
                case "player" -> playerEdit(sender, a.subList(1,a.size()));
                default -> { send(sender,"unknown-command"); yield true; }
            };
        } catch (Exception ex) {
            plugin.getLogger().warning("XNBT command error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            sender.sendMessage(loc.msg("raw-error", Map.of("error", String.valueOf(ex.getMessage()))));
            return true;
        }
    }

    private List<String> concat(String first, List<String> rest) { List<String> x=new ArrayList<>(); x.add(first); x.addAll(rest); return x; }

    private boolean help(CommandSender s, String page) { List<String> lines=loc.help(page); if(lines.isEmpty()) lines=loc.help("main"); for(String l:lines) s.sendMessage(l); return true; }
    private boolean reload(CommandSender s) { if(!perm(s,"xnbt.admin")){send(s,"no-permission");return true;} plugin.reloadXNBT(); send(s,"reload"); return true; }
    private boolean undo(CommandSender s) { if(!player(s)||!perm(s,"xnbt.undo")) {if(s instanceof Player && !perm(s,"xnbt.undo"))send(s,"no-permission"); return true;} if(!history.undo((Player)s))send(s,"nothing-to-undo"); else send(s,"changed"); return true; }
    private boolean redo(CommandSender s) { if(!player(s)||!perm(s,"xnbt.undo")) {if(s instanceof Player && !perm(s,"xnbt.undo"))send(s,"no-permission"); return true;} if(!history.redo((Player)s))send(s,"nothing-to-redo"); else send(s,"changed"); return true; }

    private boolean item(CommandSender sender, List<String> a) {
        if(!perm(sender,"xnbt.item") && !perm(sender,"xnbt.enchant") && !perm(sender,"xnbt.attribute")){send(sender,"no-permission");return true;}
        if(!player(sender))return true;
        Player p=(Player)sender;
        if(a.isEmpty()){help(sender,"main");return true;}
        ItemStack item=editor.mainHand(p);
        if(item==null||item.getType().isAir()){send(sender,"no-item");return true;}
        String op=a.get(0).toLowerCase(Locale.ROOT);
        switch(op){
            case "get" -> { return itemGet(p,item,a.size()>1&&a.get(1).equalsIgnoreCase("raw")); }
            case "enchant" -> { if(!perm(sender,"xnbt.enchant")){send(sender,"no-permission");return true;} if(a.size()<3){return help(sender,"enchant");} String id=loc.enchantId(a.get(1)); if(id==null){send(sender,"invalid-enchant","value",a.get(1));return true;} int level=parseInt(sender,a.get(2)); if(level==Integer.MIN_VALUE)return true; editor.enchant(p,item,id,level); send(sender,"changed"); return true; }
            case "enchantremove", "removeenchant" -> { if(!perm(sender,"xnbt.enchant")){send(sender,"no-permission");return true;} if(a.size()<2)return help(sender,"enchant"); String id=loc.enchantId(a.get(1)); if(id==null){send(sender,"invalid-enchant","value",a.get(1));return true;} editor.removeEnchant(p,item,id); send(sender,"removed"); return true; }
            case "attr", "attribute" -> { if(!perm(sender,"xnbt.attribute")){send(sender,"no-permission");return true;} return attrItem(p,item,a.subList(1,a.size())); }
            case "name" -> { if(!perm(sender,"xnbt.item")){send(sender,"no-permission");return true;} if(a.size()<2){return help(sender,"main");} editor.setName(p,item,String.join(" ",a.subList(1,a.size()))); send(sender,"set"); return true; }
            case "lore" -> { if(!perm(sender,"xnbt.item")){send(sender,"no-permission");return true;} return loreItem(p,item,a.subList(1,a.size())); }
            case "unbreakable" -> { if(!perm(sender,"xnbt.item")){send(sender,"no-permission");return true;} return flagItem(p,item,"unbreakable",a); }
            case "soulbound" -> { if(!perm(sender,"xnbt.item")){send(sender,"no-permission");return true;} return flagItem(p,item,"soulbound",a); }
            case "locked" -> { if(!perm(sender,"xnbt.item")){send(sender,"no-permission");return true;} return flagItem(p,item,"locked",a); }
            case "set" -> { if(!perm(sender,"xnbt.nbt")){send(sender,"no-permission");return true;} if(a.size()<4){shelp(sender,"/xnbt item set <路径> <类型> <值>");return true;} boolean ok=editor.rawSet(p,item,a.get(1),a.get(2),String.join(" ",a.subList(3,a.size()))); if(!ok)send(sender,"raw-error","error","类型、路径或值无效"); else send(sender,"set"); return true; }
            case "remove" -> { if(!perm(sender,"xnbt.nbt")){send(sender,"no-permission");return true;} if(a.size()<2){shelp(sender,"/xnbt item remove <路径>");return true;} editor.rawRemove(p,item,a.get(1)); send(sender,"removed"); return true; }
            default -> { help(sender,"main"); return true; }
        }
    }

    private boolean attrItem(Player p, ItemStack item, List<String> a){
        if(a.isEmpty()){shelp(p,"/xnbt attr <属性> <数值> [操作] [部位]");return true;}
        if(a.get(0).equalsIgnoreCase("list")){p.sendMessage(loc.msg("changed")); for(String n:loc.attributeNames()) p.sendMessage("§7- §f"+n); return true;}
        if(a.get(0).equalsIgnoreCase("remove")){ if(a.size()<2){shelp(p,"/xnbt attr remove <属性>");return true;} String id=loc.attributeId(a.get(1)); if(id==null){send(p,"invalid-attribute","value",a.get(1));return true;} editor.removeAttribute(p,item,id);send(p,"removed");return true; }
        if(a.size()<2){shelp(p,"/xnbt attr <属性> <数值> [操作] [部位]");return true;}
        String id=loc.attributeId(a.get(0)); if(id==null){send(p,"invalid-attribute","value",a.get(0));return true;}
        double value; try{value=Double.parseDouble(a.get(1));}catch(Exception e){send(p,"invalid-number","value",a.get(1));return true;}
        String op=a.size()>2?loc.operationId(a.get(2)):"ADD_NUMBER"; if(op==null){p.sendMessage("§c未知属性操作。可用："+String.join("、",loc.operationNames()));return true;}
        String slot=null; if(a.size()>3){slot=loc.slotId(a.get(3)); if(slot==null){send(p,"invalid-slot","value",a.get(3));return true;}}
        editor.setAttribute(p,item,id,value,op,slot);send(p,"changed");return true;
    }

    private boolean loreItem(Player p, ItemStack item, List<String> a){
        if(a.isEmpty()){shelp(p,"/xnbt lore add|set|remove|clear ...");return true;}
        String op=a.get(0).toLowerCase(Locale.ROOT);
        switch(op){
            case "add"->{if(a.size()<2){shelp(p,"/xnbt lore add <内容>");return true;}editor.loreAdd(p,item,String.join(" ",a.subList(1,a.size())));send(p,"set");}
            case "set"->{if(a.size()<3){shelp(p,"/xnbt lore set <行> <内容>");return true;}int n=parseInt(p,a.get(1));if(n==Integer.MIN_VALUE)return true;if(!editor.loreSet(p,item,n,String.join(" ",a.subList(2,a.size()))))p.sendMessage("§cLore 行号不存在。");else send(p,"set");}
            case "remove"->{if(a.size()<2){shelp(p,"/xnbt lore remove <行>");return true;}int n=parseInt(p,a.get(1));if(n==Integer.MIN_VALUE)return true;if(!editor.loreRemove(p,item,n))p.sendMessage("§cLore 行号不存在。");else send(p,"removed");}
            case "clear"->{editor.loreClear(p,item);send(p,"removed");}
            default->shelp(p,"/xnbt lore add|set|remove|clear ...");
        } return true;
    }

    private boolean flagItem(Player p, ItemStack item, String type, List<String> a){
        if(a.size()<2){shelp(p,"/xnbt "+type+" <true|false>");return true;}Boolean v=parseBoolean(p,a.get(1));if(v==null)return true;
        if(type.equals("unbreakable"))editor.unbreakable(p,item,v);else if(type.equals("soulbound"))editor.specialFlag(p,item,"Soulbound",v);else editor.specialFlag(p,item,"Locked",v);
        send(p,"set");return true;
    }

    private boolean itemGet(Player p, ItemStack item, boolean raw){
        if(raw){try{String snbt=NBT.get(item, (Function<ReadableItemNBT, String>) Object::toString); p.sendMessage("§6XNBT Raw NBT"); for(String line : wrap(snbt,240))p.sendMessage("§7"+line); }catch(Exception e){send(p,"raw-error","error",String.valueOf(e.getMessage()));}return true;}
        ItemMeta meta=item.getItemMeta(); StringBuilder b=new StringBuilder();b.append("{");
        b.append("\n  \"物品\":\"").append(item.getType().name()).append("\","); b.append("\n  \"数量\":").append(item.getAmount());
        if(meta!=null && meta.hasDisplayName())b.append(",\n  \"名称\":\"").append(escape(ChatColor.stripColor(meta.getDisplayName()))).append("\"");
        Map<String,Integer> en=new LinkedHashMap<>();for(var e:meta.getEnchants().entrySet())en.put(loc.enchantName(e.getKey().getKey().getKey()),e.getValue()); if(!en.isEmpty()){b.append(",\n  \"附魔\":{");int i=0;for(var e:en.entrySet()){if(i++>0)b.append(",");b.append("\""+escape(e.getKey())+"\":"+e.getValue());}b.append("}");}
        if(meta!=null&&meta.hasAttributeModifiers()){b.append(",\n  \"属性\":{ ");int i=0;for(Attribute at:meta.getAttributeModifiers().keySet()){var mods=meta.getAttributeModifiers(at);if(mods==null)continue;double sum=0;for(var m:mods)sum+=m.getAmount();if(i++>0)b.append(", ");b.append("\""+escape(loc.attributeName(at.name()))+"\":"+trimDouble(sum));}b.append(" }");}
        boolean ub=meta!=null&&meta.isUnbreakable();boolean soul=special.flag(item,"Soulbound");boolean locked=special.flag(item,"Locked");b.append(",\n  \"不可破坏\":").append(ub).append(",\n  \"死亡不掉落\":").append(soul).append(",\n  \"不可移动\":").append(locked);
        Set<String> extra=NBT.get(item, (Function<ReadableItemNBT, Set<String>>) nbt->new TreeSet<>(nbt.getKeys())); extra.removeAll(Set.of("id","Count","tag","Enchantments","AttributeModifiers","Unbreakable","Damage")); if(!extra.isEmpty()){b.append(",\n  \"其他NBT\":[");int i=0;for(String k:extra){if(i++>=plugin.getConfig().getInt("settings.compact-raw-max-keys",10))break;if(i>1)b.append(",");b.append("\""+escape(k)+"\"");}b.append("]");}
        b.append("\n}"); for(String line:b.toString().split("\n"))p.sendMessage("§7"+line);return true;
    }

    private boolean mob(CommandSender sender,List<String>a){
        if(!perm(sender,"xnbt.mob")){send(sender,"no-permission");return true;} if(!player(sender))return true; Player actor=(Player)sender;
        if(a.isEmpty()){help(sender,"mob");return true;}
        int index=0; Entity target=null; if(isTargetToken(a.get(0))){target=editor.findEntityByToken(a.get(0));if(target!=null)index=1;}
        if(target==null)target=editor.findTarget(actor,plugin.getConfig().getDouble("settings.max-target-distance",20));
        if(target==null){send(sender,"invalid-target");return true;}
        if(index>=a.size()){help(sender,"mob");return true;}
        String op=a.get(index).toLowerCase(Locale.ROOT); List<String> rest=a.subList(index+1,a.size());
        if(op.equals("get")){boolean raw=rest.size()>0&&rest.get(0).equalsIgnoreCase("raw");if(raw){pRaw(actor,target);}else mobSummary(actor,target);return true;}
        if(op.equals("set")){if(rest.size()<3){shelp(sender,"/xnbt mob set <路径> <类型> <值>");return true;}boolean ok=editor.rawSetEntity(actor,target,rest.get(0),rest.get(1),String.join(" ",rest.subList(2,rest.size())));if(!ok)send(sender,"raw-error","error","类型、路径或值无效");else send(sender,"set");return true;}
        if(op.equals("remove")){if(rest.isEmpty()){shelp(sender,"/xnbt mob remove <路径>");return true;}editor.rawRemoveEntity(actor,target,rest.get(0));send(sender,"removed");return true;}
        if(op.equals("name")){if(rest.isEmpty()){shelp(sender,"/xnbt mob name <名称>");return true;}editor.setEntityName(actor,target,String.join(" ",rest));send(sender,"set");return true;}
        if(!(target instanceof LivingEntity le)){actor.sendMessage("§c目标不是生物，无法修改生命/属性。");return true;}
        if(op.equals("health")){if(rest.isEmpty()){shelp(sender,"/xnbt mob health <数值>");return true;}double v=parseDouble(sender,rest.get(0));if(Double.isNaN(v))return true;editor.setEntityHealth(actor,le,v);send(sender,"set");return true;}
        if(op.equals("maxhealth")){if(rest.isEmpty()){shelp(sender,"/xnbt mob maxhealth <数值>");return true;}double v=parseDouble(sender,rest.get(0));if(Double.isNaN(v)||v<=0)return true;var at=le.getAttribute(Attribute.GENERIC_MAX_HEALTH);if(at==null){send(sender,"invalid-attribute","value","最大生命");return true;}double old=at.getBaseValue();double oldHealth=le.getHealth();at.setBaseValue(v);if(le.getHealth()>v)le.setHealth(v);history.record(actor,()->{at.setBaseValue(old);le.setHealth(Math.min(oldHealth,at.getValue()));},()->{at.setBaseValue(v);if(le.getHealth()>v)le.setHealth(v);});send(sender,"set");return true;}
        if(op.equals("attr")||op.equals("attribute")){if(rest.size()<2){shelp(sender,"/xnbt mob attr <属性> <数值>");return true;}String id=loc.attributeId(rest.get(0));if(id==null){send(sender,"invalid-attribute","value",rest.get(0));return true;}double v=parseDouble(sender,rest.get(1));if(Double.isNaN(v))return true;if(!editor.setEntityAttribute(actor,le,id,v)){actor.sendMessage("§c该生物不支持这个属性。");return true;}send(sender,"set");return true;}
        if(op.equals("speed")||op.equals("damage")||op.equals("armor")){String id=switch(op){case "speed"->"GENERIC_MOVEMENT_SPEED";case "damage"->"GENERIC_ATTACK_DAMAGE";default->"GENERIC_ARMOR";};if(rest.isEmpty()){shelp(sender,"/xnbt mob "+op+" <数值>");return true;}double v=parseDouble(sender,rest.get(0));if(Double.isNaN(v))return true;if(!editor.setEntityAttribute(actor,le,id,v)){actor.sendMessage("§c该生物不支持这个属性。");return true;}send(sender,"set");return true;}
        if(Set.of("ai","invulnerable","gravity","silent","glowing").contains(op)){if(rest.isEmpty()){shelp(sender,"/xnbt mob "+op+" <true|false>");return true;}Boolean v=parseBoolean(actor,rest.get(0));if(v==null)return true;if(!editor.setEntityState(actor,target,op,v)){actor.sendMessage("§c该实体不支持这个选项。");return true;}send(sender,"set");return true;}
        if(op.equals("equipment")||op.equals("equip")){return mobEquip(actor,le,rest);}
        help(sender,"mob");return true;
    }

    private boolean isTargetToken(String s){return s.contains("-")||plugin.getServer().getPlayerExact(s)!=null;}
    private void mobSummary(Player p,Entity e){p.sendMessage("§6XNBT 生物信息");p.sendMessage("§7{ \"类型\": \""+e.getType().name()+"\", \"UUID\": \""+e.getUniqueId()+"\"");if(e instanceof LivingEntity le){p.sendMessage("§7  \"生命\": "+trimDouble(le.getHealth())+", \"最大生命\": "+trimDouble(le.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));for(var x:List.of(Attribute.GENERIC_MOVEMENT_SPEED,Attribute.GENERIC_ATTACK_DAMAGE,Attribute.GENERIC_ARMOR)){var at=le.getAttribute(x);if(at!=null)p.sendMessage("§7  \""+loc.attributeName(x.name())+"\": "+trimDouble(at.getValue()));}p.sendMessage("§7  \"AI\": "+le.hasAI()+", \"无敌\": "+e.isInvulnerable()+", \"重力\": "+e.hasGravity()+", \"静音\": "+e.isSilent()+", \"发光\": "+e.isGlowing());}p.sendMessage("§7}");}
    private void pRaw(Player p,Entity e){String snbt=editor.entityNbt(e);p.sendMessage("§6XNBT Raw NBT");for(String l:wrap(snbt,240))p.sendMessage("§7"+l);}

    private boolean mobEquip(Player actor, LivingEntity target, List<String> rest) {
        if (rest.isEmpty()) {
            shelp(actor, "/xnbt mob equipment <部位> set|clear|get");
            return true;
        }
        String slot = loc.slotId(rest.get(0));
        if (slot == null) {
            send(actor, "invalid-slot", "value", rest.get(0));
            return true;
        }
        PlayerInventory pi = actor.getInventory();
        ItemStack source = pi.getItemInMainHand();
        var eq = target.getEquipment();
        if (eq == null) return true;
        String op = rest.size() > 1 ? rest.get(1).toLowerCase(Locale.ROOT) : "get";
        switch (op) {
            case "get" -> {
                ItemStack i = getEquip(eq, slot);
                if (i == null || i.getType().isAir()) actor.sendMessage("§7该部位为空。");
                else itemGet(actor, i, false);
            }
            case "set" -> {
                if (source.getType().isAir()) { send(actor, "no-item"); return true; }
                ItemStack before = getEquip(eq, slot);
                ItemStack after = source.clone();
                setEquip(eq, slot, after);
                ItemStack beforeFinal = before == null ? null : before.clone();
                ItemStack afterFinal = after.clone();
                history.record(actor, () -> setEquip(eq, slot, beforeFinal == null ? null : beforeFinal.clone()), () -> setEquip(eq, slot, afterFinal.clone()));
                send(actor, "set");
            }
            case "clear" -> {
                ItemStack before = getEquip(eq, slot);
                setEquip(eq, slot, null);
                ItemStack beforeFinal = before == null ? null : before.clone();
                history.record(actor, () -> setEquip(eq, slot, beforeFinal == null ? null : beforeFinal.clone()), () -> setEquip(eq, slot, null));
                send(actor, "removed");
            }
            default -> shelp(actor, "/xnbt mob equipment <部位> set|clear|get");
        }
        return true;
    }

    private ItemStack getEquip(org.bukkit.inventory.EntityEquipment eq,String slot){return switch(slot){case "HAND"->eq.getItemInMainHand();case "OFF_HAND"->eq.getItemInOffHand();case "HEAD"->eq.getHelmet();case "CHEST"->eq.getChestplate();case "LEGS"->eq.getLeggings();case "FEET"->eq.getBoots();default->null;};}
    private void setEquip(org.bukkit.inventory.EntityEquipment eq,String slot,ItemStack i){switch(slot){case "HAND"->eq.setItemInMainHand(i);case "OFF_HAND"->eq.setItemInOffHand(i);case "HEAD"->eq.setHelmet(i);case "CHEST"->eq.setChestplate(i);case "LEGS"->eq.setLeggings(i);case "FEET"->eq.setBoots(i);}}

    private boolean playerEdit(CommandSender sender,List<String>a){if(!perm(sender,"xnbt.player")){send(sender,"no-permission");return true;}if(a.size()<2){shelp(sender,"/xnbt player <玩家> <参数>");return true;}Player target=plugin.getServer().getPlayerExact(a.get(0));if(target==null){send(sender,"player-not-found","player",a.get(0));return true;}if(!(sender instanceof Player actor)){send(sender,"player-only");return true;}String op=a.get(1).toLowerCase(Locale.ROOT);List<String> r=a.subList(2,a.size());
        switch(op){case "health"->{if(r.isEmpty()){shelp(sender,"/xnbt player <玩家> health <数值>");return true;}double v=parseDouble(sender,r.get(0));if(Double.isNaN(v))return true;double old=target.getHealth();double max=target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();double applied=Math.max(0,Math.min(v,max));target.setHealth(applied);history.record(actor,()->target.setHealth(Math.min(old,target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())),()->target.setHealth(Math.min(applied,target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())));send(sender,"set");}
            case "maxhealth"->{if(r.isEmpty()){shelp(sender,"/xnbt player <玩家> maxhealth <数值>");return true;}double v=parseDouble(sender,r.get(0));if(Double.isNaN(v)||v<=0)return true;var at=target.getAttribute(Attribute.GENERIC_MAX_HEALTH);double old=at.getBaseValue();double oldHealth=target.getHealth();at.setBaseValue(v);if(target.getHealth()>v)target.setHealth(v);history.record(actor,()->{at.setBaseValue(old);target.setHealth(Math.min(oldHealth,at.getValue()));},()->{at.setBaseValue(v);if(target.getHealth()>v)target.setHealth(v);});send(sender,"set");}
            case "food", "hunger"->{if(r.isEmpty()){shelp(sender,"/xnbt player <玩家> food <0-20>");return true;}int parsed=parseInt(sender,r.get(0));if(parsed==Integer.MIN_VALUE)return true;final int v=Math.max(0,Math.min(20,parsed));final int old=target.getFoodLevel();target.setFoodLevel(v);history.record(actor,()->target.setFoodLevel(old),()->target.setFoodLevel(v));send(sender,"set");}
            case "level"->{if(r.isEmpty()){shelp(sender,"/xnbt player <玩家> level <等级>");return true;}int v=parseInt(sender,r.get(0));if(v==Integer.MIN_VALUE)return true;int old=target.getLevel();target.setLevel(Math.max(0,v));history.record(actor,()->target.setLevel(old),()->target.setLevel(v));send(sender,"set");}
            case "exp", "experience"->{if(r.isEmpty()){shelp(sender,"/xnbt player <玩家> exp <经验值>");return true;}int v=parseInt(sender,r.get(0));if(v==Integer.MIN_VALUE)return true;int old=target.getTotalExperience();target.setTotalExperience(Math.max(0,v));history.record(actor,()->target.setTotalExperience(old),()->target.setTotalExperience(v));send(sender,"set");}
            case "attr", "attribute"->{if(r.size()<2){shelp(sender,"/xnbt player <玩家> attr <属性> <数值>");return true;}String id=loc.attributeId(r.get(0));if(id==null){send(sender,"invalid-attribute","value",r.get(0));return true;}double v=parseDouble(sender,r.get(1));if(Double.isNaN(v))return true;if(!editor.setEntityAttribute(actor,target,id,v)){target.sendMessage("§c该玩家不支持这个属性。");return true;}send(sender,"set");}
            case "equipment", "equip"->{return playerEquipment(actor,target,r);}
            default->{shelp(sender,"/xnbt player <玩家> health|maxhealth|food|exp|level|attr|equipment ...");}
        } return true;
    }

    private boolean playerEquipment(Player actor,Player target,List<String>r){if(r.isEmpty()){shelp(actor,"/xnbt player <玩家> equipment <部位> get|set|clear");return true;}String slot=loc.slotId(r.get(0));if(slot==null){send(actor,"invalid-slot","value",r.get(0));return true;}var eq=target.getEquipment();String op=r.size()>1?r.get(1).toLowerCase(Locale.ROOT):"get";switch(op){case "get"->{ItemStack i=getEquip(eq,slot);if(i==null||i.getType().isAir())actor.sendMessage("§7该部位为空。");else itemGet(actor,i,false);}case "set"->{ItemStack i=actor.getInventory().getItemInMainHand();if(i.getType().isAir()){send(actor,"no-item");return true;}ItemStack before=getEquip(eq,slot);ItemStack after=i.clone();setEquip(eq,slot,after);ItemStack beforeFinal=before==null?null:before.clone(),afterFinal=after.clone();history.record(actor,()->setEquip(eq,slot,beforeFinal==null?null:beforeFinal.clone()),()->setEquip(eq,slot,afterFinal.clone()));send(actor,"set");}case "clear"->{ItemStack before=getEquip(eq,slot);setEquip(eq,slot,null);ItemStack beforeFinal=before==null?null:before.clone();history.record(actor,()->setEquip(eq,slot,beforeFinal==null?null:beforeFinal.clone()),()->setEquip(eq,slot,null));send(actor,"removed");}case "enchant"->{if(r.size()<4){shelp(actor,"/xnbt player <玩家> equipment <部位> enchant <附魔> <等级>");return true;}ItemStack i=getEquip(eq,slot);if(i==null||i.getType().isAir()){send(actor,"no-item");return true;}String id=loc.enchantId(r.get(2));if(id==null){send(actor,"invalid-enchant","value",r.get(2));return true;}int lv=parseInt(actor,r.get(3));if(lv==Integer.MIN_VALUE)return true;ItemStack before=i.clone();if(!editor.enchantNoHistory(i,id,lv)){send(actor,"raw-error","error","附魔修改失败");return true;}ItemStack after=i.clone();setEquip(eq,slot,after);ItemStack beforeFinal=before.clone(),afterFinal=after.clone();history.record(actor,()->setEquip(eq,slot,beforeFinal.clone()),()->setEquip(eq,slot,afterFinal.clone()));send(actor,"set");}default->shelp(actor,"/xnbt player <玩家> equipment <部位> get|set|clear|enchant ...");}return true;}

    private int parseInt(CommandSender s,String v){try{return Integer.parseInt(v);}catch(Exception e){send(s,"invalid-number","value",v);return Integer.MIN_VALUE;}}
    private double parseDouble(CommandSender s,String v){try{return Double.parseDouble(v);}catch(Exception e){send(s,"invalid-number","value",v);return Double.NaN;}}
    private Boolean parseBoolean(CommandSender s,String v){if(v.equalsIgnoreCase("true"))return true;if(v.equalsIgnoreCase("false"))return false;send(s,"invalid-boolean");return null;}
    private void shelp(CommandSender s,String line){s.sendMessage("§7格式：§f"+line);}
    private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private String trimDouble(double d){return Math.rint(d)==d?Long.toString((long)d):Double.toString(d);}
    private List<String> wrap(String s,int max){List<String> out=new ArrayList<>();for(int i=0;i<s.length();i+=max)out.add(s.substring(i,Math.min(s.length(),i+max)));return out;}

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] raw) {
        List<String> a=Arrays.asList(raw); String partial=raw.length==0?"":raw[raw.length-1];
        List<String> out=new ArrayList<>();
        if(raw.length==1){out.addAll(List.of("help","item","enchant","attr","name","lore","unbreakable","soulbound","locked","get","set","remove","mob","player","undo","redo"));return filter(out,partial);}
        String root=raw[0].toLowerCase(Locale.ROOT);
        if(root.equals("help")){if(raw.length==2)out.addAll(List.of("main","enchant","attr","mob","player"));return filter(out,partial);}
        if(root.equals("enchant")){return enchantTab(raw,1,partial);}
        if(root.equals("get")&&raw.length==2)return filter(List.of("raw"),partial);
        if(Set.of("unbreakable","soulbound","locked").contains(root)&&raw.length==2)return filter(List.of("true","false"),partial);
        if(root.equals("lore")&&raw.length==2)return filter(List.of("add","set","remove","clear"),partial);
        if(root.equals("attr")||root.equals("attribute")){return attrTab(raw,1,partial);}
        if(root.equals("item")){if(raw.length==2)return filter(List.of("get","set","remove","enchant","enchantremove","removeenchant","attr","attribute","name","lore","unbreakable","soulbound","locked"),partial);String sub=raw[1].toLowerCase(Locale.ROOT);if(sub.equals("get")&&raw.length==3)return filter(List.of("raw"),partial);if(sub.equals("enchant")||sub.equals("enchantremove")||sub.equals("removeenchant"))return enchantTab(raw,2,partial);if(sub.equals("attr")||sub.equals("attribute"))return attrTab(raw,2,partial);if(sub.equals("set"))return rawSetTab(sender,raw,2,partial);if(sub.equals("remove"))return nbtPathTab(sender,raw,2,partial);if(Set.of("unbreakable","soulbound","locked").contains(sub)&&raw.length==3)return filter(List.of("true","false"),partial);if(sub.equals("lore")&&raw.length==3)return filter(List.of("add","set","remove","clear"),partial);return List.of();}
        if(root.equals("set")){return rawSetTab(sender,raw,1,partial);}if(root.equals("remove"))return nbtPathTab(sender,raw,1,partial);
        if(root.equals("mob")){if(raw.length==2){List<String> first=new ArrayList<>();first.addAll(List.of("get","set","remove","name","health","maxhealth","speed","damage","armor","attr","attribute","ai","invulnerable","gravity","silent","glowing","equipment","equip"));first.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());return filter(first,partial);}return mobTab(sender,raw,1,partial);}
        if(root.equals("player")){if(raw.length==2){out.addAll(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList());return filter(out,partial);}if(raw.length==3)return filter(List.of("health","maxhealth","food","hunger","exp","experience","level","attr","attribute","equipment","equip"),partial);if(raw.length>=4&&(raw[2].equalsIgnoreCase("equipment")||raw[2].equalsIgnoreCase("equip"))){if(raw.length==4)return filter(loc.slotNames(),partial);if(raw.length==5)return filter(List.of("get","set","clear","enchant"),partial);if(raw.length==6&&raw[4].equalsIgnoreCase("enchant"))return enchantTab(raw,5,partial);}return List.of();}
        return List.of();
    }

    private List<String> mobTab(CommandSender sender,String[] raw,int base,String partial){
        // /xnbt mob <operation> ... OR /xnbt mob <player/uuid> <operation> ...
        int offset=base;
        if(raw.length>base && isTargetToken(raw[base])) offset=base+1;
        if(raw.length==offset+1) return filter(List.of("get","set","remove","name","health","maxhealth","speed","damage","armor","attr","attribute","ai","invulnerable","gravity","silent","glowing","equipment","equip"),partial);
        if(raw.length==offset+2 && Set.of("set","remove").contains(raw[offset].toLowerCase(Locale.ROOT)) && sender instanceof Player p){
            if(raw[offset].equalsIgnoreCase("set")) return filter(nbtPaths(p.getInventory().getItemInMainHand(),partial),partial);
            return filter(itemNbtRootPaths(p.getInventory().getItemInMainHand()),partial);
        }
        if(raw.length==offset+3 && raw[offset].equalsIgnoreCase("set")) return filter(List.of("string","byte","short","int","integer","long","float","double","boolean","bool","byte_array","int_array","long_array","uuid","compound","list","string_list","int_list","long_list","float_list","double_list","compound_list"),partial);
        if((raw[offset].equalsIgnoreCase("equipment")||raw[offset].equalsIgnoreCase("equip")) && raw.length==offset+2) return filter(loc.slotNames(),partial);
        if((raw[offset].equalsIgnoreCase("equipment")||raw[offset].equalsIgnoreCase("equip")) && raw.length==offset+3) return filter(List.of("get","set","clear"),partial);
        if(Set.of("ai","invulnerable","gravity","silent","glowing").contains(raw[offset].toLowerCase(Locale.ROOT)) && raw.length==offset+2) return filter(List.of("true","false"),partial);
        return List.of();
    }

    private List<String> enchantTab(String[] raw,int base,String partial){int pos=raw.length-base;if(pos<=1){List<String> all=new ArrayList<>(loc.enchantNames());all.addAll(loc.enchantIds());return filter(all,partial);}return List.of();}
    private List<String> attrTab(String[] raw,int base,String partial){if(raw.length==base+1)return filter(loc.attributeNames(),partial);if(raw.length==base+2)return List.of();if(raw.length==base+3)return filter(loc.operationNames(),partial);if(raw.length==base+4)return filter(loc.slotNames(),partial);return List.of();}
    private List<String> rawSetTab(CommandSender sender,String[] raw,int base,String partial){if(sender instanceof Player p){if(raw.length==base+1)return filter(nbtPaths(p.getInventory().getItemInMainHand(),partial),partial);if(raw.length==base+2)return filter(List.of("string","byte","short","int","integer","long","float","double","boolean","bool","byte_array","int_array","long_array","uuid","compound","list","string_list","int_list","long_list","float_list","double_list","compound_list"),partial);}return List.of();}
    private List<String> nbtPathTab(CommandSender sender,String[] raw,int base,String partial){
        if(!(sender instanceof Player p))return List.of();
        ItemStack item=p.getInventory().getItemInMainHand();
        if(item==null||item.getType().isAir())return List.of();
        return nbtPaths(item,partial);
    }
    private List<String> itemNbtRootPaths(ItemStack item){
        if(item==null||item.getType().isAir())return List.of();
        try{return new ArrayList<>(NBT.get(item, (Function<ReadableItemNBT, Set<String>>) nbt -> nbt.getKeys()));}catch(Exception e){return List.of();}
    }
    private List<String> nbtPaths(ItemStack item,String partial){
        try {
            de.tr7zw.changeme.nbtapi.NBTItem root=new de.tr7zw.changeme.nbtapi.NBTItem(item);
            String prefix=""; String leaf=partial; int dot=partial.lastIndexOf('.');
            if(dot>=0){prefix=partial.substring(0,dot+1); leaf=partial.substring(dot+1);}
            ReadableNBT parent=root;
            if(!prefix.isEmpty()) {
                String path=prefix.substring(0,prefix.length()-1);
                parent=root.resolveCompound(path);
                if(parent==null)return List.of();
            }
            String finalPrefix=prefix;
            return parent.getKeys().stream().map(k->finalPrefix+k).filter(k->k.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))).sorted().limit(80).toList();
        } catch(Exception e){return List.of();}
    }
    private List<String> filter(List<String> values,String p){return values.stream().distinct().filter(x->x.toLowerCase(Locale.ROOT).startsWith(p.toLowerCase(Locale.ROOT))).sorted().limit(80).toList();}
}
