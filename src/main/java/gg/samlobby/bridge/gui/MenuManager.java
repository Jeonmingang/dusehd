
package gg.samlobby.bridge.gui;

import gg.samlobby.bridge.ServerMenuBridgePlugin;
import gg.samlobby.bridge.util.ConnectUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.stream.Collectors;

public class MenuManager implements Listener {
    private final ServerMenuBridgePlugin plugin;
    private String title;
    private int rows;
    private boolean filler;
    private Map<Integer, String> slotToServer = new HashMap<>();
    private NamespacedKey keySlot;

    public MenuManager(ServerMenuBridgePlugin plugin) {
        this.plugin = plugin;
        this.keySlot = new NamespacedKey(plugin, "slot");
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var cfg = plugin.getConfig();
        this.title = color(cfg.getString("menu.title", "&l서버 메뉴"));
        this.rows = Math.max(1, Math.min(6, cfg.getInt("menu.rows", 3)));
        this.filler = cfg.getBoolean("menu.fillerGlass", true);
        this.slotToServer.clear();
    }

    public void open(Player p) {
        var cfg = plugin.getConfig();
        Inventory inv = Bukkit.createInventory(p, rows * 9, title);
        if (filler) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta();
            pm.setDisplayName(" ");
            pane.setItemMeta(pm);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        }

        List<Map<?,?>> servers = cfg.getMapList("servers");
        for (Map<?,?> m : servers) {
            int slot = ((Number)m.getOrDefault("slot", 13)).intValue();
            String iconStr = String.valueOf(m.getOrDefault("icon", "EMERALD"));
            Material mat = Material.matchMaterial(iconStr);
            if (mat == null) mat = Material.EMERALD;
            String name = color(String.valueOf(m.getOrDefault("name", "&a서버")));
            @SuppressWarnings("unchecked")
            List<String> lore = ((List<Object>)m.getOrDefault("lore", List.of("&7클릭하여 이동"))).stream().map(o -> color(String.valueOf(o))).collect(Collectors.toList());
            String connect = String.valueOf(m.getOrDefault("connect", "lobby"));

            ItemStack it = new ItemStack(mat);
            ItemMeta im = it.getItemMeta();
            im.setDisplayName(name);
            im.setLore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            // store slot/server in PDC
            im.getPersistentDataContainer().set(keySlot, PersistentDataType.INTEGER, slot);
            it.setItemMeta(im);

            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it);
            slotToServer.put(slot, connect);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle() == null || !ChatColor.stripColor(e.getView().getTitle()).equals(ChatColor.stripColor(title))) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        String server = slotToServer.get(e.getRawSlot());
        if (server == null) return;
        p.closeInventory();
        p.sendMessage(color("&a연결 중... &7" + server));
        ConnectUtil.connect(plugin, p, server);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // no-op
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public Entity getLookedAtEntity(Player p, double maxDistance) {
        RayTraceResult res = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                maxDistance, 0.5, ent -> !ent.equals(p));
        return res != null ? res.getHitEntity() : null;
    }

    public static boolean isNpcLike(Entity e) {
        // Citizens sets "NPC" metadata on its NPC entities
        try {
            if (e.hasMetadata("NPC")) return true;
            // Also consider entities named or types commonly used for lobby NPCs
            return switch (e.getType()) {
                case VILLAGER, ARMOR_STAND, ZOMBIE, PLAYER -> true;
                default -> false;
            };
        } catch (Throwable t) {
            return false;
        }
    }
}
