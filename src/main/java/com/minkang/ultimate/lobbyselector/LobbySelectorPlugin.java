package com.minkang.ultimate.lobbyselector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.stream.Collectors;

public class LobbySelectorPlugin extends JavaPlugin implements Listener {

    private String menuTitle;
    private int menuSize;
    private List<ServerButton> buttons;
    private Set<Integer> linkedNpcIds;

    @Override
    public void onEnable() {
        // 기본 설정 로드
        saveDefaultConfig();
        reloadLocal();

        // 이벤트 / 채널 등록
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // 커맨드
        getCommand("servermenu").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("플레이어만 사용 가능합니다.");
                return true;
            }
            Player p = (Player) sender;
            openMenu(p);
            return true;
        });

        getCommand("npcgui").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("플레이어만 사용.");
                return true;
            }
            Player p = (Player) sender;
            if (args.length == 0) {
                p.sendMessage(col("&e/npcgui link &7- 바라보는 Citizens NPC를 등록"));
                p.sendMessage(col("&e/npcgui unlink &7- 바라보는 NPC 등록 해제"));
                p.sendMessage(col("&e/npcgui list &7- 등록된 NPC 목록 보기"));
                return true;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "link":
                    linkLookingNpc(p, true);
                    return true;
                case "unlink":
                    linkLookingNpc(p, false);
                    return true;
                case "list":
                    p.sendMessage(col("&a등록된 NPC: &f" + linkedNpcIds.stream().map(String::valueOf).collect(Collectors.joining(", "))));
                    return true;
                default:
                    p.sendMessage(col("&c사용법: /npcgui link|unlink|list"));
                    return true;
            }
        });

        getLogger().info("LobbySelector enabled. Citizens installed: " + isCitizensPresent());
    }

    @Override
    public void onDisable() {
        // 저장
        saveNpcLinks();
    }

    private void reloadLocal() {
        FileConfiguration cfg = getConfig();
        this.menuTitle = ChatColor.translateAlternateColorCodes('&', cfg.getString("menu-title", "&6서버 선택"));
        this.menuSize = Math.max(9, cfg.getInt("menu-size", 9));

        this.buttons = new ArrayList<>();
        List<Map<?,?>> list = cfg.getMapList("servers");
        for (Map<?,?> m : list) {
            String id = String.valueOf(m.get("id"));
            String name = ChatColor.translateAlternateColorCodes('&', String.valueOf(m.get("name")));
            String matStr = String.valueOf(m.get("material"));
            int slot = Integer.parseInt(String.valueOf(m.get("slot")));
            @SuppressWarnings("unchecked")
            List<String> lore = (List<String>) m.getOrDefault("lore", Collections.emptyList());
            List<String> coloredLore = lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList());

            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.PAPER;

            buttons.add(new ServerButton(id, name, mat, slot, coloredLore));
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
            p.sendMessage(col("&cCitizens가 설치되어 있지 않습니다."));
            return;
        }
        Entity target = rayTraceEntity(p, 5.0);
        if (target == null) {
            p.sendMessage(col("&c바라보는 NPC를 찾지 못했습니다. NPC를 바라보고 사용하세요."));
            return;
        }
        if (!CitizensAPI.getNPCRegistry().isNPC(target)) {
            p.sendMessage(col("&c이 엔티티는 Citizens NPC가 아닙니다."));
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(target);
        int id = npc.getId();
        if (add) {
            if (linkedNpcIds.add(id)) {
                p.sendMessage(col("&aNPC 등록: &f#" + id));
                saveNpcLinks();
            } else {
                p.sendMessage(col("&e이미 등록된 NPC입니다: &f#" + id));
            }
        } else {
            if (linkedNpcIds.remove(id)) {
                p.sendMessage(col("&aNPC 해제: &f#" + id));
                saveNpcLinks();
            } else {
                p.sendMessage(col("&e등록되어 있지 않습니다: &f#" + id));
            }
        }
    }

    private Entity rayTraceEntity(Player p, double maxDistance) {
        Vector direction = p.getEyeLocation().getDirection();
        RayTraceResult result = p.getWorld().rayTraceEntities(p.getEyeLocation(), direction, maxDistance, e -> !e.equals(p));
        if (result != null) {
            return result.getHitEntity();
        }
        // fallback: use getTargetEntity if available
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
                meta.setDisplayName(b.name);
                if (b.lore != null && !b.lore.isEmpty()) meta.setLore(b.lore);
                item.setItemMeta(meta);
            }
            if (b.slot >= 0 && b.slot < inv.getSize()) {
                inv.setItem(b.slot, item);
            } else {
                inv.addItem(item);
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (e.getView().getTitle().equals(ChatColor.stripColor(menuTitle)) || e.getView().getTitle().equals(menuTitle)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            ServerButton match = null;
            for (ServerButton b : buttons) {
                if (clicked.getType() == b.material) {
                    match = b; break;
                }
            }
            if (match != null) {
                connect(p, match.id);
                p.closeInventory();
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
            p.sendMessage(col("&7이동중... &f" + serverId));
        } catch (Exception ex) {
            p.sendMessage(col("&c서버 이동 실패: " + ex.getMessage()));
            getLogger().warning("Failed to connect " + p.getName() + " to " + serverId + ": " + ex.getMessage());
        }
    }

    private String col(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static class ServerButton {
        final String id;
        final String name;
        final Material material;
        final int slot;
        final List<String> lore;
        ServerButton(String id, String name, Material material, int slot, List<String> lore) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.slot = slot;
            this.lore = lore;
        }
    }
}
