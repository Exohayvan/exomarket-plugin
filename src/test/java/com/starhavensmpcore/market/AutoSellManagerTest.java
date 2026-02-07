package com.starhavensmpcore.market;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.starhavensmpcore.market.db.DatabaseManager;
import com.starhavensmpcore.market.items.ItemSanitizer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoSellManagerTest {

    private ServerMock server;
    private StarhavenSMPCoreTestPlugin plugin;
    private TrackingDatabaseManager trackingDatabaseManager;
    private AutoSellManager autoSellManager;

    @Before
    public void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(StarhavenSMPCoreTestPlugin.class);
        trackingDatabaseManager = new TrackingDatabaseManager(plugin);
        plugin.setDatabaseManager(trackingDatabaseManager);
        ensureAutoSellDbDir();
        deleteAutoSellDb();
        autoSellManager = new TestAutoSellManager(plugin, Material.DIRT);
    }

    @After
    public void tearDown() throws Exception {
        closeAutoSellConnection();
        MockBukkit.unmock();
        deleteAutoSellDb();
    }

    @Test
    public void countMatchingItemsSumsAmounts() {
        ItemStack diamondThree = new ItemStack(Material.DIAMOND, 3);
        ItemStack dirtTwo = new ItemStack(Material.DIRT, 2);
        ItemStack diamondOne = new ItemStack(Material.DIAMOND, 1);
        ItemStack template = new ItemStack(Material.DIAMOND, 1);

        ItemStack[] contents = new ItemStack[] { diamondThree, dirtTwo, diamondOne };

        assertEquals(4, AutoSellManager.countMatchingItems(contents, template));
    }

    @Test
    public void removeMatchingItemsRemovesAcrossStacks() {
        ItemStack diamondTwo = new ItemStack(Material.DIAMOND, 2);
        ItemStack diamondThree = new ItemStack(Material.DIAMOND, 3);
        ItemStack dirtOne = new ItemStack(Material.DIRT, 1);
        ItemStack template = new ItemStack(Material.DIAMOND, 1);

        ItemStack[] contents = new ItemStack[] { diamondTwo, diamondThree, dirtOne };

        AutoSellManager.removeMatchingItems(contents, template, 4);

        assertNull(contents[0]);
        assertEquals(1, contents[1].getAmount());
        assertEquals(Material.DIRT, contents[2].getType());
        assertEquals(1, contents[2].getAmount());
    }

    @Test
    public void commandOpensInventoryAndRendersNavigation() throws Exception {
        PlayerMock player = server.addPlayer();
        autoSellManager.onCommand(player, mockCommand(), "autosell", new String[0]);

        Inventory top = getInventoryMap().get(player.getUniqueId());
        assertEquals(Material.BARRIER, top.getItem(48).getType());
        assertEquals(Material.BARRIER, top.getItem(50).getType());
        assertEquals(Material.PAPER, top.getItem(49).getType());
    }

    @Test
    public void clickTogglesAutoSellEntry() throws Exception {
        PlayerMock player = server.addPlayer();
        autoSellManager.onCommand(player, mockCommand(), "autosell", new String[0]);

        Inventory top = getInventoryMap().get(player.getUniqueId());
        ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
        top.setItem(0, diamond);

        InventoryView view = new TestInventoryView(player, top, "AutoSell Inventory");
        InventoryClickEvent click = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        autoSellManager.onInventoryClick(click);

        List<ItemStack> items = AutoSellManager.access$0(autoSellManager, player.getUniqueId());
        assertEquals(1, items.size());

        top.setItem(0, diamond);
        InventoryClickEvent clickAgain = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        autoSellManager.onInventoryClick(clickAgain);

        items = AutoSellManager.access$0(autoSellManager, player.getUniqueId());
        assertTrue(items.isEmpty());
    }

    @Test
    public void onInventoryCloseClearsState() throws Exception {
        PlayerMock player = server.addPlayer();
        autoSellManager.onCommand(player, mockCommand(), "autosell", new String[0]);
        Inventory top = getInventoryMap().get(player.getUniqueId());
        InventoryView view = new TestInventoryView(player, top, "AutoSell Inventory");

        InventoryCloseEvent closeEvent = new InventoryCloseEvent(view);
        autoSellManager.onInventoryClose(closeEvent);

        assertFalse(getInventoryMap().containsKey(player.getUniqueId()));
        assertFalse(getPageMap().containsKey(player.getUniqueId()));
    }

    @Test
    public void getAutoSellItemsDedupesAndSorts() throws Exception {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
        ItemStack dirt = new ItemStack(Material.DIRT, 1);

        insertAutoSellRow(playerId, diamond);
        insertAutoSellRow(playerId, diamond);
        insertAutoSellRow(playerId, dirt);

        List<ItemStack> items = AutoSellManager.access$0(autoSellManager, playerId);
        assertEquals(2, items.size());
        assertEquals(Material.DIAMOND, items.get(0).getType());
        assertEquals(Material.DIRT, items.get(1).getType());
    }

    @Test
    public void autoSellTaskRemovesDamagedEntries() throws Exception {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();

        ItemStack damaged = new ItemStack(Material.DIRT, 1);

        insertAutoSellRow(playerId, damaged);

        autoSellManager.runAutoSellTick();

        List<ItemStack> items = AutoSellManager.access$0(autoSellManager, playerId);
        assertTrue(items.isEmpty());
    }

    @Test
    public void autoSellTaskSellsAndRemovesInventoryItems() throws Exception {
        PlayerMock player = server.addPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack diamond = new ItemStack(Material.DIAMOND, 5);
        player.getInventory().addItem(diamond);

        insertAutoSellRow(playerId, new ItemStack(Material.DIAMOND, 1));

        autoSellManager.runAutoSellTick();

        assertEquals(0, AutoSellManager.countMatchingItems(player.getInventory().getContents(),
                new ItemStack(Material.DIAMOND, 1)));
        assertEquals(1, trackingDatabaseManager.sellCalls.size());
        SellCall call = trackingDatabaseManager.sellCalls.get(0);
        assertEquals(playerId, call.playerId);
        assertEquals(Material.DIAMOND, call.item.getType());
        assertEquals(5, call.quantity);
    }

    private void ensureAutoSellDbDir() throws Exception {
        File dir = new File("plugins/StarhavenSMPCore");
        Files.createDirectories(dir.toPath());
    }

    private void deleteAutoSellDb() throws Exception {
        File db = new File("plugins/StarhavenSMPCore/autosell.db");
        Files.deleteIfExists(db.toPath());
    }

    private void closeAutoSellConnection() throws Exception {
        if (autoSellManager == null) {
            return;
        }
        Field connectionField = AutoSellManager.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        java.sql.Connection connection = (java.sql.Connection) connectionField.get(autoSellManager);
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void insertAutoSellRow(UUID playerId, ItemStack item) throws Exception {
        String serialized = ItemSanitizer.serializeToString(item);
        Field connectionField = AutoSellManager.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        java.sql.Connection connection = (java.sql.Connection) connectionField.get(autoSellManager);
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO autosell_items (uuid, item) VALUES (?, ?)")) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, serialized);
            stmt.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<UUID, Inventory> getInventoryMap() throws Exception {
        Field field = AutoSellManager.class.getDeclaredField("autoSellInventories");
        field.setAccessible(true);
        return (java.util.Map<UUID, Inventory>) field.get(autoSellManager);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<UUID, Integer> getPageMap() throws Exception {
        Field field = AutoSellManager.class.getDeclaredField("currentPage");
        field.setAccessible(true);
        return (java.util.Map<UUID, Integer>) field.get(autoSellManager);
    }

    private Command mockCommand() {
        return null;
    }

    private static final class TestInventoryView implements InventoryView {
        private final PlayerMock player;
        private final Inventory top;
        private final Inventory bottom;
        private String title;
        private ItemStack cursor;

        private TestInventoryView(PlayerMock player, Inventory top, String title) {
            this.player = player;
            this.top = top;
            this.bottom = player.getInventory();
            this.title = title;
        }

        @Override
        public Inventory getTopInventory() {
            return top;
        }

        @Override
        public Inventory getBottomInventory() {
            return bottom;
        }

        @Override
        public PlayerMock getPlayer() {
            return player;
        }

        @Override
        public InventoryType getType() {
            return InventoryType.CHEST;
        }

        @Override
        public void setItem(int slot, ItemStack item) {
            getInventory(slot).setItem(convertSlot(slot), item);
        }

        @Override
        public ItemStack getItem(int slot) {
            return getInventory(slot).getItem(convertSlot(slot));
        }

        @Override
        public void setCursor(ItemStack item) {
            cursor = item;
        }

        @Override
        public ItemStack getCursor() {
            return cursor;
        }

        @Override
        public Inventory getInventory(int rawSlot) {
            return rawSlot < top.getSize() ? top : bottom;
        }

        @Override
        public int convertSlot(int rawSlot) {
            return rawSlot < top.getSize() ? rawSlot : rawSlot - top.getSize();
        }

        @Override
        public InventoryType.SlotType getSlotType(int slot) {
            return InventoryType.SlotType.CONTAINER;
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public int countSlots() {
            return top.getSize() + bottom.getSize();
        }

        @Override
        public boolean setProperty(Property prop, int value) {
            return false;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getOriginalTitle() {
            return title;
        }

        @Override
        public void setTitle(String title) {
            this.title = title;
        }
    }

    private static final class TrackingDatabaseManager extends DatabaseManager {
        private final List<SellCall> sellCalls = new ArrayList<>();

        private TrackingDatabaseManager(StarhavenSMPCoreTestPlugin plugin) {
            super(plugin);
        }

        @Override
        public void sellItemsDirectly(UUID playerUUID, ItemStack itemStack, int quantity) {
            sellCalls.add(new SellCall(playerUUID, itemStack.clone(), quantity));
        }
    }

    private static final class SellCall {
        private final UUID playerId;
        private final ItemStack item;
        private final int quantity;

        private SellCall(UUID playerId, ItemStack item, int quantity) {
            this.playerId = playerId;
            this.item = item;
            this.quantity = quantity;
        }
    }

    private static final class TestAutoSellManager extends AutoSellManager {
        private final Material forcedDamaged;

        private TestAutoSellManager(StarhavenSMPCoreTestPlugin plugin, Material forcedDamaged) {
            super(plugin);
            this.forcedDamaged = forcedDamaged;
        }

        @Override
        protected boolean isDamaged(ItemStack item) {
            return super.isDamaged(item)
                    || (item != null && item.getType() == forcedDamaged);
        }
    }
}
