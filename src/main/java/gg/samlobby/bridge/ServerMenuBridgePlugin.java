
package gg.samlobby.bridge;

import gg.samlobby.bridge.gui.MenuManager;
import gg.samlobby.bridge.util.ConnectUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerMenuBridgePlugin extends JavaPlugin implements Listener {

    private MenuManager menu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.menu = new MenuManager(this);
        // Register events & channels
        getServer().getPluginManager().registerEvents(menu, this);
        // Plugin messaging channels for Velocity/Bungee compatibility
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        getLogger().info("ServerMenuBridge enabled. Java=" + System.getProperty("java.version") + " Paper=" + Bukkit.getVersion());
        // Commands
        getCommand("servermenu").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Player only.");
                return true;
            }
            if (!sender.hasPermission("servermenu.use")) {
                sender.sendMessage("§c권한이 없습니다.");
                return true;
            }
            boolean requireSight = getConfig().getBoolean("menu.requireNpcSight", true);
            if (requireSight) {
                Entity looked = menu.getLookedAtEntity(player, 6.0);
                if (looked == null || !MenuManager.isNpcLike(looked)) {
                    player.sendMessage("§eNPC를 바라본 상태에서 /" + label + " 를 입력하세요.");
                    return true;
                }
            }
            menu.open(player);
            return true;
        });
        getCommand("samlobby").setExecutor((sender, cmd, label, args) -> {
            if (!sender.hasPermission("servermenu.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§e/samlobby reload|open");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    reloadConfig();
                    menu.reload();
                    sender.sendMessage("§aConfig reloaded.");
                }
                case "open" -> {
                    if (sender instanceof Player p) {
                        menu.open(p);
                    } else {
                        sender.sendMessage("Player only.");
                    }
                }
                default -> sender.sendMessage("§cUnknown: reload|open");
            }
            return true;
        });
    }

    @Override
    public void onDisable() {
        // nothing
    }

    public MenuManager getMenu() {
        return menu;
    }
}
