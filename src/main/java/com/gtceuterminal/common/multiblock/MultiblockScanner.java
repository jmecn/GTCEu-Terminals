package com.gtceuterminal.common.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.GTValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Collection;

public class MultiblockScanner {

    public static List<MultiblockInfo> scanNearbyMultiblocks(Player player, Level level, int radius) {
        List<MultiblockInfo> multiblocks = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();
        Vec3 playerVec = player.position();

        // Scan area around player
        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-radius, -radius, -radius),
                playerPos.offset(radius, radius, radius)
        )) {
            MetaMachine machine = MetaMachine.getMachine(level, pos);

            if (machine instanceof IMultiController controller) {
                // Calculate distance
                double distance = playerVec.distanceTo(Vec3.atCenterOf(pos));

                // Get multiblock info
                String name = getMultiblockName(controller);
                int tier = getMultiblockTier(controller);
                boolean isFormed = controller.isFormed();

                MultiblockInfo info = new MultiblockInfo(
                        controller,
                        name,
                        pos,
                        tier,
                        distance,
                        isFormed
                );

                // Scan components if formed
                if (isFormed) {
                    scanComponents(controller, info, level);
                }

                multiblocks.add(info);
            }
        }

        // Sort by distance
        multiblocks.sort((a, b) -> Double.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));

        return multiblocks;
    }

    private static void scanComponents(IMultiController controller, MultiblockInfo info, Level level) {
        Set<BlockPos> scannedPositions = new HashSet<>();
        BlockPos controllerPos = controller.self().getPos();

        try {
            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                for (var part : parts) {
                    BlockPos partPos = part.self().getPos();
                    if (scannedPositions.contains(partPos)) continue;
                    scannedPositions.add(partPos);

                    BlockState state = level.getBlockState(partPos);
                    ComponentInfo component = identifyComponent(state, partPos);

                    if (component != null) {
                        info.addComponent(component);
                    }
                }
            }

            Set<BlockPos> structurePositions = getMultiblockStructurePositions(controller, level);

            for (BlockPos pos : structurePositions) {
                if (scannedPositions.contains(pos)) continue;
                scannedPositions.add(pos);

                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;

                ComponentInfo component = identifyComponent(state, pos);

                if (component != null &&
                        (component.getType().isUpgradeable() || component.getType() == ComponentType.COIL)) {
                    info.addComponent(component);
                }
            }

            com.gtceuterminal.GTCEUTerminalMod.LOGGER.debug("Scanned {} total blocks for multiblock at {}",
                    scannedPositions.size(), controllerPos);

            // Log component summary
            var components = info.getComponents();
            long coilCount = components.stream().filter(c -> c.getType() == ComponentType.COIL).count();
            if (coilCount > 0) {
                com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Found {} coils in multiblock at {}",
                        coilCount, controllerPos);
            }

        } catch (Exception e) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.error("Error scanning components", e);
        }
    }

    private static Set<BlockPos> getMultiblockStructurePositions(IMultiController controller, Level level) {
        Set<BlockPos> result = new HashSet<>();

        if (controller == null) {
            return result;
        }

        BlockPos controllerPos = controller.self().getPos();

        try {
            MultiblockState state = controller.getMultiblockState();
            if (state != null) {
                Collection<BlockPos> cache = null;

                try {
                    cache = state.getCache();
                } catch (Exception ex) {
                    GTCEUTerminalMod.LOGGER.warn(
                            "MultiblockState cache is null or unreadable for controller at {}, falling back to BFS scan",
                            controllerPos
                    );
                }

                if (cache != null && !cache.isEmpty()) {
                    result.addAll(cache);
                    GTCEUTerminalMod.LOGGER.debug(
                            "Using MultiblockState cache for controller at {} ({} blocks)",
                            controllerPos, result.size()
                    );
                    return result;
                }
            }
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error(
                    "Error while reading multiblock structure positions for controller at " + controllerPos,
                    e
            );
        }

        int maxRadius = 16;
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(controllerPos);
        visited.add(controllerPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            if (!isWithinRadius(controllerPos, current, maxRadius)) {
                continue;
            }

            BlockState currentState = level.getBlockState(current);
            if (currentState.isAir()) {
                continue;
            }

            String namespace = currentState.getBlock()
                    .builtInRegistryHolder()
                    .key()
                    .location()
                    .getNamespace();

            if (!"gtceu".equals(namespace)) {
                continue;
            }

            result.add(current.immutable());

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && isWithinRadius(controllerPos, neighbor, maxRadius)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        GTCEUTerminalMod.LOGGER.info(
                "Fallback BFS found {} GTCEu blocks around controller at {}",
                result.size(), controllerPos
        );

        return result;
    }

    private static boolean isWithinRadius(BlockPos origin, BlockPos pos, int radius) {
        int dx = pos.getX() - origin.getX();
        int dy = pos.getY() - origin.getY();
        int dz = pos.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static BlockPos findNearestController(Level level, BlockPos currentController) {
        BlockPos nearest = null;
        double minDistance = Double.MAX_VALUE;
        int controllersFound = 0;

        // Scan area around current controller for other controllers
        int searchRadius = 30;

        for (BlockPos pos : BlockPos.betweenClosed(
                currentController.offset(-searchRadius, -searchRadius, -searchRadius),
                currentController.offset(searchRadius, searchRadius, searchRadius)
        )) {
            if (pos.equals(currentController)) continue;

            MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof IMultiController) {
                controllersFound++;
                double distance = Math.sqrt(
                        Math.pow(pos.getX() - currentController.getX(), 2) +
                                Math.pow(pos.getY() - currentController.getY(), 2) +
                                Math.pow(pos.getZ() - currentController.getZ(), 2)
                );

                com.gtceuterminal.GTCEUTerminalMod.LOGGER.debug(
                        "Found other controller at {} distance {} from {}",
                        pos, distance, currentController
                );

                if (distance < minDistance) {
                    minDistance = distance;

                    nearest = pos.immutable();
                }
            }
        }

        if (nearest != null) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.info(
                    "Nearest controller to {} is at {} (distance {}), found {} total controllers",
                    currentController, nearest, minDistance, controllersFound
            );
        } else {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.warn(
                    "No other controllers found within {} blocks of {} (scanned {} controllers total)",
                    searchRadius, currentController, controllersFound
            );
        }

        return nearest;
    }

    private static ComponentInfo identifyComponent(BlockState state, BlockPos pos) {
        String blockName = state.getBlock().getDescriptionId().toLowerCase();

        ComponentType type = identifyComponentType(blockName);
        int tier = identifyTier(blockName);

        // Debug logging for coils
        if (type == ComponentType.COIL) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Detected COIL: {} at {} with tier {}",
                    blockName, pos, tier);
        }

        return new ComponentInfo(type, tier, pos, state);
    }

    private static ComponentType identifyComponentType(String blockName) {
        // Check for energy FIRST
        if (blockName.contains("energy") && (blockName.contains("hatch") || blockName.contains("input"))) {
            return ComponentType.ENERGY_HATCH;
        }

        // Check for hatches
        if (blockName.contains("input_hatch")) {
            return ComponentType.INPUT_HATCH;
        } else if (blockName.contains("output_hatch")) {
            return ComponentType.OUTPUT_HATCH;
        } else if (blockName.contains("dual_hatch")) {
            return ComponentType.DUAL_HATCH;
        }

        // Check for buses
        else if (blockName.contains("input_bus")) {
            return ComponentType.INPUT_BUS;
        } else if (blockName.contains("output_bus")) {
            return ComponentType.OUTPUT_BUS;
        }

        else if (blockName.contains("muffler")) {
            return ComponentType.MUFFLER;
        }

        else if (blockName.contains("maintenance")) {
            return ComponentType.MAINTENANCE;
        }

        else if (blockName.contains("coil")) {
            return ComponentType.COIL;
        }

        else if (blockName.contains("casing")) {
            return ComponentType.CASING;
        }

        else if (blockName.contains("pipe")) {
            return ComponentType.PIPE;
        }

        return ComponentType.OTHER;
    }

    private static int identifyTier(String blockName) {
        // Special handling for coils
        if (blockName.contains("coil")) {
            return identifyCoilTier(blockName);
        }

        // Check for each tier name
        for (int i = 0; i < GTValues.VN.length; i++) {
            String tierName = GTValues.VN[i].toLowerCase();
            if (blockName.contains(tierName) || blockName.contains("." + tierName + ".")) {
                return i;
            }
        }

        // Alternative tier detection patterns
        if (blockName.contains("ulv")) return GTValues.ULV;
        if (blockName.contains("lv")) return GTValues.LV;
        if (blockName.contains("mv")) return GTValues.MV;
        if (blockName.contains("hv")) return GTValues.HV;
        if (blockName.contains("ev")) return GTValues.EV;
        if (blockName.contains("iv")) return GTValues.IV;
        if (blockName.contains("luv")) return GTValues.LuV;
        if (blockName.contains("zpm")) return GTValues.ZPM;
        if (blockName.contains("uv")) return GTValues.UV;
        if (blockName.contains("uhv")) return GTValues.UHV;
        if (blockName.contains("uev")) return GTValues.UEV;
        if (blockName.contains("uiv")) return GTValues.UIV;
        if (blockName.contains("uxv")) return GTValues.UXV;
        if (blockName.contains("opv")) return GTValues.OpV;
        if (blockName.contains("max")) return GTValues.MAX;

        return 0;
    }

    private static int identifyCoilTier(String blockName) {
        if (blockName.contains("cupronickel")) return 0;
        if (blockName.contains("kanthal")) return 1;
        if (blockName.contains("nichrome")) return 2;
        if (blockName.contains("rtm_alloy")) return 3;
        if (blockName.contains("hss_g")) return 4;
        if (blockName.contains("naquadah")) return 5;
        if (blockName.contains("trinium")) return 6;
        if (blockName.contains("tritanium")) return 7;
        return 0;
    }

    private static String getMultiblockName(IMultiController controller) {
        try {
            BlockState state = controller.self().getBlockState();
            String displayName = state.getBlock().getName().getString();

            if (!displayName.isEmpty() && !displayName.contains("block.gtceu")) {
                return displayName;
            }

            String className = controller.getClass().getSimpleName();

            String name = className
                    .replace("MetaTileEntity", "")
                    .replace("Machine", "")
                    .replace("Controller", "")
                    .replace("Workable", "")
                    .replace("Electric", "")
                    .replace("Multiblock", "")
                    .trim();

            name = name.replaceAll("([a-z])([A-Z])", "$1 $2");

            name = name.replaceAll("\\s+", " ").trim();

            return name.isEmpty() ? "Unknown Multiblock" : name;
        } catch (Exception e) {
            return "Unknown Multiblock";
        }
    }

    private static int getMultiblockTier(IMultiController controller) {
        return 1;
    }

    public static Set<BlockPos> getMultiblockBlocks(IMultiController controller) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos controllerPos = controller.self().getPos();
        Level level = controller.self().getLevel();

        try {
            var parts = controller.getParts();
            if (parts != null && !parts.isEmpty()) {
                for (var part : parts) {
                    if (part != null && part.self() != null) {
                        positions.add(part.self().getPos());
                    }
                }
                if (!positions.isEmpty()) {
                    com.gtceuterminal.GTCEUTerminalMod.LOGGER.debug("Got {} blocks from parts", positions.size());
                    return positions;
                }
            }

            for (int x = -16; x <= 16; x++) {
                for (int y = -16; y <= 16; y++) {
                    for (int z = -16; z <= 16; z++) {
                        BlockPos pos = controllerPos.offset(x, y, z);
                        BlockState state = level.getBlockState(pos);

                        if (state.isAir()) continue;

                        // Check if it's a GTCEu block
                        String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
                        if (namespace.equals("gtceu")) {
                            positions.add(pos.immutable());
                        }
                    }
                }
            }

            com.gtceuterminal.GTCEUTerminalMod.LOGGER.info("Area scan found {} GTCEu blocks around controller at {}",
                    positions.size(), controllerPos);

        } catch (Exception e) {
            com.gtceuterminal.GTCEUTerminalMod.LOGGER.error("Error scanning multiblock blocks at " + controllerPos, e);
        }

        return positions;
    }

    public static com.gtceuterminal.common.data.BlockReplacementData scanMultiblock(IMultiController controller) {
        com.gtceuterminal.common.data.BlockReplacementData data =
                new com.gtceuterminal.common.data.BlockReplacementData();

        Set<BlockPos> positions = getMultiblockBlocks(controller);

        for (BlockPos pos : positions) {
            try {
                BlockState state = controller.self().getLevel().getBlockState(pos);
                if (!state.isAir()) {
                    data.addBlock(state);
                }
            } catch (Exception e) {
            }
        }

        return data;
    }
}