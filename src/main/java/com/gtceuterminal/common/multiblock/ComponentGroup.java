package com.gtceuterminal.common.multiblock;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ComponentGroup {
    private final ComponentType type;
    private final int tier;
    private final String blockName;
    private final List<ComponentInfo> components;

    public ComponentGroup(ComponentType type, int tier, String blockName) {
        this.type = type;
        this.tier = tier;
        this.blockName = blockName;
        this.components = new ArrayList<>();
    }

    public void addComponent(ComponentInfo component) {
        components.add(component);
    }

    public ComponentType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public String getBlockName() {
        return blockName;
    }

    public int getCount() {
        return components.size();
    }

    public List<ComponentInfo> getComponents() {
        return components;
    }

    public ComponentInfo getRepresentative() {
        return components.isEmpty() ? null : components.get(0);
    }

    public String getTierName() {
        ComponentInfo rep = getRepresentative();
        return rep != null ? rep.getTierName() : "Unknown";
    }

    public List<BlockPos> getPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (ComponentInfo comp : components) {
            positions.add(comp.getPosition());
        }
        return positions;
    }

    public static String getGroupKey(ComponentType type, int tier, String blockName) {
        return type.name() + "_" + tier + "_" + blockName;
    }

    public String getGroupKey() {
        return getGroupKey(type, tier, blockName);
    }
}