package com.fell1cs.rtp.listener;

import com.fell1cs.rtp.RtpPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final RtpPlugin plugin;

    public PlayerJoinListener(RtpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && plugin.getUpdateChecker() != null) {
                plugin.getUpdateChecker().notifyPlayer(event.getPlayer());
            }
        }, 40L);
    }
}