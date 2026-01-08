package com.gtceuterminal.common.material;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.*;


 // Calculates material requirements and checks availability

public class MaterialCalculator {

    private static final int CHEST_SCAN_RADIUS = 3;

    public static Map<Item, Integer> calculateUpgradeCost(ComponentInfo component, int targetTier) {
        Map<Item, Integer> materials = new HashMap<>();
        String blockName = component.getBlockName();

        return materials;
    }

    public static Map<Item, Integer> scanPlayerInventory(Player player) {
        Map<Item, Integer> inventory = new HashMap<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                inventory.merge(item, stack.getCount(), Integer::sum);
            }
        }

        return inventory;
    }

    public static Map<Item, Integer> scanNearbyChests(Level level, BlockPos center) {
        Map<Item, Integer> items = new HashMap<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-CHEST_SCAN_RADIUS, -CHEST_SCAN_RADIUS, -CHEST_SCAN_RADIUS),
                center.offset(CHEST_SCAN_RADIUS, CHEST_SCAN_RADIUS, CHEST_SCAN_RADIUS)
        )) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack stack = chest.getItem(i);
                    if (!stack.isEmpty()) {
                        Item item = stack.getItem();
                        items.merge(item, stack.getCount(), Integer::sum);
                    }
                }
            }
        }

        return items;
    }


      // Check material availability from all sources

    public static List<MaterialAvailability> checkMaterialsAvailability(
            Map<Item, Integer> required,
            Player player,
            Level level
    ) {
        GTCEUTerminalMod.LOGGER.info("=== Checking Materials Availability (Server: {}) ===", !level.isClientSide);

        List<MaterialAvailability> availability = new ArrayList<>();

        Map<Item, Integer> playerInv = scanPlayerInventory(player);
        Map<Item, Integer> chests = scanNearbyChests(level, player.blockPosition());

        for (Map.Entry<Item, Integer> entry : required.entrySet()) {
            MaterialAvailability mat = new MaterialAvailability(entry.getKey(), entry.getValue());

            mat.setInInventory(playerInv.getOrDefault(entry.getKey(), 0));
            mat.setInNearbyChests(chests.getOrDefault(entry.getKey(), 0));

            long inME = scanMENetworkForItem(player, level, entry.getKey());
            mat.setInMENetwork(inME);

            String itemName = entry.getKey().getDescription().getString();
            GTCEUTerminalMod.LOGGER.info("  {}: Required={}, InInv={}, InChests={}, InME={}",
                    itemName, entry.getValue(), mat.getInInventory(), mat.getInNearbyChests(), inME);

            availability.add(mat);
        }

        return availability;
    }

    public static Map<Item, Long> scanMENetwork(Player player, Level level) {
        Map<Item, Long> items = new HashMap<>();

        try {
            if (!com.gtceuterminal.common.ae2.MENetworkScanner.isAE2Available()) {
                return items;
            }

        } catch (Exception e) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.error("Error scanning ME network", e);
        }

        return items;
    }

    public static long scanMENetworkForItem(Player player, Level level, Item item) {
        try {
            if (!com.gtceuterminal.common.ae2.MENetworkScanner.isAE2Available()) {
                return 0;
            }

            return com.gtceuterminal.common.ae2.MENetworkScanner.getTotalInMENetworks(
                    player, level, item, 16
            );

        } catch (Exception e) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.error("Error scanning ME network for item", e);
            return 0;
        }
    }

    public static boolean hasEnoughMaterials(List<MaterialAvailability> materials) {
        return materials.stream().allMatch(MaterialAvailability::hasEnough);
    }

    public static Map<Item, Integer> getMissingMaterials(List<MaterialAvailability> materials) {
        Map<Item, Integer> missing = new HashMap<>();

        for (MaterialAvailability mat : materials) {
            if (!mat.hasEnough()) {
                missing.put(mat.getItem(), mat.getMissing());
            }
        }

        return missing;
    }

    public static boolean extractFromInventory(Player player, Item item, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                int toTake = Math.min(remaining, stack.getCount());
                stack.shrink(toTake);
                remaining -= toTake;

                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return remaining <= 0;
    }

    public static boolean extractFromChests(Level level, BlockPos center, Item item, int amount) {
        int remaining = amount;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-CHEST_SCAN_RADIUS, -CHEST_SCAN_RADIUS, -CHEST_SCAN_RADIUS),
                center.offset(CHEST_SCAN_RADIUS, CHEST_SCAN_RADIUS, CHEST_SCAN_RADIUS)
        )) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof ChestBlockEntity chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    ItemStack stack = chest.getItem(i);
                    if (stack.getItem() == item) {
                        int toTake = Math.min(remaining, stack.getCount());
                        stack.shrink(toTake);
                        remaining -= toTake;

                        if (remaining <= 0) {
                            chest.setChanged();
                            return true;
                        }
                    }
                }
            }
        }

        return remaining <= 0;
    }

    public static boolean extractMaterials(
            List<MaterialAvailability> materials,
            Player player,
            Level level,
            boolean useInventory,
            boolean useChests
    ) {
        GTCEUTerminalMod.LOGGER.info("=== Extracting Materials (Server: {}) ===", !level.isClientSide);

        for (MaterialAvailability mat : materials) {
            int remaining = mat.getRequired();
            String itemName = mat.getItemName();

            GTCEUTerminalMod.LOGGER.info("  Item: {}, Required: {}, InInv: {}, InME: {}",
                    itemName, remaining, mat.getInInventory(), mat.getInMENetwork());

            // Try inventory first
            if (useInventory && remaining > 0) {
                int available = Math.min(remaining, mat.getInInventory());
                if (available > 0 && extractFromInventory(player, mat.getItem(), available)) {
                    GTCEUTerminalMod.LOGGER.info("    Extracted {} from inventory", available);
                    remaining -= available;
                }
            }

            // Try chests (it doesnt work, next update maybe)
            if (useChests && remaining > 0) {
                int available = Math.min(remaining, mat.getInNearbyChests());
                if (available > 0 && extractFromChests(level, player.blockPosition(), mat.getItem(), available)) {
                    GTCEUTerminalMod.LOGGER.info("    Extracted {} from chests", available);
                    remaining -= available;
                }
            }

            // Try ME Network last (it doesnt work, next update maybe)
            if (remaining > 0 && mat.getInMENetwork() > 0) {
                long available = Math.min(remaining, mat.getInMENetwork());
                GTCEUTerminalMod.LOGGER.info("    Trying to extract {} from ME Network", available);

                if (available > 0) {
                    long extracted = com.gtceuterminal.common.ae2.MENetworkExtractor.extractFromNearbyMENetwork(
                            player, level, mat.getItem(), available, 16
                    );
                    GTCEUTerminalMod.LOGGER.info("    Actually extracted {} from ME", extracted);
                    remaining -= (int) extracted;
                }
            }

            // Check if enough materials
            if (remaining > 0) {
                GTCEUTerminalMod.LOGGER.warn("  FAILED: Still missing {} of {}", remaining, itemName);
                return false;
            }

            GTCEUTerminalMod.LOGGER.info("  SUCCESS: Got all required {}", itemName);
        }

        GTCEUTerminalMod.LOGGER.info("=== All Materials Extracted Successfully ===");
        return true;
    }
}
