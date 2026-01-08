package com.gtceuterminal.common.material;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MaterialAvailability {
    private final Item item;
    private final int required;
    private int inInventory;
    private int inNearbyChests;
    private long inMENetwork;

    public MaterialAvailability(Item item, int required) {
        this.item = item;
        this.required = required;
        this.inInventory = 0;
        this.inNearbyChests = 0;
        this.inMENetwork = 0;
    }

    public Item getItem() {
        return item;
    }

    public int getRequired() {
        return required;
    }

    public int getInInventory() {
        return inInventory;
    }

    public void setInInventory(int amount) {
        this.inInventory = amount;
    }

    public int getInNearbyChests() {
        return inNearbyChests;
    }

    public void setInNearbyChests(int amount) {
        this.inNearbyChests = amount;
    }

    public long getInMENetwork() {
        return inMENetwork;
    }

    public void setInMENetwork(long amount) {
        this.inMENetwork = amount;
    }

    public long getTotalAvailable() {
        return inInventory + inNearbyChests + inMENetwork;
    }

    public boolean hasEnough() {
        return getTotalAvailable() >= required;
    }

    public int getMissing() {
        return Math.max(0, required - (int)getTotalAvailable());
    }

    public int getAvailabilityPercent() {
        if (required == 0) return 100;
        return Math.min(100, (int)((getTotalAvailable() * 100) / required));
    }

    public int getColor() {
        int percent = getAvailabilityPercent();
        if (percent >= 100) {
            return 0x00FF00; // Green
        } else if (percent >= 50) {
            return 0xFFFF00; // Yellow
        } else if (percent > 0) {
            return 0xFF8800; // Orange
        } else {
            return 0xFF0000; // Red
        }
    }

    public String getDisplayString() {
        return String.format("%s x%d [Inv: %d] [Chests: %d]%s",
                item.getDescription().getString(),
                required,
                inInventory,
                inNearbyChests,
                inMENetwork > 0 ? " [ME: " + inMENetwork + "]" : ""
        );
    }

    public String getItemName() {
        return item.getDescription().getString();
    }

    public long getAvailable() {
        return getTotalAvailable();
    }

    public String getShortString() {
        if (hasEnough()) {
            return "✓ " + item.getDescription().getString();
        } else {
            return "✗ " + item.getDescription().getString() + " (need " + getMissing() + " more)";
        }
    }
}
