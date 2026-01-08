package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class MultiblockInfo {
    private final IMultiController controller;
    private final String name;
    private final BlockPos controllerPos;
    private final int tier;
    private final double distanceFromPlayer;
    private final boolean isFormed;
    private final List<ComponentInfo> components;
    private MultiblockStatus status;

    public MultiblockInfo(
            IMultiController controller,
            String name,
            BlockPos controllerPos,
            int tier,
            double distanceFromPlayer,
            boolean isFormed
    ) {
        this.controller = controller;
        this.name = name;
        this.controllerPos = controllerPos;
        this.tier = tier;
        this.distanceFromPlayer = distanceFromPlayer;
        this.isFormed = isFormed;
        this.components = new ArrayList<>();
        this.status = MultiblockStatus.IDLE;
    }

    public IMultiController getController() {
        return controller;
    }

    public String getName() {
        return name;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public int getTier() {
        return tier;
    }

    public double getDistanceFromPlayer() {
        return distanceFromPlayer;
    }

    public boolean isFormed() {
        return isFormed;
    }

    public List<ComponentInfo> getComponents() {
        return components;
    }

    public List<ComponentGroup> getGroupedComponents() {
        java.util.Map<String, ComponentGroup> groups = new java.util.HashMap<>();

        for (ComponentInfo comp : components) {
            String blockName = comp.getBlockName();
            String key = ComponentGroup.getGroupKey(comp.getType(), comp.getTier(), blockName);

            ComponentGroup group = groups.get(key);
            if (group == null) {
                group = new ComponentGroup(comp.getType(), comp.getTier(), blockName);
                groups.put(key, group);
            }

            group.addComponent(comp);
        }

        // Convert to list and sort by type name, then block name, then tier
        List<ComponentGroup> result = new ArrayList<>(groups.values());
        result.sort((a, b) -> {
            // Sort by type first
            int typeCompare = a.getType().getDisplayName().compareTo(b.getType().getDisplayName());
            if (typeCompare != 0) return typeCompare;
            // Then by block name
            int nameCompare = a.getBlockName().compareTo(b.getBlockName());
            if (nameCompare != 0) return nameCompare;
            // Then by tier
            return Integer.compare(a.getTier(), b.getTier());
        });

        return result;
    }

    public void addComponent(ComponentInfo component) {
        components.add(component);
    }

    public MultiblockStatus getStatus() {
        return status;
    }

    public void setStatus(MultiblockStatus status) {
        this.status = status;
    }

    public String getTierName() {
        if (tier < 0 || tier >= com.gregtechceu.gtceu.api.GTValues.VN.length) {
            return "Unknown";
        }
        return com.gregtechceu.gtceu.api.GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
    }

    public String getDistanceString() {
        return String.format("%.0fm", distanceFromPlayer);
    }

    public List<ComponentInfo> getComponentsByType(ComponentType type) {
        return components.stream()
                .filter(c -> c.getType() == type)
                .toList();
    }

    public List<ComponentInfo> getUpgradeableComponents() {
        return components.stream()
                .filter(c -> c.getType().isUpgradeable())
                .toList();
    }

    public int countComponentsOfType(ComponentType type) {
        return (int) components.stream()
                .filter(c -> c.getType() == type)
                .count();
    }

    @Override
    public String toString() {
        return "MultiblockInfo{" +
                "name='" + name + '\'' +
                ", tier=" + getTierName() +
                ", distance=" + getDistanceString() +
                ", formed=" + isFormed +
                ", components=" + components.size() +
                ", status=" + status +
                '}';
    }
}