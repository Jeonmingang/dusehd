package com.minkang.ultimate.servergui;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

public class ServerGUIPlugin extends JavaPlugin implements Listener {

    private Component menuTitle;
    private int menuSize;
    private java.util.List<ServerButton> buttons;
    private java.util.Set<Integer> linkedNpcIds;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    @Override
    public void onDisable() {
        saveNpcLinks();
    }

    // /서버 [열기|연동|리로드]
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("서버") && !cmd.getName().equalsIgnoreCase("server")) return false;

        if (args.length == 0 || args[0].equalsIgnoreCase("열기")) {
            if (sender instanceof Player p) openMenu(p);
            else sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }
        if (args[0].equalsIgnoreCase("연동")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("플레이어만 사용 가능합니다."); return true; }
            if (!sender.hasPermission("servergui.admin")) { sender.sendMessage("권한이 없습니다: servergui.admin"); return true; }
            linkLookingNpc(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("리로드")) {
            if (!sender.hasPermission("servergui.admin")) { sender.sendMessage("권한이 없습니다: servergui.admin"); return true; }
            reloadConfig();
            reloadLocal();
            sender.sendMessage("ServerGUI 설정을 리로드했습니다.");
            return true;
        }
        sender.sendMessage("사용법: /서버 열기 | /서버 연동 | /서버 리로드");
        return true;
    }

    private void reloadLocal() {
        FileConfiguration cfg = getConfig();
        this.menuTitle = LEGACY.deserialize(cfg.getString("menu-title", "&6서버 선택"));
        this.menuSize = Math.max(9, cfg.getInt("menu-size", 9));

        java.util.List<java.util.Map<?,?>> list = cfg.getMapList("servers");
        this.buttons = new java.util.ArrayList<>();
        for (java.util.Map<?,?> m : list) {
            String id = String.valueOf(m.get("id"));
            String name = String.valueOf(m.get("name"));
            String matStr = String.valueOf(m.get("material"));
            int slot = Integer.parseInt(String.valueOf(m.get("slot")));
            @SuppressWarnings("unchecked")
            java.util.List<String> loreLines = (java.util.List<String>) m.getOrDefault("lore", java.util.Collections.emptyList());

            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.PAPER;

            buttons.add(new ServerButton(id, name, mat, slot, loreLines));
        }
        this.linkedNpcIds = new java.util.HashSet<>(cfg.getIntegerList("npc.linked-ids"));
    }

    private void saveNpcLinks() {
        getConfig().set("npc.linked-ids", new java.util.ArrayList<>(linkedNpcIds));
        saveConfig();
    }

    // Citizens NPC 링크
    private void linkLookingNpc(Player p) {
        try { Class.forName("net.citizensnpcs.api.CitizensAPI"); }
        catch (Throwable t) { p.sendMessage("Citizens가 설치되어 있지 않습니다."); return; }

        Entity target = rayTraceEntity(p, 5.0);
        if (target == null) { p.sendMessage("바라보는 NPC를 찾지 못했습니다."); return; }
        if (!CitizensAPI.getNPCRegistry().isNPC(target)) { p.sendMessage("이 엔티티는 Citizens NPC가 아닙니다."); return; }

        NPC npc = CitizensAPI.getNPCRegistry().getNPC(target);
        int id = npc.getId();
        if (linkedNpcIds.add(id)) {
            saveNpcLinks();
            p.sendMessage("NPC 연동 완료: #" + id + " (우클릭 시 GUI 오픈)");
        } else {
            p.sendMessage("이미 연동된 NPC입니다: #" + id);
        }
    }

    private Entity rayTraceEntity(Player p, double maxDistance) {
        Vector direction = p.getEyeLocation().getDirection();
        RayTraceResult result = p.getWorld().rayTraceEntities(p.getEyeLocation(), direction, maxDistance, e -> !e.equals(p));
        if (result != null) return result.getHitEntity();
        try {
            Entity e = p.getTargetEntity((int)Math.ceil(maxDistance));
            if (e != null) return e;
        } catch (Throwable ignored) {}
        return null;
    }

    // === GUI ===
    private void applyMetaReflective(ItemMeta meta, String nameLegacy, java.util.List<String> loreLegacy) {
        try {
            // displayName(Component) 또는 setDisplayName(String)
            try {
                java.lang.reflect.Method dn = meta.getClass().getMethod("displayName", Class.forName("net.kyori.adventure.text.Component"));
                dn.invoke(meta, LEGACY.deserialize(nameLegacy));
            } catch (NoSuchMethodException e) {
                meta.getClass().getMethod("setDisplayName", String.class).invoke(meta, translateAltCodes(nameLegacy));
            }
            // lore(List<Component>) 또는 setLore(List<String>)
            try {
                java.lang.reflect.Method loreM = meta.getClass().getMethod("lore", java.util.List.class);
                java.util.List<Object> compList = new java.util.ArrayList<>();
                if (loreLegacy != null) {
                    for (String s : loreLegacy) compList.add(LEGACY.deserialize(s));
                }
                loreM.invoke(meta, compList);
            } catch (NoSuchMethodException e) {
                java.util.List<String> legacyLore = new java.util.ArrayList<>();
                if (loreLegacy != null) {
                    for (String s : loreLegacy) legacyLore.add(translateAltCodes(s));
                }
                meta.getClass().getMethod("setLore", java.util.List.class).invoke(meta, legacyLore);
            }
        } catch (Exception ex) {
            getLogger().warning("applyMeta failed: " + ex.getMessage());
        }
    }

    private String translateAltCodes(String s) {
        return s == null ? null : s.replaceAll("&([0-9a-fk-or])", "§$1");
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(p, menuSize, menuTitle);
        for (ServerButton b : buttons) {
            ItemStack item = new ItemStack(b.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                applyMetaReflective(meta, b.nameLegacy, b.loreLegacy);
                item.setItemMeta(meta);
            }
            if (b.slot >= 0 && b.slot < inv.getSize()) inv.setItem(b.slot, item);
            else inv.addItem(item);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().title().equals(menuTitle)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            for (ServerButton b : buttons) {
                if (clicked.getType() == b.material) {
                    connect(p, b.id);
                    p.closeInventory();
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onNpcRightClick(PlayerInteractAtEntityEvent e) {
        Entity clicked = e.getRightClicked();
        try { Class.forName("net.citizensnpcs.api.CitizensAPI"); }
        catch (Throwable ignored) { return; }
        if (!CitizensAPI.getNPCRegistry().isNPC(clicked)) return;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
        if (linkedNpcIds.contains(npc.getId())) {
            e.setCancelled(true);
            openMenu(e.getPlayer());
        }
    }

    private void connect(Player p, String serverId) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverId);
            p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
            p.sendMessage(LEGACY.deserialize("&7이동중... &f" + serverId));
        } catch (Exception ex) {
            p.sendMessage(LEGACY.deserialize("&c서버 이동 실패: " + ex.getMessage()));
            getLogger().warning("Failed to connect " + p.getName() + " to " + serverId + ": " + ex.getMessage());
        }
    }

    private static final class ServerButton {
        final String id;
        final String nameLegacy;
        final Material material;
        final int slot;
        final java.util.List<String> loreLegacy;
        ServerButton(String id, String nameLegacy, Material material, int slot, java.util.List<String> loreLegacy) {
            this.id = id;
            this.nameLegacy = nameLegacy;
            this.material = material;
            this.slot = slot;
            this.loreLegacy = loreLegacy;
        }
    }
}
