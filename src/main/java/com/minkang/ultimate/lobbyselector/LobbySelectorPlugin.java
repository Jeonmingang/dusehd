package com.minkang.ultimate.lobbyselector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

import java.util.*;

public class LobbySelectorPlugin extends JavaPlugin implements Listener {

    private Component menuTitle;
    private int menuSize;
    private List<ServerButton> buttons;
    private Set<Integer> linkedNpcIds;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        Objects.requireNonNull(getCommand("servermenu")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("플레이어만 사용 가능합니다.");
                return true;
            }
            openMenu(p);
            return true;
        });

        Objects.requireNonNull(getCommand("npcgui")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("플레이어만 사용.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(LEGACY.deserialize("&e/npcgui link &7- 바라보는 Citizens NPC를 등록"));
                p.sendMessage(LEGACY.deserialize("&e/npcgui unlink &7- 바라보는 NPC 등록 해제"));
                p.sendMessage(LEGACY.deserialize("&e/npcgui list &7- 등록된 NPC 목록 보기"));
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "link" -> linkLookingNpc(p, true);
                case "unlink" -> linkLookingNpc(p, false);
                case "list" -> p.sendMessage(LEGACY.deserialize("&a등록된 NPC: &f" + String.join(", ", linkedNpcIds.stream().map(String::valueOf).toList())));
                default -> p.sendMessage(LEGACY.deserialize("&c사용법: /npcgui link|unlink|list"));
            }
            return true;
        });

        getLogger().info("LobbySelector enabled. Citizens installed: " + isCitizensPresent());
    }

    @Override
    public void onDisable() {
        saveNpcLinks();
    }

    private void reloadLocal() {
        FileConfiguration cfg = getConfig();
        this.menuTitle = LEGACY.deserialize(cfg.getString("menu-title", "&6서버 선택"));
        this.menuSize = Math.max(9, cfg.getInt("menu-size", 9));

        // Build buttons with explicit types to avoid generic inference issues on CI
        List<Map<?,?>> list = cfg.getMapList("servers");
        this.buttons = new ArrayList<>();
        for (Map<?,?> m : list) {
            String id = String.valueOf(m.get("id"));
            String name = String.valueOf(m.get("name"));
            String matStr = String.valueOf(m.get("material"));
            int slot = Integer.parseInt(String.valueOf(m.get("slot")));
            @SuppressWarnings("unchecked")
            List<String> loreLines = (List<String>) m.getOrDefault("lore", Collections.emptyList());

            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.PAPER;

            // Explicitly build List<Component>
            List<Component> loreComponents = new ArrayList<>();
            for (String s : loreLines) loreComponents.add(LEGACY.deserialize(s));

            ServerButton btn = new ServerButton(id, LEGACY.deserialize(name), mat, slot, loreComponents);
            buttons.add(btn);
        }

        this.linkedNpcIds = new HashSet<>(cfg.getIntegerList("npc.linked-ids"));
    }

    private void saveNpcLinks() {
        getConfig().set("npc.linked-ids", new ArrayList<>(linkedNpcIds));
        saveConfig();
    }

    private boolean isCitizensPresent() {
        try {
            return Bukkit.getPluginManager().getPlugin("Citizens") != null && CitizensAPI.hasImplementation();
        } catch (Throwable t) {
            return false;
        }
    }

    private void linkLookingNpc(Player p, boolean add) {
        if (!isCitizensPresent()) {
            p.sendMessage(LEGACY.deserialize("&cCitizens가 설치되어 있지 않습니다."));
            return;
        }
        Entity target = rayTraceEntity(p, 5.0);
        if (target == null) {
            p.sendMessage(LEGACY.deserialize("&c바라보는 NPC를 찾지 못했습니다. NPC를 바라보고 사용하세요."));
            return;
        }
        if (!CitizensAPI.getNPCRegistry().isNPC(target)) {
            p.sendMessage(LEGACY.deserialize("&c이 엔티티는 Citizens NPC가 아닙니다."));
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(target);
        int id = npc.getId();
        if (add) {
            if (linkedNpcIds.add(id)) {
                p.sendMessage(LEGACY.deserialize("&aNPC 등록: &f#" + id));
                saveNpcLinks();
            } else {
                p.sendMessage(LEGACY.deserialize("&e이미 등록된 NPC입니다: &f#" + id));
            }
        } else {
            if (linkedNpcIds.remove(id)) {
                p.sendMessage(LEGACY.deserialize("&aNPC 해제: &f#" + id));
                saveNpcLinks();
            } else {
                p.sendMessage(LEGACY.deserialize("&e등록되어 있지 않습니다: &f#" + id));
            }
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

    // ==== GUI ====
    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(p, menuSize, menuTitle);
        for (ServerButton b : buttons) {
            ItemStack item = new ItemStack(b.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(b.name);
                meta.lore(b.lore);
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

            // Find by material match
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
        if (!isCitizensPresent()) return;
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
        final Component name;
        final Material material;
        final int slot;
        final List<Component> lore;
        ServerButton(String id, Component name, Material material, int slot, List<Component> lore) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.slot = slot;
            this.lore = lore;
        }
    }
}
