package com.starhavensmpcore.resourcepack;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.starhavensmpcore.core.StarhavenSMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class ResourcePackManager implements Listener {
    private static final Gson GSON = new Gson();
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String USER_AGENT = "StarhavenSMPCore";

    private final StarhavenSMPCore plugin;
    private BukkitTask refreshTask;

    private boolean enabled;
    private String githubOwner;
    private String githubRepo;
    private String assetPrefix;
    private String assetName;
    private String prompt;
    private boolean force;
    private long refreshSeconds;
    private String githubToken;

    private volatile String packUrl;
    private volatile byte[] packHash;
    private volatile String packAssetName;

    public ResourcePackManager(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        loadConfig();
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (!enabled) {
            return;
        }
        checkLatestAsync();
        if (refreshSeconds > 0) {
            long periodTicks = Math.max(20L, refreshSeconds * 20L);
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkLatestAsync, periodTicks, periodTicks);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled || packUrl == null || packHash == null) {
            return;
        }
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendPack(player), 20L);
    }

    private void sendPack(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (packUrl == null || packHash == null) {
            return;
        }
        player.setResourcePack(packUrl, packHash, prompt, force);
    }

    private void loadConfig() {
        FileConfiguration config = loadConfigFile();
        enabled = config.getBoolean("ResourcePack.Enabled", false);
        String ownerConfig = config.getString("ResourcePack.GitHubOwner", "").trim();
        String repoConfig = config.getString("ResourcePack.GitHubRepo", "").trim();
        RepoInfo repoInfo = normalizeRepo(ownerConfig, repoConfig);
        githubOwner = repoInfo.owner;
        githubRepo = repoInfo.repo;
        assetPrefix = config.getString("ResourcePack.AssetPrefix", "StarhavenSMP-ResourcePack-").trim();
        assetName = config.getString("ResourcePack.AssetName", "").trim();
        prompt = config.getString("ResourcePack.Prompt", "This server requires the StarhavenSMP resource pack.");
        force = config.getBoolean("ResourcePack.Force", true);
        refreshSeconds = config.getLong("ResourcePack.RefreshSeconds", 3600L);
        githubToken = config.getString("ResourcePack.GitHubToken", "").trim();
    }

    private FileConfiguration loadConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        return YamlConfiguration.loadConfiguration(configFile);
    }

    private void checkLatestAsync() {
        if (!enabled) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkLatest);
    }

    private void checkLatest() {
        if (githubOwner.isEmpty() || githubRepo.isEmpty()) {
            plugin.getLogger().warning("Resource pack GitHub repo is not configured. Skipping release check.");
            return;
        }

        Release release = fetchLatestRelease();
        if (release == null || release.assets == null || release.assets.isEmpty()) {
            plugin.getLogger().warning("No assets found on latest release for resource pack.");
            return;
        }

        Asset asset = selectAsset(release.assets);
        if (asset == null || asset.browserDownloadUrl == null || asset.browserDownloadUrl.isEmpty()) {
            plugin.getLogger().warning("No matching resource pack asset found on latest release.");
            return;
        }

        if (asset.name != null && asset.name.equals(packAssetName) && packUrl != null && packHash != null) {
            return;
        }

        byte[] hash = downloadAndHash(asset.browserDownloadUrl, asset.name);
        if (hash == null) {
            return;
        }

        packUrl = asset.browserDownloadUrl;
        packHash = hash;
        packAssetName = asset.name;

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::sendPack));
        plugin.getLogger().info("Updated resource pack to " + asset.name);
    }

    private Release fetchLatestRelease() {
        String url = GITHUB_API_BASE + "/repos/" + githubOwner + "/" + githubRepo + "/releases/latest";
        try {
            HttpURLConnection connection = openConnection(new URL(url), "application/vnd.github+json");
            int code = connection.getResponseCode();
            if (code != 200) {
                plugin.getLogger().warning("GitHub API returned status " + code + " while checking resource pack release.");
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                return GSON.fromJson(reader, Release.class);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to check resource pack release: " + e.getMessage());
            return null;
        }
    }

    private Asset selectAsset(List<Asset> assets) {
        if (!assetName.isEmpty()) {
            for (Asset asset : assets) {
                if (assetName.equals(asset.name)) {
                    return asset;
                }
            }
            return null;
        }

        for (Asset asset : assets) {
            if (asset.name == null) {
                continue;
            }
            if (!assetPrefix.isEmpty() && !asset.name.startsWith(assetPrefix)) {
                continue;
            }
            if (!asset.name.toLowerCase().endsWith(".zip")) {
                continue;
            }
            return asset;
        }
        return null;
    }

    private byte[] downloadAndHash(String downloadUrl, String assetName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            File cacheDir = new File(plugin.getDataFolder(), "resourcepack");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                plugin.getLogger().warning("Unable to create resource pack cache directory.");
            }
            File target = new File(cacheDir, assetName == null ? "resourcepack.zip" : assetName);

            HttpURLConnection connection = openConnection(new URL(downloadUrl), "application/octet-stream");
            int code = connection.getResponseCode();
            if (code != 200) {
                plugin.getLogger().warning("Failed to download resource pack asset. HTTP " + code);
                return null;
            }

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }

            deleteOtherPacks(cacheDir, target.getName());
            return digest.digest();
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().warning("Failed to download resource pack asset: " + e.getMessage());
            return null;
        }
    }

    private void deleteOtherPacks(File cacheDir, String keepName) {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            if (!file.getName().equals(keepName)) {
                if (!file.delete()) {
                    plugin.getLogger().warning("Failed to delete old resource pack " + file.getName());
                }
            }
        }
    }

    private HttpURLConnection openConnection(URL url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (!githubToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "token " + githubToken);
        }
        return connection;
    }

    private RepoInfo normalizeRepo(String ownerConfig, String repoConfig) {
        String owner = ownerConfig == null ? "" : ownerConfig.trim();
        String repo = repoConfig == null ? "" : repoConfig.trim();

        if (!repo.isEmpty()) {
            String candidate = repo;
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                try {
                    URL url = new URL(candidate);
                    String path = url.getPath();
                    if (path != null) {
                        String trimmed = path.startsWith("/") ? path.substring(1) : path;
                        String[] parts = trimmed.split("/");
                        if (parts.length >= 2) {
                            owner = parts[0];
                            repo = parts[1];
                        }
                    }
                } catch (IOException ignored) {
                    // Fall back to other parsing paths.
                }
            } else if (candidate.contains("/")) {
                String[] parts = candidate.split("/");
                if (parts.length >= 2) {
                    owner = parts[0];
                    repo = parts[1];
                }
            }
        }

        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }

        return new RepoInfo(owner, repo);
    }

    private static final class RepoInfo {
        private final String owner;
        private final String repo;

        private RepoInfo(String owner, String repo) {
            this.owner = owner == null ? "" : owner;
            this.repo = repo == null ? "" : repo;
        }
    }

    private static class Release {
        List<Asset> assets;
    }

    private static class Asset {
        String name;
        @SerializedName("browser_download_url")
        String browserDownloadUrl;
    }
}
