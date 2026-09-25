package com.fell1cs.rtp.manager;

import com.fell1cs.rtp.RtpPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TeleportManager {

    private final RtpPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();

    private final Map<UUID, BukkitRunnable> pending = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public TeleportManager(RtpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPending(UUID uuid) { return pending.containsKey(uuid); }

    public boolean isOnCooldown(UUID uuid) {
        Long until = cooldowns.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public long getRemainingCooldown(UUID uuid) {
        Long until = cooldowns.get(uuid);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(UUID uuid, int seconds) {
        if (seconds > 0) cooldowns.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    private Location findSafeLocation(World world) {
        int attempts = 0;
        while (attempts < plugin.getConfigManager().getMaxAttempts()) {
            attempts++;
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = plugin.getConfigManager().getMinRadius()
                    + random.nextDouble() * (plugin.getConfigManager().getRadius() - plugin.getConfigManager().getMinRadius());
            int x = (int) (Math.cos(angle) * dist);
            int z = (int) (Math.sin(angle) * dist);

            // Загружаем чанк асинхронно
            world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
                // Проверяем высоту
                int y = world.getHighestBlockYAt(x, z);
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                // Здесь можно добавить проверку на безопасность (лава, вода и т.д.)
            });
        }
        return null;
    }

    public void startRtp(Player player, int countdown) {
        cancel(player.getUniqueId());

        World world = player.getWorld();
        Location target = findSafeLocation(world);
        if (target == null) {
            player.sendMessage(mm.deserialize(plugin.getConfigManager().message("no-safe-location")));
            return;
        }

        if (countdown <= 0) {
            finish(player, target);
            return;
        }

        startLocations.put(player.getUniqueId(), player.getLocation().clone());

        BukkitRunnable task = new BukkitRunnable() {
            int left = countdown;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    TeleportManager.this.cancel(player.getUniqueId());
                    return;
                }
                if (left <= 0) {
                    finish(player, target);
                    return;
                }
                player.sendActionBar(mm.deserialize(
                        plugin.getConfigManager().message("countdown", "time", left)
                ));
                left--;
            }
        };
        pending.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void cancel(UUID uuid) {
        BukkitRunnable task = pending.remove(uuid);
        if (task != null) task.cancel();
        startLocations.remove(uuid);
    }

    private void finish(Player player, Location target) {
        pending.remove(player.getUniqueId());
        startLocations.remove(player.getUniqueId());

        player.sendMessage(mm.deserialize(plugin.getConfigManager().message("teleporting")));

        player.teleportAsync(target).thenAccept(success -> {
            if (success) {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().message("teleported")));
            } else {
                player.sendMessage(mm.deserialize(plugin.getConfigManager().message("no-safe-location")));
            }
        });
    }

    public boolean shouldCancelOnMove(Player player) {
        Location start = startLocations.get(player.getUniqueId());
        if (start == null) return false;
        Location now = player.getLocation();
        return now.getWorld() != start.getWorld()
                || now.distanceSquared(start) > 0.25;
    }

    public void clearAll() {
        pending.values().forEach(BukkitRunnable::cancel);
        pending.clear();
        startLocations.clear();
        cooldowns.clear();
    }
}