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
                "Iron"
        ));
        families.add(new OreFamily(
                "gold",
                "gold_nugget",
                "gold_ingot",
                "gold_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "Gold"
        ));
        families.add(new OreFamily(
                "copper",
                "copper_nugget",
                "copper_ingot",
                "copper_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "Copper"
        ));
        families.add(new OreFamily(
                "diamond",
                null,
                "diamond",
                "diamond_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "Diamond"
        ));
        families.add(new OreFamily(
                "lapis",
                null,
                "lapis_lazuli",
                "lapis_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "Lapis"
        ));
        families.add(new OreFamily(
                "tin",
                null,
                "tin_ingot",
                "tin_block",
                BigInteger.valueOf(9),
                BigInteger.valueOf(9),
                "Tin"
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
