package com.exomarket;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Minimal embedded HTTP server that exposes a read-only view of market items.
 */
@SuppressWarnings("restriction")
public class MarketWebServer {
    private final ExoMarketPlugin plugin;
    private final DatabaseManager databaseManager;
    private final int port;
    private HttpServer server;

    public MarketWebServer(ExoMarketPlugin plugin, DatabaseManager databaseManager, int port) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.port = port;
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handleIndex);
            server.createContext("/item", this::handleItem);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("Market web view available at http://localhost:" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start market web server on port " + port + ": " + e.getMessage());
            stop();
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("Market web server stopped");
        }
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Only GET is supported.");
            return;
        }

        Map<String, Aggregate> aggregates = aggregate(databaseManager.getMarketItems());
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>ExoMarket Listings</title>")
                .append("<style>")
                .append("body{font-family:'Trebuchet MS',Helvetica,Arial,sans-serif;background:#0f1724;color:#e9f1ff;margin:0;padding:24px;}")
                .append("h1{margin-top:0;}table{width:100%;border-collapse:collapse;margin-top:12px;}")
                .append("th,td{padding:10px 12px;border-bottom:1px solid #233146;text-align:left;}")
                .append("th{background:#1a2536;color:#9bc0ff;font-weight:700;cursor:pointer;user-select:none;}")
                .append("th.sort-asc::after{content:' ▲';font-size:12px;color:#7fb4ff;}")
                .append("th.sort-desc::after{content:' ▼';font-size:12px;color:#7fb4ff;}")
                .append("tr:hover td{background:#122038;}a{color:#7fb4ff;text-decoration:none;}a:hover{text-decoration:underline;}")
                .append(".pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#1f2f46;color:#b8dcff;font-size:12px;}")
                .append("</style></head><body>")
                .append("<h1>ExoMarket Listings</h1>")
                .append("<p class=\"pill\">Port ").append(port).append("</p>")
                .append("<table id=\"market-table\"><thead><tr>")
                .append("<th data-type=\"text\">Item</th>")
                .append("<th data-type=\"number\">Total Quantity</th>")
                .append("<th data-type=\"number\">Listings</th>")
                .append("<th data-type=\"number\">Lowest Price</th>")
                .append("<th data-type=\"text\"></th>")
                .append("</tr></thead><tbody>");

        for (Aggregate aggregate : aggregates.values()) {
            String encodedId = URLEncoder.encode(aggregate.itemData, "UTF-8");
            body.append("<tr>")
                    .append("<td data-value=\"").append(escapeHtml(aggregate.displayName)).append("\">")
                    .append(escapeHtml(aggregate.displayName)).append("</td>")
                    .append("<td data-value=\"").append(aggregate.totalQuantity.toString()).append("\">")
                    .append(QuantityFormatter.format(aggregate.totalQuantity)).append("</td>")
                    .append("<td data-value=\"").append(aggregate.listingCount).append("\">")
                    .append(aggregate.listingCount).append("</td>")
                    .append("<td data-value=\"").append(aggregate.lowestPrice).append("\">")
                    .append(escapeHtml(CurrencyFormatter.format(aggregate.lowestPrice))).append("</td>")
                    .append("<td data-value=\"\">")
                    .append("<a href=\"/item?id=").append(encodedId).append("\">View sellers</a></td>")
                    .append("</tr>");
        }

        if (aggregates.isEmpty()) {
            body.append("<tr><td colspan=\"5\">No items are listed right now.</td></tr>");
        }

        body.append("</tbody></table>")
                .append("<script>")
                .append("const table=document.getElementById('market-table');")
                .append("const headers=table.querySelectorAll('th');")
                .append("const tbody=table.querySelector('tbody');")
                .append("let sortIndex=-1,sortDir=1;")
                .append("function clearSort(){headers.forEach(h=>h.classList.remove('sort-asc','sort-desc'));}")
                .append("function getValue(cell,type){const raw=cell.getAttribute('data-value')||cell.textContent||'';")
                .append("return type==='number'?parseFloat(raw)||0:raw.toLowerCase();}")
                .append("headers.forEach((header,idx)=>{")
                .append("header.addEventListener('click',()=>{")
                .append("const type=header.getAttribute('data-type')||'text';")
                .append("sortDir=(sortIndex===idx)?-sortDir:1;")
                .append("sortIndex=idx;")
                .append("const rows=Array.from(tbody.querySelectorAll('tr'));")
                .append("rows.sort((a,b)=>{")
                .append("const aVal=getValue(a.children[idx],type);")
                .append("const bVal=getValue(b.children[idx],type);")
                .append("if(aVal<bVal)return -1*sortDir;")
                .append("if(aVal>bVal)return 1*sortDir;")
                .append("return 0;")
                .append("});")
                .append("rows.forEach(r=>tbody.appendChild(r));")
                .append("clearSort();")
                .append("header.classList.add(sortDir===1?'sort-asc':'sort-desc');")
                .append("});")
                .append("});")
                .append("</script></body></html>");
        respond(exchange, 200, body.toString());
    }

    private void handleItem(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "Only GET is supported.");
            return;
        }

        String rawQuery = exchange.getRequestURI().getRawQuery();
        String encodedId = parseQuery(rawQuery, "id");
        if (encodedId == null || encodedId.isEmpty()) {
            respond(exchange, 400, "Missing item id.");
            return;
        }

        String itemData = URLDecoder.decode(encodedId, "UTF-8");
        List<MarketItem> listings = databaseManager.getMarketItemsByItemData(itemData);

        if (listings.isEmpty()) {
            respond(exchange, 404, "This item is not currently listed.");
            return;
        }

        MarketItem sample = listings.get(0);
        String itemName = ItemDisplayNameFormatter.format(sample.getItemStack());
        BigInteger totalQuantity = listings.stream()
                .map(MarketItem::getQuantity)
                .reduce(BigInteger.ZERO, BigInteger::add);
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escapeHtml(itemName)).append(" | ExoMarket</title>")
                .append("<style>")
                .append("body{font-family:'Trebuchet MS',Helvetica,Arial,sans-serif;background:#0f1724;color:#e9f1ff;margin:0;padding:24px;}")
                .append("a{color:#7fb4ff;text-decoration:none;}a:hover{text-decoration:underline;}")
                .append("table{width:100%;border-collapse:collapse;margin-top:12px;}")
                .append("th,td{padding:10px 12px;border-bottom:1px solid #233146;text-align:left;}")
                .append("th{background:#1a2536;color:#9bc0ff;font-weight:700;}")
                .append("tr:hover td{background:#122038;}")
                .append("</style></head><body>")
                .append("<a href=\"/\">&#8592; Back to list</a>")
                .append("<h1>").append(escapeHtml(itemName)).append("</h1>")
                .append("<p>Total quantity available: ").append(QuantityFormatter.format(totalQuantity)).append("</p>")
                .append("<table><thead><tr><th>Seller</th><th>Quantity</th><th>Price (each)</th></tr></thead><tbody>");

        for (MarketItem listing : listings) {
            body.append("<tr>")
                    .append("<td>").append(escapeHtml(resolveSeller(listing.getSellerUUID()))).append("</td>")
                    .append("<td>").append(QuantityFormatter.format(listing.getQuantity())).append("</td>")
                    .append("<td>").append(escapeHtml(CurrencyFormatter.format(listing.getPrice()))).append("</td>")
                    .append("</tr>");
        }

        body.append("</tbody></table></body></html>");
        respond(exchange, 200, body.toString());
    }

    private Map<String, Aggregate> aggregate(List<MarketItem> items) {
        Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (MarketItem item : items) {
            Aggregate aggregate = aggregates.get(item.getItemData());
            if (aggregate == null) {
                aggregate = new Aggregate(item.getItemStack(), item.getItemData());
                aggregates.put(item.getItemData(), aggregate);
            }
            aggregate.totalQuantity = aggregate.totalQuantity.add(item.getQuantity());
            aggregate.listingCount += 1;
            aggregate.lowestPrice = Math.min(aggregate.lowestPrice, item.getPrice());
        }
        return aggregates;
    }

    private String resolveSeller(String sellerUuid) {
        String known = databaseManager.getLastKnownName(sellerUuid);
        if (known != null && !known.isEmpty()) {
            return known;
        }
        try {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(UUID.fromString(sellerUuid));
            if (offlinePlayer != null && offlinePlayer.getName() != null) {
                databaseManager.recordPlayerName(offlinePlayer.getUniqueId(), offlinePlayer.getName());
                return offlinePlayer.getName();
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to return UUID
        }
        return sellerUuid;
    }

    private String parseQuery(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx == -1) {
                continue;
            }
            String queryKey = pair.substring(0, idx);
            if (key.equalsIgnoreCase(queryKey)) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static class Aggregate {
        private final String displayName;
        private final String itemData;
        private BigInteger totalQuantity = BigInteger.ZERO;
        private int listingCount = 0;
        private double lowestPrice = Double.MAX_VALUE;

        Aggregate(ItemStack stack, String itemData) {
            this.displayName = ItemDisplayNameFormatter.format(stack);
            this.itemData = itemData;
        }
    }
}
