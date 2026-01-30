package com.starhavensmpcore.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ConfigHandler {
    private ConfigHandler() {
    }

    public static FileConfiguration loadAndUpdate(JavaPlugin plugin, File configFile) {
        YamlConfiguration defaults = loadDefaults(plugin);
        YamlConfiguration current = YamlConfiguration.loadConfiguration(configFile);

        Set<String> allowedPaths = defaults == null ? Collections.emptySet() : defaults.getKeys(true);

        YamlConfiguration sanitized = new YamlConfiguration();
        if (defaults != null) {
            for (String path : allowedPaths) {
                if (defaults.isConfigurationSection(path)) {
                    continue;
                }
                if (current.contains(path)) {
                    sanitized.set(path, current.get(path));
                } else {
                    sanitized.set(path, defaults.get(path));
                }
            }
        }

        YamlConfiguration unknown = new YamlConfiguration();
        for (String path : current.getKeys(true)) {
            if (current.isConfigurationSection(path)) {
                continue;
            }
            if (!allowedPaths.contains(path)) {
                unknown.set(path, current.get(path));
            }
        }

        writeConfig(configFile, sanitized, unknown, plugin);
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private static YamlConfiguration loadDefaults(JavaPlugin plugin) {
        InputStream defaultsStream = plugin.getResource("config.yml");
        if (defaultsStream == null) {
            return null;
        }
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load default config: " + ex.getMessage());
        }
        return defaults;
    }

    private static void writeConfig(File configFile,
                                    YamlConfiguration sanitized,
                                    YamlConfiguration unknown,
                                    JavaPlugin plugin) {
        StringBuilder output = new StringBuilder();
        String sanitizedText = sanitized == null ? "" : sanitized.saveToString().trim();
        if (!sanitizedText.isEmpty()) {
            output.append(sanitizedText).append(System.lineSeparator());
        }

        if (unknown != null && !unknown.getKeys(true).isEmpty()) {
            output.append(System.lineSeparator());
            output.append("# Not recognized config entries (commented out).")
                    .append(System.lineSeparator());
            output.append("# Review and remove or update them if needed.")
                    .append(System.lineSeparator());
            String unknownText = unknown.saveToString().trim();
            if (!unknownText.isEmpty()) {
                for (String line : unknownText.split("\\r?\\n")) {
                    output.append("# ").append(line).append(System.lineSeparator());
                }
            }
        }

        try {
            Files.write(configFile.toPath(), output.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write updated config: " + ex.getMessage());
        }
    }
}
