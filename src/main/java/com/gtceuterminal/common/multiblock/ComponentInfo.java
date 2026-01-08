package com.gtceuterminal.common.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ComponentInfo {
    private final ComponentType type;
    private final int tier;
    private final BlockPos position;
    private final BlockState state;
    private final String blockName;

    public ComponentInfo(ComponentType type, int tier, BlockPos position, BlockState state) {
        this.type = type;
        this.tier = tier;
        this.position = position;
        this.state = state;
        // Use registry ID for more specific identification
        this.blockName = state.getBlock().builtInRegistryHolder().key().location().getPath();
    }

    public ComponentType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public BlockPos getPosition() {
        return position;
    }

    public BlockState getState() {
        return state;
    }

    public String getBlockName() {
        return blockName;
    }

    public String getTierName() {
        if (tier < 0 || tier >= GTValues.VN.length) {
            return "Unknown";
        }
        return GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
    }

    public List<Integer> getPossibleUpgradeTiers() {
        List<Integer> tiers = new ArrayList<>();

        for (int i = tier + 1; i < GTValues.VN.length; i++) {
            tiers.add(i);
        }

        return tiers;
    }

    public boolean canUpgradeTo(int targetTier) {
        return targetTier > tier && targetTier < GTValues.VN.length;
    }


     // Get display name for GUI, Formats registry ID to readable name
    public String getDisplayName() {
        if (blockName.contains("rtm_alloy")) {
            return blockName.replace("rtm_alloy", "RTM Alloy")
                           .replace("_coil_block", " Coil Block")
                           .replace("_", " ");
        }
        if (blockName.contains("hss_g")) {
            return blockName.replace("hss_g", "HSS-G")
                           .replace("_coil_block", " Coil Block")
                           .replace("_", " ");
        }
        
        String[] parts = blockName.split("_");
        StringBuilder display = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (display.length() > 0) {
                display.append(" ");
            }
            
            if (!part.isEmpty()) {
                // Check if this part is a tier name
                if (isTierName(part)) {
                    display.append(part.toUpperCase(java.util.Locale.ROOT));
                } else {
                    display.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        display.append(part.substring(1));
                    }
                }
            }
        }

        return display.toString();
    }

    private boolean isTierName(String s) {
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("ulv") || lower.equals("lv") || lower.equals("mv") || 
               lower.equals("hv") || lower.equals("ev") || lower.equals("iv") || 
               lower.equals("luv") || lower.equals("zpm") || lower.equals("uv") || 
               lower.equals("uhv") || lower.equals("uev") || lower.equals("uiv") || 
               lower.equals("uxv") || lower.equals("opv") || lower.equals("max");
    }

    @Override
    public String toString() {
        return "ComponentInfo{" +
                "type=" + type +
                ", tier=" + getTierName() +
                ", position=" + position +
                '}';
    }
}