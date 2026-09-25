package com.fell1cs.rtp.util;

import com.fell1cs.rtp.RtpPlugin;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final RtpPlugin plugin;
    private final String owner;
    private final String repo;
    private final String currentVersion;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public UpdateChecker(RtpPlugin plugin, String owner, String repo, String currentVersion) {
        this.plugin = plugin;
        this.owner = owner;
        this.repo = repo;
        this.currentVersion = currentVersion;
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
                    String latestVersion = json.get("tag_name").getAsString().replace("v", "");
                    String htmlUrl = json.get("html_url").getAsString();

                    if (!latestVersion.equalsIgnoreCase(currentVersion)) {
                        plugin.getLogger().info("Доступно обновление: " + latestVersion + " — " + htmlUrl);

                        // Уведомляем игроков с правом rtp.admin при входе
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            plugin.getServer().getOnlinePlayers().stream()
                                    .filter(p -> p.hasPermission("rtp.admin"))
                                    .forEach(p -> p.sendMessage(mm.deserialize(
                                            plugin.getConfigManager().message("update-available",
                                                    "version", latestVersion,
                                                    "url", htmlUrl)
                                    )));
                        });
                    } else {
                        plugin.getLogger().info("Плагин обновлён до последней версии.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при проверке обновлений: " + e.getMessage());
            }
        });
    }
}