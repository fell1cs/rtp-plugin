package com.fell1cs.rtp.command;

import com.fell1cs.rtp.RtpPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RtpCommand implements CommandExecutor {

    private final RtpPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public RtpCommand(RtpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Только для игроков."));
            return true;
        }

        var cfg = plugin.getConfigManager();
        var tm = plugin.getTeleportManager();

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("rtp.admin")) {
                player.sendMessage(mm.deserialize(cfg.message("no-permission")));
                return true;
            }
            cfg.reload();
            player.sendMessage(mm.deserialize(cfg.message("reloaded")));
            return true;
        }

        if (!player.hasPermission("rtp.use")) {
            player.sendMessage(mm.deserialize(cfg.message("no-permission")));
            return true;
        }

        if (!cfg.getAllowedWorlds().contains(player.getWorld().getName())) {
            player.sendMessage(mm.deserialize(cfg.message("world-not-allowed")));
            return true;
        }

        if (tm.isOnCooldown(player.getUniqueId())
                && !player.hasPermission("rtp.bypass.countdown")) {
            player.sendMessage(mm.deserialize(cfg.message("cooldown",
                    "time", tm.getRemainingCooldown(player.getUniqueId()))));
            return true;
        }

        int countdown = player.hasPermission("rtp.bypass.countdown") ? 0 : cfg.getCountdown();
        tm.setCooldown(player.getUniqueId(), cfg.getCooldown());
        tm.startRtp(player, countdown);

        return true;
    }
}