package com.starhavensmpcore.market.items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OreFamilyList {
    private static final List<OreFamily> FAMILIES;

    static {
        List<OreFamily> families = new ArrayList<>();
        families.add(new OreFamily(
                "iron",
                "iron_nugget",
                "iron_ingot",
                "iron_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "IRON"
        ));
        families.add(new OreFamily(
                "gold",
                "gold_nugget",
                "gold_ingot",
                "gold_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "GOLD"
        ));
        families.add(new OreFamily(
                "copper",
                "copper_nugget",
                "copper_ingot",
                "copper_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "COPPER"
        ));
        families.add(new OreFamily(
                "diamond",
                null,
                "diamond",
                "diamond_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "DIAMOND"
        ));
        families.add(new OreFamily(
                "lapis",
                null,
                "lapis_lazuli",
                "lapis_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "LAPIS"
        ));
        families.add(new OreFamily(
                "coal",
                null,
                "coal",
                "coal_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "COAL"
        ));
        families.add(new OreFamily(
                "emerald",
                null,
                "emerald",
                "emerald_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "EMERALD"
        ));
        families.add(new OreFamily(
                "netherite",
                null,
                "netherite_ingot",
                "netherite_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "NETHERITE"
        ));
        families.add(new OreFamily(
                "resin",
                null,
                "resin_clump",
                "resin_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "RESIN"
        ));
        families.add(new OreFamily(
                "slime",
                null,
                "slime_ball",
                "slime_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "SLIME"
        ));
        families.add(new OreFamily(
                "redstone",
                null,
                "redstone",
                "redstone_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "REDSTONE"
        ));
        families.add(new OreFamily(
                "raw_iron",
                null,
                "raw_iron",
                "raw_iron_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "RAW IRON"
        ));
        families.add(new OreFamily(
                "raw_gold",
                null,
                "raw_gold",
                "raw_gold_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "RAW GOLD"
        ));
        families.add(new OreFamily(
                "raw_copper",
                null,
                "raw_copper",
                "raw_copper_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "RAW COPPER"
        ));
        families.add(new OreFamily(
                "tin",
                null,
                "tin_ingot",
                "tin_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "TIN"
        ));
        families.add(new OreFamily(
                "tin",
                null,
                "raw_tin",
                "raw_tin_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "RAW TIN"
        ));
        FAMILIES = Collections.unmodifiableList(families);
    }

    private OreFamilyList() {
    }

    public static List<OreFamily> getFamilies() {
        return FAMILIES;
    }

    public static final class OreFamily {
        private final String id;
        private final String nuggetId;
        private final String baseId;
        private final String blockId;
        private final BigInteger nuggetRatio;
        private final BigInteger blockRatio;
        private final String label;

        public OreFamily(String id, String nuggetId, String baseId, String blockId,
                         BigInteger nuggetRatio, BigInteger blockRatio, String label) {
            this.id = id;
            this.nuggetId = nuggetId;
            this.baseId = baseId;
            this.blockId = blockId;
            this.nuggetRatio = nuggetRatio;
            this.blockRatio = blockRatio;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public String getNuggetId() {
            return nuggetId;
        }

        public String getBaseId() {
            return baseId;
        }

        public String getBlockId() {
            return blockId;
        }

        public BigInteger getNuggetRatio() {
            return nuggetRatio;
        }

        public BigInteger getBlockRatio() {
            return blockRatio;
        }

        public String getLabel() {
            return label;
        }
    }
}
