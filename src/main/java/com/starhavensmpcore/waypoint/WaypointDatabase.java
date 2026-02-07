package com.starhavensmpcore.waypoint;

import com.starhavensmpcore.core.StarhavenSMPCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class WaypointDatabase {

    private final StarhavenSMPCore plugin;
    private final ExecutorService dbExecutor;
    private Connection connection;
    private volatile Thread dbThread;

    public WaypointDatabase(StarhavenSMPCore plugin) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StarhavenSMPCore-Waypoints-DB");
            thread.setDaemon(true);
            dbThread = thread;
            return thread;
        });
        connect();
    }

    public void saveWaypoint(WaypointSaveRequest request) {
        if (connection == null || request == null || request.worldId == null || request.type == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO waypoints (world, x, y, z, type, category, name, owner, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, request.worldId.toString());
                statement.setInt(2, request.x);
                statement.setInt(3, request.y);
                statement.setInt(4, request.z);
                statement.setString(5, request.type);
                statement.setString(6, request.category);
                statement.setString(7, request.name);
                statement.setString(8, request.ownerId == null ? null : request.ownerId.toString());
                statement.setLong(9, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save waypoint: " + e.getMessage());
            }
        });
    }

    public List<WaypointRecord> loadWaypoints() {
        if (connection == null) {
            return Collections.emptyList();
        }
        return callOnDbThread(() -> {
            List<WaypointRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT world, x, y, z, type, category, name, owner FROM waypoints")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String world = resultSet.getString("world");
                        if (world == null) {
                            continue;
                        }
                        records.add(new WaypointRecord(
                                UUID.fromString(world),
                                resultSet.getInt("x"),
                                resultSet.getInt("y"),
                                resultSet.getInt("z"),
                                resultSet.getString("type"),
                                resultSet.getString("category"),
                                resultSet.getString("name"),
                                resultSet.getString("owner")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load waypoints: " + e.getMessage());
            }
            return records;
        });
    }

    public void deleteWaypoint(UUID worldId, int x, int y, int z) {
        if (connection == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM waypoints WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, worldId.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete waypoint: " + e.getMessage());
            }
            deleteKnownForWaypoint(worldId, x, y, z);
        });
    }

    public void updateWaypointCategory(UUID worldId, int x, int y, int z, String category) {
        if (connection == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE waypoints SET category = ? WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, category);
                statement.setString(2, worldId.toString());
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update waypoint category: " + e.getMessage());
            }
        });
    }

    public void updateWaypointName(UUID worldId, int x, int y, int z, String name) {
        if (connection == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE waypoints SET name = ? WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, name);
                statement.setString(2, worldId.toString());
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update waypoint name: " + e.getMessage());
            }
        });
    }

    public void upsertKnownWaypoint(UUID playerId, UUID worldId, int x, int y, int z, String name) {
        if (connection == null || playerId == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO known_waypoints (player, world, x, y, z, name, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, worldId.toString());
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.setString(6, name);
                statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save known waypoint: " + e.getMessage());
            }
        });
    }

    public void deleteKnownWaypoint(UUID playerId, UUID worldId, int x, int y, int z) {
        if (connection == null || playerId == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM known_waypoints WHERE player = ? AND world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, worldId.toString());
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete known waypoint: " + e.getMessage());
            }
        });
    }

    public boolean hasKnownWaypoint(UUID playerId, UUID worldId, int x, int y, int z) {
        if (connection == null || playerId == null || worldId == null) {
            return false;
        }
        Boolean result = callOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM known_waypoints WHERE player = ? AND world = ? AND x = ? AND y = ? AND z = ? LIMIT 1")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, worldId.toString());
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to check known waypoint: " + e.getMessage());
            }
            return false;
        });
        return result != null && result;
    }

    public List<KnownWaypointRecord> loadKnownWaypoints(UUID playerId) {
        if (connection == null || playerId == null) {
            return Collections.emptyList();
        }
        return callOnDbThread(() -> {
            List<KnownWaypointRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT world, x, y, z, name FROM known_waypoints WHERE player = ?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String world = resultSet.getString("world");
                        if (world == null) {
                            continue;
                        }
                        records.add(new KnownWaypointRecord(
                                UUID.fromString(world),
                                resultSet.getInt("x"),
                                resultSet.getInt("y"),
                                resultSet.getInt("z"),
                                resultSet.getString("name")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load known waypoints: " + e.getMessage());
            }
            return records;
        });
    }

    public void deleteKnownForWaypoint(UUID worldId, int x, int y, int z) {
        if (connection == null || worldId == null) {
            return;
        }
        runOnDbThread(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM known_waypoints WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                statement.setString(1, worldId.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete known waypoints: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        dbExecutor.shutdown();
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close waypoint database: " + e.getMessage());
        }
    }

    private void runOnDbThread(Runnable task) {
        if (task == null) {
            return;
        }
        if (Thread.currentThread() == dbThread) {
            task.run();
            return;
        }
        dbExecutor.execute(task);
    }

    private <T> T callOnDbThread(Callable<T> task) {
        if (task == null) {
            return null;
        }
        if (Thread.currentThread() == dbThread) {
            try {
                return task.call();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        Future<T> future = dbExecutor.submit(task);
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void connect() {
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for waypoints.");
            return;
        }
        File dbFile = new File(dataDir, "waypoints.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTable();
            ensureWaypointColumn("category");
            ensureWaypointColumn("name");
            createKnownTable();
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().warning("Failed to initialize waypoints database: " + e.getMessage());
        }
    }

    private void createTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS waypoints (" +
                            "world TEXT NOT NULL," +
                            "x INTEGER NOT NULL," +
                            "y INTEGER NOT NULL," +
                            "z INTEGER NOT NULL," +
                            "type TEXT NOT NULL," +
                            "category TEXT," +
                            "name TEXT," +
                            "owner TEXT," +
                            "created_at INTEGER NOT NULL," +
                            "PRIMARY KEY (world, x, y, z)" +
                            ")"
            );
        }
    }

    private void ensureWaypointColumn(String column) {
        if (column == null || column.trim().isEmpty()) {
            return;
        }
        boolean hasColumn = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(waypoints)")) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                if (column.equalsIgnoreCase(name)) {
                    hasColumn = true;
                    break;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to check waypoints schema: " + e.getMessage());
            return;
        }
        if (hasColumn) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE waypoints ADD COLUMN " + column + " TEXT");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add " + column + " column to waypoints: " + e.getMessage());
        }
    }

    private void createKnownTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS known_waypoints (" +
                            "player TEXT NOT NULL," +
                            "world TEXT NOT NULL," +
                            "x INTEGER NOT NULL," +
                            "y INTEGER NOT NULL," +
                            "z INTEGER NOT NULL," +
                            "name TEXT," +
                            "created_at INTEGER NOT NULL," +
                            "PRIMARY KEY (player, world, x, y, z)" +
                            ")"
            );
        }
    }

    public static final class WaypointRecord {
        public final UUID worldId;
        public final int x;
        public final int y;
        public final int z;
        public final String type;
        public final String category;
        public final String name;
        public final String owner;

        public WaypointRecord(UUID worldId, int x, int y, int z, String type, String category, String name, String owner) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.category = category;
            this.name = name;
            this.owner = owner;
        }
    }

    public static final class WaypointSaveRequest {
        public final UUID worldId;
        public final int x;
        public final int y;
        public final int z;
        public final String type;
        public final String category;
        public final String name;
        public final UUID ownerId;

        public WaypointSaveRequest(UUID worldId, int x, int y, int z, String type, String category, String name, UUID ownerId) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.category = category;
            this.name = name;
            this.ownerId = ownerId;
        }
    }

    public static final class KnownWaypointRecord {
        public final UUID worldId;
        public final int x;
        public final int y;
        public final int z;
        public final String name;

        public KnownWaypointRecord(UUID worldId, int x, int y, int z, String name) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }
    }
}
