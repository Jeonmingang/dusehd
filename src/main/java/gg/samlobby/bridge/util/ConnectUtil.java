
package gg.samlobby.bridge.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class ConnectUtil {
    private ConnectUtil() {}

    public static void connect(JavaPlugin plugin, Player player, String serverName) {
        try {
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(msg);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", msg.toByteArray());
            player.sendPluginMessage(plugin, "bungeecord:main", msg.toByteArray());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send connect message: " + ex.getMessage());
            player.sendMessage("§c연결 메시지 전송 실패: §7" + ex.getMessage());
        }
    }
}
