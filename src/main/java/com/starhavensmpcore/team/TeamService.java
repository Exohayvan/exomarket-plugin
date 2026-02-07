package com.starhavensmpcore.team;

import com.starhavensmpcore.core.StarhavenSMPCore;
import com.starhavensmpcore.market.MarketItem;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.economy.EconomyManager;
import com.starhavensmpcore.market.items.FamilyBreakdown;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TeamService {

    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(5);

    private final StarhavenSMPCore plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final Map<UUID, CachedTotals> cache = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile boolean available = false;
    private Method getTeamByUuid;
    private Method getTeamByOfflinePlayer;
    private Method getTeamByPlayer;
    private Method getTeamByName;
    private Method getMembers;
    private Method getOfflinePlayers;
    private Method getOnlinePlayers;

    public TeamService(StarhavenSMPCore plugin, DatabaseManager databaseManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
    }

    public boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public TeamTotals getTeamTotals(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return TeamTotals.empty();
        }
        ensureInitialized();
        if (!available) {
            return TeamTotals.empty();
        }
        UUID key = player.getUniqueId();
        if (plugin.getServer().isPrimaryThread()) {
            CachedTotals cached = cache.get(key);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestampMs <= CACHE_TTL_MS) {
                return cached.totals;
            }
            scheduleRefresh(player);
            return cached == null ? TeamTotals.empty() : cached.totals;
        }
        TeamTotals totals = computeTeamTotals(player);
        cache.put(key, new CachedTotals(totals, System.currentTimeMillis()));
        return totals;
    }

    private void scheduleRefresh(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return;
        }
        UUID key = player.getUniqueId();
        CachedTotals cached = cache.get(key);
        if (cached != null && cached.refreshing) {
            return;
        }
        cache.put(key, CachedTotals.refreshing(cached));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            TeamTotals totals = computeTeamTotals(player);
            cache.put(key, new CachedTotals(totals, System.currentTimeMillis()));
        });
    }

    private TeamTotals computeTeamTotals(OfflinePlayer player) {
        Object team = getTeam(player);
        if (team == null) {
            return TeamTotals.empty();
        }

        List<OfflinePlayer> members = getTeamMembers(team, player);
        if (members.isEmpty()) {
            return TeamTotals.empty();
        }

        double totalEco = 0d;
        BigInteger totalItems = BigInteger.ZERO;
        for (OfflinePlayer member : members) {
            if (member == null || member.getUniqueId() == null) {
                continue;
            }
            totalEco += economyManager.getBalance(member);
            totalItems = totalItems.add(getListedItemsFor(member.getUniqueId()));
        }

        return new TeamTotals(totalEco, totalItems);
    }

    private BigInteger getListedItemsFor(UUID playerUuid) {
        if (playerUuid == null) {
            return BigInteger.ZERO;
        }
        List<MarketItem> listings = databaseManager.getMarketItemsByOwner(playerUuid.toString());
        BigInteger total = BigInteger.ZERO;
        for (MarketItem listing : listings) {
            if (isHiddenListing(listing.getItemStack())) {
                continue;
            }
            total = total.add(listing.getQuantity().max(BigInteger.ZERO));
        }
        return total;
    }

    private boolean isHiddenListing(ItemStack stack) {
        return FamilyBreakdown.isFamilySmall(stack);
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
            try {
                Plugin betterTeams = plugin.getServer().getPluginManager().getPlugin("BetterTeams");
                if (betterTeams == null || !betterTeams.isEnabled()) {
                    return;
                }
                ClassLoader loader = betterTeams.getClass().getClassLoader();
                Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team", true, loader);
                for (Method method : teamClass.getMethods()) {
                    if (!"getTeam".equals(method.getName())) {
                        continue;
                    }
                    if (!Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length != 1) {
                        continue;
                    }
                    Class<?> param = params[0];
                    if (OfflinePlayer.class.isAssignableFrom(param)) {
                        getTeamByOfflinePlayer = method;
                    } else if (UUID.class.isAssignableFrom(param)) {
                        getTeamByUuid = method;
                    } else if (Player.class.isAssignableFrom(param)) {
                        getTeamByPlayer = method;
                    } else if (String.class.isAssignableFrom(param)) {
                        getTeamByName = method;
                    }
                }

                getMembers = findMembersMethod(teamClass);

                Class<?> memberComponent = null;
                try {
                    memberComponent = Class.forName("com.booksaw.betterTeams.team.MemberSetComponent", true, loader);
                } catch (ClassNotFoundException ignored) {
                    // Optional; fall back to member collections directly.
                }

                if (memberComponent != null) {
                    try {
                        getOfflinePlayers = memberComponent.getMethod("getOfflinePlayers");
                    } catch (NoSuchMethodException ignored) {
                        // Optional
                    }
                    try {
                        getOnlinePlayers = memberComponent.getMethod("getOnlinePlayers");
                    } catch (NoSuchMethodException ignored) {
                        // Optional
                    }
                }

                available = hasTeamLookup() && getMembers != null;
            } catch (Exception ex) {
                available = false;
                plugin.getLogger().warning("BetterTeams integration failed to initialize: " + ex.getMessage());
            }
        }
    }

    private boolean hasTeamLookup() {
        return getTeamByOfflinePlayer != null || getTeamByUuid != null || getTeamByPlayer != null || getTeamByName != null;
    }

    private Object getTeam(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        Object team = invokeTeamLookup(getTeamByOfflinePlayer, player);
        if (team != null) {
            return team;
        }
        UUID uuid = player.getUniqueId();
        if (uuid != null) {
            team = invokeTeamLookup(getTeamByUuid, uuid);
            if (team != null) {
                return team;
            }
        }
        Player online = player.getPlayer();
        if (online != null) {
            team = invokeTeamLookup(getTeamByPlayer, online);
            if (team != null) {
                return team;
            }
        }
        String name = player.getName();
        if (name != null && !name.isEmpty()) {
            team = invokeTeamLookup(getTeamByName, name);
            if (team != null) {
                return team;
            }
        }
        return null;
    }

    private Object invokeTeamLookup(Method method, Object argument) {
        if (method == null || argument == null) {
            return null;
        }
        try {
            return method.invoke(null, argument);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<OfflinePlayer> getTeamMembers(Object team, OfflinePlayer requester) {
        if (team == null || getMembers == null) {
            return Collections.emptyList();
        }
        try {
            Object members = getMembers.invoke(team);
            if (members == null) {
                return Collections.emptyList();
            }

            List<OfflinePlayer> direct = coerceMembers(members);
            if (!direct.isEmpty()) {
                return ensureRequester(direct, requester);
            }

            if (getOfflinePlayers != null) {
                Object result = getOfflinePlayers.invoke(members);
                List<OfflinePlayer> offline = coerceMembers(result);
                if (!offline.isEmpty()) {
                    return ensureRequester(offline, requester);
                }
            }
            if (getOnlinePlayers != null) {
                Object result = getOnlinePlayers.invoke(members);
                List<OfflinePlayer> online = coerceMembers(result);
                if (!online.isEmpty()) {
                    return ensureRequester(online, requester);
                }
            }

            List<OfflinePlayer> fallbackOffline = coerceMembers(invokeMemberList(members, "getOfflinePlayers"));
            if (!fallbackOffline.isEmpty()) {
                return ensureRequester(fallbackOffline, requester);
            }
            List<OfflinePlayer> fallbackOnline = coerceMembers(invokeMemberList(members, "getOnlinePlayers"));
            if (!fallbackOnline.isEmpty()) {
                return ensureRequester(fallbackOnline, requester);
            }
            List<OfflinePlayer> fallbackPlayers = coerceMembers(invokeMemberList(members, "getPlayers"));
            if (!fallbackPlayers.isEmpty()) {
                return ensureRequester(fallbackPlayers, requester);
            }
        } catch (Exception ex) {
            // ignore
        }
        return Collections.emptyList();
    }

    private Method findMembersMethod(Class<?> teamClass) {
        for (String name : new String[]{"getMembers", "getMemberSet", "getMembersComponent", "getPlayers", "getOnlinePlayers", "getOfflinePlayers"}) {
            try {
                return teamClass.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        for (Method method : teamClass.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            String name = method.getName().toLowerCase();
            if (name.contains("member") || name.contains("player")) {
                return method;
            }
        }
        return null;
    }

    private List<OfflinePlayer> coerceMembers(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof Map) {
            return coerceMap((Map<?, ?>) value);
        }
        if (value instanceof Collection) {
            return coerceCollection((Collection<?>) value);
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            List<OfflinePlayer> result = new ArrayList<>();
            for (Object entry : array) {
                OfflinePlayer player = toOfflinePlayer(entry);
                if (player != null) {
                    result.add(player);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private List<OfflinePlayer> coerceCollection(Collection<?> collection) {
        if (collection.isEmpty()) {
            return Collections.emptyList();
        }
        List<OfflinePlayer> result = new ArrayList<>();
        for (Object entry : collection) {
            OfflinePlayer player = toOfflinePlayer(entry);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    private List<OfflinePlayer> coerceMap(java.util.Map<?, ?> map) {
        if (map.isEmpty()) {
            return Collections.emptyList();
        }
        List<OfflinePlayer> result = new ArrayList<>();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            OfflinePlayer player = toOfflinePlayer(entry.getKey());
            if (player == null) {
                player = toOfflinePlayer(entry.getValue());
            }
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    private OfflinePlayer toOfflinePlayer(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof OfflinePlayer) {
            return (OfflinePlayer) entry;
        }
        if (entry instanceof UUID) {
            return plugin.getServer().getOfflinePlayer((UUID) entry);
        }
        if (entry instanceof String) {
            OfflinePlayer byUuid = offlinePlayerFromString((String) entry);
            if (byUuid != null) {
                return byUuid;
            }
        }
        OfflinePlayer fromMethod = offlinePlayerFromMethod(entry);
        if (fromMethod != null) {
            return fromMethod;
        }
        UUID uuid = uuidFromMethod(entry);
        if (uuid != null) {
            return plugin.getServer().getOfflinePlayer(uuid);
        }
        String name = nameFromMethod(entry);
        if (name != null) {
            OfflinePlayer byName = getOfflinePlayerIfCached(name);
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    private OfflinePlayer offlinePlayerFromMethod(Object entry) {
        for (String name : new String[]{"getOfflinePlayer", "getPlayer"}) {
            Object result = invokeNoArg(entry, name);
            if (result instanceof OfflinePlayer) {
                return (OfflinePlayer) result;
            }
            if (result instanceof Player) {
                return ((Player) result).getPlayer();
            }
        }
        return null;
    }

    private UUID uuidFromMethod(Object entry) {
        for (String name : new String[]{"getUniqueId", "getUniqueID", "getUuid", "getUUID", "getPlayerUUID", "getPlayerUuid"}) {
            Object result = invokeNoArg(entry, name);
            if (result instanceof UUID) {
                return (UUID) result;
            }
        }
        return null;
    }

    private String nameFromMethod(Object entry) {
        for (String name : new String[]{"getName", "getUsername", "getPlayerName"}) {
            Object result = invokeNoArg(entry, name);
            if (result instanceof String) {
                return (String) result;
            }
        }
        return null;
    }

    private OfflinePlayer offlinePlayerFromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return plugin.getServer().getOfflinePlayer(uuid);
        } catch (IllegalArgumentException ignored) {
            return getOfflinePlayerIfCached(value);
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer getOfflinePlayerIfCached(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Server server = plugin.getServer();
        try {
            Method method = server.getClass().getMethod("getOfflinePlayerIfCached", String.class);
            Object result = method.invoke(server, name);
            if (result instanceof OfflinePlayer) {
                return (OfflinePlayer) result;
            }
        } catch (Exception ignored) {
            // Method not available.
        }
        return server.getOfflinePlayer(name);
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<OfflinePlayer> ensureRequester(List<OfflinePlayer> members, OfflinePlayer requester) {
        if (requester == null || requester.getUniqueId() == null) {
            return members;
        }
        for (OfflinePlayer member : members) {
            if (member != null && requester.getUniqueId().equals(member.getUniqueId())) {
                return members;
            }
        }
        List<OfflinePlayer> expanded = new ArrayList<>(members);
        expanded.add(requester);
        return expanded;
    }

    private Object invokeMemberList(Object members, String methodName) {
        if (members == null || methodName == null) {
            return null;
        }
        try {
            Method method = members.getClass().getMethod(methodName);
            return method.invoke(members);
        } catch (Exception ex) {
            return null;
        }
    }

    public static class TeamTotals {
        private final double totalEco;
        private final BigInteger totalItems;

        public TeamTotals(double totalEco, BigInteger totalItems) {
            this.totalEco = totalEco;
            this.totalItems = totalItems == null ? BigInteger.ZERO : totalItems;
        }

        public double getTotalEco() {
            return totalEco;
        }

        public BigInteger getTotalItems() {
            return totalItems;
        }

        public static TeamTotals empty() {
            return new TeamTotals(0d, BigInteger.ZERO);
        }
    }

    private static final class CachedTotals {
        private final TeamTotals totals;
        private final long timestampMs;
        private final boolean refreshing;

        private CachedTotals(TeamTotals totals, long timestampMs, boolean refreshing) {
            this.totals = totals == null ? TeamTotals.empty() : totals;
            this.timestampMs = timestampMs;
            this.refreshing = refreshing;
        }

        private CachedTotals(TeamTotals totals, long timestampMs) {
            this(totals, timestampMs, false);
        }

        private static CachedTotals refreshing(CachedTotals prior) {
            if (prior == null) {
                return new CachedTotals(TeamTotals.empty(), 0L, true);
            }
            return new CachedTotals(prior.totals, prior.timestampMs, true);
        }
    }
}
