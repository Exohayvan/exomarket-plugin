package com.starhavensmpcore.market.items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FamilyList {
    private static final List<Family> FAMILIES;

    static {
        List<Family> families = new ArrayList<>();
        families.add(new Family(
                "iron_ingot",
                "IRON",
                "Iron Ingots",
                "Iron Nuggets",
                "iron_nugget",
                BigInteger.valueOf(9),
                "Iron Blocks",
                "iron_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "gold_ingot",
                "GOLD",
                "Gold Ingots",
                "Gold Nuggets",
                "gold_nugget",
                BigInteger.valueOf(9),
                "Gold Blocks",
                "gold_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "copper_ingot",
                "COPPER",
                "Copper Ingots",
                "Copper Nuggets",
                "copper_nugget",
                BigInteger.valueOf(9),
                "Copper Blocks",
                "copper_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "diamond",
                "DIAMOND",
                "Diamonds",
                null,
                null,
                null,
                "Diamond Blocks",
                "diamond_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "lapis_lazuli",
                "LAPIS",
                "Lapis",
                null,
                null,
                null,
                "Lapis Blocks",
                "lapis_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "coal",
                "COAL",
                "Coal",
                null,
                null,
                null,
                "Coal Blocks",
                "coal_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "emerald",
                "EMERALD",
                "Emeralds",
                null,
                null,
                null,
                "Emerald Blocks",
                "emerald_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "netherite_ingot",
                "NETHERITE",
                "Netherite Ingots",
                null,
                null,
                null,
                "Netherite Blocks",
                "netherite_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "resin_clump",
                "RESIN",
                "Resin Clumps",
                null,
                null,
                null,
                "Resin Blocks",
                "resin_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "slime_ball",
                "SLIME",
                "Slime Balls",
                null,
                null,
                null,
                "Slime Blocks",
                "slime_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "redstone",
                "REDSTONE",
                "Restone Dust",
                null,
                null,
                null,
                "Restone Blocks",
                "redstone_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "raw_iron",
                "RAW IRON",
                "Raw Iron",
                null,
                null,
                null,
                "Raw Iron Blocks",
                "raw_iron_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "raw_gold",
                "RAW GOLD",
                "Raw Gold",
                null,
                null,
                null,
                "Raw Gold Blocks",
                "raw_gold_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "raw_copper",
                "RAW COPPER",
                "Raw Copper",
                null,
                null,
                null,
                "Raw Copper Blocks",
                "raw_copper_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "tin_ingot",
                "TIN",
                "Tin Ingots",
                null,
                null,
                null,
                "Tin Blocks",
                "tin_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "raw_tin",
                "RAW TIN",
                "Raw Tin",
                null,
                null,
                null,
                "Raw Tin Blocks",
                "raw_tin_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "cobalt_ingot",
                "COBALT",
                "Ingots",
                null,
                null,
                null,
                "Cobalt Blocks",
                "cobalt_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "raw_cobalt",
                "RAW COBALT",
                "Raw Cobalt",
                null,
                null,
                null,
                "Raw Cobalt Blocks",
                "raw_cobalt_block",
                BigInteger.valueOf(9)
        ));
        families.add(new Family(
                "oak_planks",
                "OAK",
                "Oak Planks",
                "Oak Slabs",
                "oak_slab",
                BigInteger.valueOf(2),
                "Oak Logs",
                "oak_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "dark_oak_planks",
                "DARK OAK",
                "Dark Oak Planks",
                "Dark Oak Slabs",
                "dark_oak_slab",
                BigInteger.valueOf(2),
                "Dark Oak Logs",
                "dark_oak_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "spruce_planks",
                "SPRUCE",
                "Spruce Planks",
                "Spruce Slabs",
                "spruce_slab",
                BigInteger.valueOf(2),
                "Spruce Logs",
                "spruce_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "birch_planks",
                "BIRCH",
                "Birch Planks",
                "Birch Slabs",
                "birch_slab",
                BigInteger.valueOf(2),
                "Birch Logs",
                "birch_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "jungle_planks",
                "JUNGLE",
                "Jungle Planks",
                "Jungle Slabs",
                "jungle_slab",
                BigInteger.valueOf(2),
                "Jungle Logs",
                "jungle_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "acacia_planks",
                "ACACIA",
                "Acacia Planks",
                "Acacia Slabs",
                "acacia_slab",
                BigInteger.valueOf(2),
                "Acacia Logs",
                "acacia_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "cherry_planks",
                "CHERRY",
                "Cherry Planks",
                "Cherry Slabs",
                "cherry_slab",
                BigInteger.valueOf(2),
                "Cherry Logs",
                "cherry_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "pale_oak_planks",
                "PALE OAK",
                "Pale Oak Planks",
                "Pale Oak Slabs",
                "pale_oak_slab",
                BigInteger.valueOf(2),
                "Pale Oak Logs",
                "pale_oak_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "crimson_planks",
                "CRIMSON",
                "Crimson Planks",
                "Crimson Slabs",
                "crimson_slab",
                BigInteger.valueOf(2),
                "Crimson Logs",
                "crimson_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "warped_planks",
                "WARPED",
                "Warped Planks",
                "Warped Slabs",
                "warped_slab",
                BigInteger.valueOf(2),
                "Warped Logs",
                "warped_log",
                BigInteger.valueOf(4)
        ));
        families.add(new Family(
                "mangrove_planks",
                "MANGROVE",
                "Mangrove Planks",
                "Mangrove Slabs",
                "mangrove_slab",
                BigInteger.valueOf(2),
                "Mangrove Logs",
                "mangrove_log",
                BigInteger.valueOf(4)
        ));
        FAMILIES = Collections.unmodifiableList(families);
    }

    private FamilyList() {
    }

    public static List<Family> getFamilies() {
        return FAMILIES;
    }

    public static final class Family {
        private final String id;
        private final String displayName;
        private final String baseLabel;
        private final String smallLabel;
        private final String smallId;
        private final BigInteger smallRatio;
        private final String largeLabel;
        private final String largeId;
        private final BigInteger largeRatio;

        public Family(String id,
                      String displayName,
                      String baseLabel,
                      String smallLabel,
                      String smallId,
                      BigInteger smallRatio,
                      String largeLabel,
                      String largeId,
                      BigInteger largeRatio) {
            this.id = id;
            this.displayName = displayName;
            this.baseLabel = baseLabel;
            this.smallLabel = smallLabel;
            this.smallId = smallId;
            this.smallRatio = normalizeRatio(smallRatio);
            this.largeLabel = largeLabel;
            this.largeId = largeId;
            this.largeRatio = normalizeRatio(largeRatio);
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getBaseLabel() {
            return baseLabel;
        }

        public String getSmallLabel() {
            return smallLabel;
        }

        public String getSmallId() {
            return smallId;
        }

        public String getBaseId() {
            return id;
        }

        public String getLargeLabel() {
            return largeLabel;
        }

        public String getLargeId() {
            return largeId;
        }

        public BigInteger getSmallRatio() {
            return smallRatio;
        }

        public BigInteger getLargeRatio() {
            return largeRatio;
        }

        public boolean hasSmall() {
            return smallId != null && !smallId.isEmpty() && smallRatio != null && smallRatio.signum() > 0;
        }

        public boolean hasLarge() {
            return largeId != null && !largeId.isEmpty() && largeRatio != null && largeRatio.signum() > 0;
        }

        private static BigInteger normalizeRatio(BigInteger value) {
            if (value == null) {
                return null;
            }
            return value.abs();
        }
    }
}
