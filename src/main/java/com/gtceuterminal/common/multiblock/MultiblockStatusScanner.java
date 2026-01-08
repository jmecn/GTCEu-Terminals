package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;

public class MultiblockStatusScanner {

    public static MultiblockStatus getStatus(IMultiController controller) {
        try {
            // Check if formed
            if (!controller.isFormed()) {
                return MultiblockStatus.UNFORMED;
            }

            return MultiblockStatus.IDLE;

        } catch (Exception e) {
            return MultiblockStatus.UNFORMED;
        }
    }

    public static String getStatusText(IMultiController controller) {
        MultiblockStatus status = getStatus(controller);

        try {
            return switch (status) {
                case ACTIVE -> {
                    yield "Active • Running recipe";
                }
                case IDLE -> "Idle • Waiting for items";
                case NEEDS_MAINTENANCE -> "NEEDS MAINTENANCE";
                case NO_POWER -> "No Power • Insufficient energy";
                case DISABLED -> "Disabled";
                case UNFORMED -> "Structure Broken";
                case OUTPUT_FULL -> "Output Full • Clear outputs";
            };
        } catch (Exception e) {
            return status.getDisplayName();
        }
    }

    public static boolean needsMaintenance(IMultiController controller) {
        try {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}