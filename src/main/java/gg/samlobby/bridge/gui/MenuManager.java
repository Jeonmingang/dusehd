
package gg.samlobby.bridge.gui;

import gg.samlobby.bridge.ServerMenuBridgePlugin;
import gg.samlobby.bridge.util.ConnectUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.stream.Collectors;

public class MenuManager implements Listener {
    private final ServerMenuBridgePlugin plugin;
    private final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private Component titleComponent;
    private int rows;
    private boolean filler;
    private final Map<Integer, String> slotToServer = new HashMap<>();
    private final NamespacedKey keyMark;

    public MenuManager(ServerMenuBridgePlugin plugin) {
        this.plugin = plugin;
        this.keyMark = new NamespacedKey(plugin, "slot");
        reload();
    }

    public void reload() {
        this.rows = Math.max(1, Math.min(6, plugin.getConfig().getInt("menu.rows", 3)));
        this.filler = plugin.getConfig().getBoolean("menu.fillerGlass", true);
        String title = plugin.getConfig().getString("menu.title", "&l서버 메뉴");
        this.titleComponent = LEGACY.deserialize(title);
        slotToServer.clear();
    }

    // Simple holder so we can identify our inventory in click events
    private static class MenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(), rows * 9, titleComponent);
        if (filler) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta();
            pm.displayName(Component.text(" "));
            pane.setItemMeta(pm);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        }

        List<Map<?, ?>> servers = plugin.getConfig().getMapList("servers");
        for (Map<?, ?> m : servers) {
            int slot = ((Number) m.getOrDefault("slot", 13)).intValue();
            String iconStr = String.valueOf(m.getOrDefault("icon", "EMERALD"));
            Material mat = Material.matchMaterial(iconStr);
            if (mat == null) mat = Material.EMERALD;
            String nameStr = String.valueOf(m.getOrDefault("name", "&a서버"));
            @SuppressWarnings("unchecked")
            List<Object> rawLore = (List<Object>) m.getOrDefault("lore", List.of("&7클릭하여 이동"));
            List<Component> lore = rawLore.stream()
                    .map(o -> LEGACY.deserialize(String.valueOf(o)))
                    .collect(Collectors.toList());
            String connect = String.valueOf(m.getOrDefault("connect", "lobby"));

            ItemStack it = new ItemStack(mat);
            ItemMeta im = it.getItemMeta();
            im.displayName(LEGACY.deserialize(nameStr));
            im.lore(lore);
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            // Store marker so we know this item belongs to our menu
            PersistentDataContainer pdc = im.getPersistentDataContainer();
            pdc.set(keyMark, PersistentDataType.INTEGER, slot);
            it.setItemMeta(im);

            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, it);
            slotToServer.put(slot, connect);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
        String server = slotToServer.get(e.getRawSlot());
        if (server == null) return;
        p.closeInventory();
        p.sendMessage(LEGACY.deserialize("&a연결 중... &7" + server));
        ConnectUtil.connect(plugin, p, server);
    }

    public Entity getLookedAtEntity(Player p, double maxDistance) {
        RayTraceResult res = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                maxDistance, 0.5, ent -> !ent.equals(p));
        return res != null ? res.getHitEntity() : null;
    }

    public static boolean isNpcLike(Entity e) {
        try {
            if (e.hasMetadata("NPC")) return true;
            // fallback: some common entity types used for NPCs
            switch (e.getType()) {
                case VILLAGER, ARMOR_STAND, ZOMBIE, PLAYER -> { return true; }
                default -> { return false; }
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
