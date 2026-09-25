package com.fell1cs.rtp.util;

import com.fell1cs.rtp.RtpPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final RtpPlugin plugin;
    private final String owner;
    private final String repo;
    private final String currentVersion;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable;

    public UpdateChecker(RtpPlugin plugin, String owner, String repo, String currentVersion) {
        this.plugin = plugin;
        this.owner = owner;
        this.repo = repo;
        this.currentVersion = currentVersion;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void checkAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() != 200) {
                    plugin.getLogger().warning("Не удалось проверить обновления (HTTP " + conn.getResponseCode() + ")");
                    return;
                }

                try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String tag = json.get("tag_name").getAsString().replace("v", "");
                    String htmlUrl = json.get("html_url").getAsString();

                    if (!tag.equalsIgnoreCase(currentVersion)) {
                        this.latestVersion = tag;
                        this.downloadUrl = htmlUrl;
                        this.updateAvailable = true;

                        plugin.getLogger().info("Доступно обновление: " + tag + " — " + htmlUrl);

                        // Уведомить уже онлайн операторов
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            for (Player p : plugin.getServer().getOnlinePlayers()) {
                                notifyPlayer(p);
                            }
                        });
                    } else {
                        this.updateAvailable = false;
                        plugin.getLogger().info("Плагин обновлён до последней версии.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при проверке обновлений: " + e.getMessage());
            }
        });
    }

    public void notifyPlayer(Player player) {
        if (!updateAvailable || latestVersion == null || downloadUrl == null) {
            return;
        }
        if (!player.isOp() && !player.hasPermission("rtp.admin")) {
            return;
        }

        player.sendMessage(mm.deserialize(
                plugin.getConfigManager().message("update-available",
                        "version", latestVersion,
                        "url", downloadUrl)
        ));
    }
}
