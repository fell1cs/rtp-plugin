package com.fell1cs.rtp;

import com.fell1cs.rtp.command.RtpCommand;
import com.fell1cs.rtp.config.ConfigManager;
import com.fell1cs.rtp.listener.PlayerJoinListener;
import com.fell1cs.rtp.manager.TeleportManager;
import com.fell1cs.rtp.util.UpdateChecker;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public final class RtpPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private TeleportManager teleportManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        teleportManager = new TeleportManager(this);

        getCommand("rtp").setExecutor(new RtpCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        String logo = configManager.getAsciiLogo();
        if (logo != null && !logo.isEmpty()) {
            MiniMessage mm = MiniMessage.miniMessage();
            getServer().getConsoleSender().sendMessage(mm.deserialize(logo));
        }
        getLogger().info(configManager.getStudioLink());

        updateChecker = new UpdateChecker(this, "fell1cs", "rtp-plugin", getPluginMeta().getVersion());
        updateChecker.checkAsync();
    }

    @Override
    public void onDisable() {
        if (teleportManager != null) teleportManager.clearAll();
        getLogger().info("Rtp plugin disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
