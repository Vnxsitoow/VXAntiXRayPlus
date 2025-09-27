package com.vnx.vXAntiXRayPlus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

public class VXAntiXRayCommand implements CommandExecutor, TabCompleter {

    private final VXAntiXRayPlus plugin;

    public VXAntiXRayCommand(VXAntiXRayPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vxantixray.admin")) {
            sender.sendMessage("§c¡No tienes permisos para usar este comando!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;

            case "stats":
                if (args.length > 1) {
                    handlePlayerStats(sender, args[1]);
                } else {
                    handleGlobalStats(sender);
                }
                break;

            case "log":
                handleLog(sender, args);
                break;

            case "info":
                handleInfo(sender);
                break;

            case "toggle":
                if (args.length > 1) {
                    handleToggle(sender, args[1]);
                } else {
                    sender.sendMessage("§cUso: /vxantixray toggle <alerts|logging>");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e=== VXAntiXRay+ Comandos ===");
        sender.sendMessage("§6/vxantixray reload §7- Recarga la configuración");
        sender.sendMessage("§6/vxantixray info §7- Información del plugin");
        sender.sendMessage("§6/vxantixray stats [jugador] §7- Estadísticas de detección");
        sender.sendMessage("§6/vxantixray log [jugador] [cantidad] §7- Ver log de detecciones");
        sender.sendMessage("§6/vxantixray toggle <alerts|logging> §7- Activar/desactivar funciones");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadConfiguration();
            sender.sendMessage("§a¡Configuración recargada exitosamente!");
        } catch (Exception e) {
            sender.sendMessage("§cError al recargar la configuración: " + e.getMessage());
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§e=== VXAntiXRay+ Info ===");
        sender.sendMessage("§7Versión: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Materiales ocultos: §f" + plugin.getHiddenMaterials().size());
        sender.sendMessage("§7Jugadores monitoreados: §f" + Bukkit.getOnlinePlayers().size());
        sender.sendMessage("§7Distancia de detección: §f" + plugin.getPluginConfig().getInt("detection-distance"));
        sender.sendMessage("§7Alertas de staff: §f" + (plugin.getPluginConfig().getBoolean("staff-alerts.enabled") ? "§aActivadas" : "§cDesactivadas"));
        sender.sendMessage("§7Logging: §f" + (plugin.getPluginConfig().getBoolean("logging.enabled") ? "§aActivado" : "§cDesactivado"));

        StringBuilder materials = new StringBuilder("§7Bloques: §f");
        for (Material mat : plugin.getHiddenMaterials()) {
            materials.append(mat.name()).append(", ");
        }
        if (materials.length() > 2) {
            sender.sendMessage(materials.substring(0, materials.length() - 2));
        }
    }

    private void handleGlobalStats(CommandSender sender) {
        Map<UUID, Map<Block, Long>> allFoundBlocks = plugin.getPlayerFoundBlocks();

        sender.sendMessage("§e=== Estadísticas Globales ===");
        sender.sendMessage("§7Total jugadores con detecciones: §f" + allFoundBlocks.size());

        Map<Material, Integer> materialCounts = new HashMap<>();
        int totalDetections = 0;

        for (Map<Block, Long> playerBlocks : allFoundBlocks.values()) {
            totalDetections += playerBlocks.size();
            for (Block block : playerBlocks.keySet()) {
                materialCounts.merge(block.getType(), 1, Integer::sum);
            }
        }

        sender.sendMessage("§7Total detecciones: §f" + totalDetections);

        sender.sendMessage("§e--- Top 5 Materiales Detectados ---");
        materialCounts.entrySet().stream()
                .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> sender.sendMessage("§7" + entry.getKey().name() + ": §f" + entry.getValue()));
    }

    private void handlePlayerStats(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUuid = null;

        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            for (UUID uuid : plugin.getPlayerFoundBlocks().keySet()) {
                Player offlinePlayer = Bukkit.getPlayer(uuid);
                if (offlinePlayer != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                    targetUuid = uuid;
                    break;
                }
            }
        }

        if (targetUuid == null) {
            sender.sendMessage("§cJugador no encontrado o sin detecciones: " + playerName);
            return;
        }

        Map<Block, Long> playerBlocks = plugin.getPlayerFoundBlocks().get(targetUuid);
        if (playerBlocks == null || playerBlocks.isEmpty()) {
            sender.sendMessage("§7El jugador §f" + playerName + " §7no tiene detecciones registradas.");
            return;
        }

        sender.sendMessage("§e=== Estadísticas de " + playerName + " ===");
        sender.sendMessage("§7Total detecciones: §f" + playerBlocks.size());

        Map<Material, Integer> materialCounts = new HashMap<>();
        for (Block block : playerBlocks.keySet()) {
            materialCounts.merge(block.getType(), 1, Integer::sum);
        }

        sender.sendMessage("§e--- Detecciones por Material ---");
        materialCounts.entrySet().stream()
                .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                .forEach(entry -> sender.sendMessage("§7" + entry.getKey().name() + ": §f" + entry.getValue()));

        long lastDetection = playerBlocks.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (lastDetection > 0) {
            String lastTime = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date(lastDetection));
            sender.sendMessage("§7Última detección: §f" + lastTime);
        }
    }

    private void handleLog(CommandSender sender, String[] args) {
        sender.sendMessage("§7Para ver los logs completos, revisa el archivo:");
        sender.sendMessage("§f" + plugin.getDataFolder().getAbsolutePath() + "/detections.log");

        if (args.length > 1) {
            sender.sendMessage("§7Filtrando por jugador: §f" + args[1]);
        }

        sender.sendMessage("§7Usa herramientas como 'tail' o 'grep' para analizar el archivo de log.");
    }

    private void handleToggle(CommandSender sender, String feature) {
        switch (feature.toLowerCase()) {
            case "alerts":
                boolean currentAlerts = plugin.getPluginConfig().getBoolean("staff-alerts.enabled");
                plugin.getPluginConfig().set("staff-alerts.enabled", !currentAlerts);
                plugin.saveConfig();
                sender.sendMessage("§7Alertas de staff: " + (!currentAlerts ? "§aActivadas" : "§cDesactivadas"));
                break;

            case "logging":
                boolean currentLogging = plugin.getPluginConfig().getBoolean("logging.enabled");
                plugin.getPluginConfig().set("logging.enabled", !currentLogging);
                plugin.saveConfig();
                sender.sendMessage("§7Logging: " + (!currentLogging ? "§aActivado" : "§cDesactivado"));
                break;

            default:
                sender.sendMessage("§cOpción no válida. Usa: alerts o logging");
                break;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("vxantixray.admin")) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "info", "stats", "log", "toggle"));
            return filterCompletions(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "stats":
                case "log":
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                    break;

                case "toggle":
                    completions.addAll(Arrays.asList("alerts", "logging"));
                    break;
            }
            return filterCompletions(completions, args[1]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .toList();
    }
}