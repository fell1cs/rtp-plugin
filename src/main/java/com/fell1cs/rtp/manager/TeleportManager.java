package com.fell1cs.rtp.manager;

import com.fell1cs.rtp.RtpPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

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
        if (seconds > 0) {
            cooldowns.put(uuid, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private CompletableFuture<Location> findSafeLocation(World world) {
        return findSafeLocation(world, 0);
    }

    private CompletableFuture<Location> findSafeLocation(World world, int attempt) {
        int maxAttempts = plugin.getConfigManager().getMaxAttempts();

        if (attempt >= maxAttempts) {
            return CompletableFuture.completedFuture(null);
        }

        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = plugin.getConfigManager().getMinRadius()
                + random.nextDouble() * (plugin.getConfigManager().getRadius()
                - plugin.getConfigManager().getMinRadius());

        int x = (int) (Math.cos(angle) * dist);
        int z = (int) (Math.sin(angle) * dist);

        // Асинхронно загружаем чанк, затем проверяем безопасность на главном потоке
        return world.getChunkAtAsync(x >> 4, z >> 4)
                .thenCompose(chunk -> {
                    // getHighestBlockYAt и проверка блоков должны выполняться на main thread
                    CompletableFuture<Location> result = new CompletableFuture<>();

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            int y = world.getHighestBlockYAt(x, z);
                            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);

                            if (isSafe(loc)) {
                                result.complete(loc);
                            } else {
                                // Рекурсивно пробуем следующую точку
                                findSafeLocation(world, attempt + 1)
                                        .whenComplete((next, ex) -> {
                                            if (ex != null) {
                                                result.completeExceptionally(ex);
                                            } else {
                                                result.complete(next);
                                            }
                                        });
                            }
                        } catch (Exception e) {
                            result.completeExceptionally(e);
                        }
                    });

                    return result;
                })
                .exceptionallyCompose(ex -> {
                    // При ошибке загрузки чанка — пробуем ещё раз (без блокировки)
                    return findSafeLocation(world, attempt + 1);
                });
    }

    private boolean isSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        // Ноги и голова должны быть воздухом (или проходимым блоком)
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }

        if (!ground.getType().isSolid()) {
            return false;
        }

        Material groundType = ground.getType();
        if (groundType == Material.LAVA
                || groundType == Material.MAGMA_BLOCK
                || groundType == Material.CACTUS
                || groundType == Material.FIRE
                || groundType == Material.SOUL_FIRE
                || groundType == Material.CAMPFIRE
                || groundType == Material.SOUL_CAMPFIRE
                || groundType == Material.SWEET_BERRY_BUSH
                || groundType == Material.WITHER_ROSE) {
            return false;
        }

        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }

        return true;
    }

    private boolean isPassable(Block block) {
        Material type = block.getType();
        return type.isAir()
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.LARGE_FERN
                || type == Material.SNOW
                || !type.isSolid();
    }

    public void startRtp(Player player, int countdown) {
        cancel(player.getUniqueId());

        World world = player.getWorld();

        // Показываем, что идёт поиск
        player.sendActionBar(mm.deserialize("<gray>Поиск безопасной точки..."));

        findSafeLocation(world).thenAccept(target -> {
            // Колбэк может прийти не с main thread — переключаемся
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (target == null) {
                    player.sendMessage(mm.deserialize(
                            plugin.getConfigManager().message("no-safe-location")));
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

                        // Отмена при движении
                        if (plugin.getConfigManager().isCancelOnMove()
                                && shouldCancelOnMove(player)) {
                            player.sendMessage(mm.deserialize(
                                    plugin.getConfigManager().message("cancelled")));
                            TeleportManager.this.cancel(player.getUniqueId());
                            return;
                        }

                        if (left <= 0) {
                            // Останавливаем этот таймер, чтобы не вызывать finish повторно
                            cancel();
                            pending.remove(player.getUniqueId());
                            finish(player, target);
                            return;
                        }

                        player.sendActionBar(mm.deserialize(
                                plugin.getConfigManager().message("countdown", "time", left)));
                        left--;
                    }
                };

                pending.put(player.getUniqueId(), task);
                task.runTaskTimer(plugin, 0L, 20L);
            });
        }).exceptionally(ex -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(mm.deserialize(
                            plugin.getConfigManager().message("no-safe-location")));
                }
                plugin.getLogger().warning("Ошибка поиска точки RTP: " + ex.getMessage());
            });
            return null;
        });
    }

    public void cancel(UUID uuid) {
        BukkitRunnable task = pending.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        startLocations.remove(uuid);
    }

    private void finish(Player player, Location target) {
        BukkitRunnable task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        startLocations.remove(player.getUniqueId());

        player.sendMessage(mm.deserialize(
                plugin.getConfigManager().message("teleporting")));

        player.teleportAsync(target).thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                if (Boolean.TRUE.equals(success)) {
                    player.sendMessage(mm.deserialize(
                            plugin.getConfigManager().message("teleported")));
                } else {
                    player.sendMessage(mm.deserialize(
                            plugin.getConfigManager().message("no-safe-location")));
                }
            });
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