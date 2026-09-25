package com.fell1cs.rtp.config;

import com.fell1cs.rtp.RtpPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final RtpPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration config;

    private int radius;
    private int minRadius;
    private int countdown;
    private boolean cancelOnMove;
    private int cooldown;
    private int maxAttempts;
    private List<String> allowedWorlds;

    public ConfigManager(RtpPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        load();
    }

    public void load() {
        radius = config.getInt("rtp.radius", 5000);
        minRadius = config.getInt("rtp.min-radius", 500);
        countdown = config.getInt("rtp.countdown", 3);
        cancelOnMove = config.getBoolean("rtp.cancel-on-move", true);
        cooldown = config.getInt("rtp.cooldown", 10);
        maxAttempts = config.getInt("rtp.max-attempts", 20);
        allowedWorlds = config.getStringList("rtp.allowed-worlds");
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        load();
    }

    public String message(String path, Object... placeholders) {
        String msg = config.getString("messages." + path, "<red>Missing: " + path);
        String prefix = config.getString("messages.prefix", "");
        msg = prefix + msg;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("<" + placeholders[i] + ">", String.valueOf(placeholders[i + 1]));
        }
        return msg;
    }

    public String getStudioLink() { return config.getString("studio-link", ""); }
    public String getAsciiLogo() { return config.getString("ascii-logo", ""); }
    public MiniMessage mm() { return mm; }

    public int getRadius() { return radius; }
    public int getMinRadius() { return minRadius; }
    public int getCountdown() { return countdown; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public int getCooldown() { return cooldown; }
    public int getMaxAttempts() { return maxAttempts; }
    public List<String> getAllowedWorlds() { return allowedWorlds; }
}