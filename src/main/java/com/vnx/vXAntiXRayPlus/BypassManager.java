package com.vnx.vXAntiXRayPlus;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BypassManager implements Listener {

    private final VXAntiXRayPlus plugin;
    private final Set<UUID> bypassPlayers;

    public BypassManager(VXAntiXRayPlus plugin) {
        this.plugin = plugin;
        this.bypassPlayers = new HashSet<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        checkBypassPermission(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bypassPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void checkBypassPermission(Player player) {
        if (player.hasPermission("vxantixray.bypass")) {
            bypassPlayers.add(player.getUniqueId());

            if (plugin.getPluginConfig().getBoolean("bypass.notify-player", true)) {
                player.sendMessage("§e[AntiXRay] §7Tienes bypass activo - puedes ver todos los minerales.");
            }

            plugin.getLogger().info("Jugador " + player.getName() + " tiene bypass activo.");
        } else {
            bypassPlayers.remove(player.getUniqueId());
        }
    }

    public boolean hasBypass(Player player) {
        return bypassPlayers.contains(player.getUniqueId());
    }

    public void refreshAllBypass() {
        bypassPlayers.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            checkBypassPermission(player);
        }
    }

    public int getBypassCount() {
        return bypassPlayers.size();
    }

    public Set<UUID> getBypassPlayers() {
        return new HashSet<>(bypassPlayers);
    }
}
