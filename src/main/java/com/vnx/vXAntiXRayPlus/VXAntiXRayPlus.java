package com.vnx.vXAntiXRayPlus;

import com.vnx.vXAntiXRayPlus.BypassManager;
import com.vnx.vXAntiXRayPlus.VXAntiXRayCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VXAntiXRayPlus extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<UUID, Set<Block>> playerHiddenBlocks;
    private Map<UUID, Map<Block, Long>> playerFoundBlocks;
    private Set<Material> hiddenMaterials;
    private int detectionDistance;
    private String detectionMessage;
    private boolean staffAlerts;
    private boolean enableLogging;
    private File logFile;
    private BypassManager bypassManager;

    @Override
    public void onEnable() {
        playerHiddenBlocks = new ConcurrentHashMap<>();
        playerFoundBlocks = new ConcurrentHashMap<>();
        hiddenMaterials = new HashSet<>();

        saveDefaultConfig();
        loadConfiguration();

        getServer().getPluginManager().registerEvents(this, this);

        bypassManager = new BypassManager(this);
        getServer().getPluginManager().registerEvents(bypassManager, this);

        getCommand("vxantixray").setExecutor(new VXAntiXRayCommand(this));

        startScanTask();

        getLogger().info("VXAntiXRay+ habilitado correctamente!");
    }

    @Override
    public void onDisable() {
        getLogger().info("VXAntiXRay+ deshabilitado!");
    }

    private void loadConfiguration() {
        config = getConfig();

        detectionDistance = config.getInt("detection-distance", 4);

        detectionMessage = config.getString("detection-message", "§c¡Mineral detectado!");

        staffAlerts = config.getBoolean("staff-alerts.enabled", true);

        enableLogging = config.getBoolean("logging.enabled", true);

        hiddenMaterials.clear();
        List<String> materials = config.getStringList("hidden-blocks");
        for (String materialName : materials) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                hiddenMaterials.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Material no válido en configuración: " + materialName);
            }
        }

        if (enableLogging) {
            logFile = new File(getDataFolder(), "detections.log");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    getLogger().warning("No se pudo crear el archivo de log: " + e.getMessage());
                }
            }
        }
    }

    private void startScanTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    scanAroundPlayer(player);
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Ejecutar cada segundo
    }

    private void scanAroundPlayer(Player player) {
        if (bypassManager.hasBypass(player)) {
            return; 
        }

        Set<Block> currentHidden = playerHiddenBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        Set<Block> newHidden = new HashSet<>();

        int radius = detectionDistance + 5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = player.getLocation().add(x, y, z).getBlock();

                    if (hiddenMaterials.contains(block.getType())) {
                        double distance = player.getLocation().distance(block.getLocation());

                        if (distance > detectionDistance) {
                            if (!currentHidden.contains(block)) {
                                hideBlockFromPlayer(player, block);
                            }
                            newHidden.add(block);
                        } else {
                            if (currentHidden.contains(block)) {
                                revealBlockToPlayer(player, block);
                                onMineralDetected(player, block);
                            }
                        }
                    }
                }
            }
        }

        for (Block block : new HashSet<>(currentHidden)) {
            if (!newHidden.contains(block)) {
                revealBlockToPlayer(player, block);
            }
        }

        playerHiddenBlocks.put(player.getUniqueId(), newHidden);
    }

    private void hideBlockFromPlayer(Player player, Block block) {
        Material fakeMaterial = getFakeMaterial(block.getLocation());
        player.sendBlockChange(block.getLocation(), fakeMaterial.createBlockData());
    }

    private void revealBlockToPlayer(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private Material getFakeMaterial(org.bukkit.Location location) {
        int y = location.getBlockY();

        if (y < 0) {
            return Material.DEEPSLATE;
        } else if (y < 16) {
            return Math.random() < 0.7 ? Material.STONE : Material.DEEPSLATE;
        } else if (y < 64) {
            return Math.random() < 0.8 ? Material.STONE : Material.COBBLESTONE;
        } else {
            return Material.STONE;
        }
    }

    private void onMineralDetected(Player player, Block block) {
        if (!detectionMessage.isEmpty()) {
            player.sendMessage(detectionMessage.replace("%block%", block.getType().toString()));
        }

        if (staffAlerts) {
            sendStaffAlert(player, block);
        }

        if (enableLogging) {
            logDetection(player, block);
        }

        Map<Block, Long> foundBlocks = playerFoundBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        foundBlocks.put(block, System.currentTimeMillis());
    }

    private void sendStaffAlert(Player player, Block block) {
        String alertMessage = config.getString("staff-alerts.message",
                "§e[AntiXRay] §f%player% §eha encontrado §f%block% §een §f%location%");

        alertMessage = alertMessage
                .replace("%player%", player.getName())
                .replace("%block%", block.getType().toString())
                .replace("%location%", block.getX() + ", " + block.getY() + ", " + block.getZ());

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("vxantixray.alerts")) {
                staff.sendMessage(alertMessage);
            }
        }
    }

    private void logDetection(Player player, Block block) {
        if (logFile == null) return;

        try (FileWriter writer = new FileWriter(logFile, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String logEntry = String.format("[%s] %s encontró %s en %d,%d,%d%n",
                    timestamp, player.getName(), block.getType().toString(),
                    block.getX(), block.getY(), block.getZ());
            writer.write(logEntry);
        } catch (IOException e) {
            getLogger().warning("Error al escribir en el log: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        Set<Block> hiddenBlocks = playerHiddenBlocks.get(player.getUniqueId());
        if (hiddenBlocks != null) {
            hiddenBlocks.remove(block);
        }

        if (hiddenMaterials.contains(block.getType()) && enableLogging) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String logEntry = String.format("[%s] %s minó %s en %d,%d,%d%n",
                        timestamp, player.getName(), block.getType().toString(),
                        block.getX(), block.getY(), block.getZ());
                writer.write(logEntry);
            } catch (IOException e) {
                getLogger().warning("Error al escribir en el log: " + e.getMessage());
            }
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        loadConfiguration();
    }

    public Map<UUID, Map<Block, Long>> getPlayerFoundBlocks() {
        return playerFoundBlocks;
    }

    public Set<Material> getHiddenMaterials() {
        return hiddenMaterials;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    public BypassManager getBypassManager() {
        return bypassManager;
    }
}
