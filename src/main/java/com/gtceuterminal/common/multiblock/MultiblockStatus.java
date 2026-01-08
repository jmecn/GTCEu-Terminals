package com.gtceuterminal.common.multiblock;

public enum MultiblockStatus {

    ACTIVE,

    IDLE,

    NEEDS_MAINTENANCE,

    NO_POWER,

    DISABLED,

    UNFORMED,

    OUTPUT_FULL;

    public int getColor() {
        return switch (this) {
            case ACTIVE -> 0x00FF00;            // Green
            case IDLE -> 0xFFFF00;              // Yellow
            case NEEDS_MAINTENANCE -> 0xFF0000; // Red
            case NO_POWER -> 0xFF8800;          // Orange
            case DISABLED -> 0xFFFFFF;          // White
            case UNFORMED -> 0xFF0000;          // Red
            case OUTPUT_FULL -> 0xFF00FF;       // Magenta
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case ACTIVE -> "Active";
            case IDLE -> "Idle";
            case NEEDS_MAINTENANCE -> "Needs Maintenance";
            case NO_POWER -> "No Power";
            case DISABLED -> "Disabled";
            case UNFORMED -> "Unformed";
            case OUTPUT_FULL -> "Output Full";
        };
    }
}