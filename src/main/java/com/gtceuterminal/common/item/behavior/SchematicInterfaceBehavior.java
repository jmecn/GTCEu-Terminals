package com.gtceuterminal.common.item.behavior;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.SchematicInterfaceScreen;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SchematicInterfaceBehavior {

    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();
        Direction clickedFace = context.getClickedFace();

        if (player.isShiftKeyDown()) {
            MetaMachine machine = MetaMachine.getMachine(level, blockPos);
            if (machine instanceof IMultiController controller && controller.isFormed()) {
                if (!level.isClientSide) {
                    copyMultiblock(controller, itemStack, player, level, blockPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("Not a formed multiblock controller!"), true);
                }
                return InteractionResult.FAIL;
            }
        } else {
            if (!level.isClientSide) {
                pasteMultiblockAtLookPosition(itemStack, player, level);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Item item, @NotNull Level level,
                                                           @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        if (player.isShiftKeyDown() && level.isClientSide) {
            // Open GUI
            openSchematicGUI(itemStack, player);
            return InteractionResultHolder.success(itemStack);
        } else if (!player.isShiftKeyDown() && !level.isClientSide) {
            pasteMultiblockAtLookPosition(itemStack, player, level);
            return InteractionResultHolder.success(itemStack);
        }

        return InteractionResultHolder.pass(itemStack);
    }

    private void copyMultiblock(IMultiController controller, ItemStack stack, Player player, Level level, BlockPos controllerPos) {
        try {
            Set<BlockPos> positions = scanMultiblockArea(controller, level);

            if (positions.isEmpty()) {
                player.displayClientMessage(Component.literal("Failed to scan multiblock!"), true);
                return;
            }

            Map<BlockPos, BlockState> blocks = new HashMap<>();

            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;

                String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
                if (!namespace.equals("gtceu")) continue;

                BlockPos relativePos = pos.subtract(controllerPos);
                blocks.put(relativePos, state);
            }

            Direction originalFacing = Direction.NORTH;
            BlockState controllerState = level.getBlockState(controllerPos);

            if (controllerState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                originalFacing = controllerState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
            }
            else if (controllerState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                Direction facing = controllerState.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis() != Direction.Axis.Y) {
                    originalFacing = facing;
                }
            }

            GTCEUTerminalMod.LOGGER.info("Copied multiblock with facing: {}", originalFacing);

            String multiblockType = controller.getClass().getSimpleName();
            SchematicData clipboard = new SchematicData("Clipboard", multiblockType, blocks, originalFacing.getName());

            CompoundTag stackTag = stack.getOrCreateTag();
            CompoundTag clipboardTag = clipboard.toNBT();
            stackTag.put("Clipboard", clipboardTag);

            player.displayClientMessage(
                    Component.literal("§aCopied " + blocks.size() + " blocks (View: " + originalFacing.getName() + ")"),
                    true
            );

            level.playSound(null, controllerPos, SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.BLOCKS, 1.0f, 1.5f);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error copying multiblock", e);
            player.displayClientMessage(Component.literal("§cError copying multiblock!"), true);
        }
    }

    private void pasteMultiblockAtLookPosition(ItemStack stack, Player player, Level level) {
        try {
            CompoundTag stackTag = stack.getTag();
            if (stackTag == null || !stackTag.contains("Clipboard")) {
                player.displayClientMessage(Component.literal("No schematic in clipboard! Copy a multiblock first."), true);
                return;
            }

            CompoundTag clipboardTag = stackTag.getCompound("Clipboard");
            SchematicData clipboard = SchematicData.fromNBT(clipboardTag, level.registryAccess());

            Direction originalFacing = Direction.SOUTH;
            try {
                String facingStr = clipboard.getOriginalFacing();
                if (facingStr != null && !facingStr.isEmpty()) {
                    originalFacing = Direction.byName(facingStr);
                }
            } catch (Exception e) {
            }

            GTCEUTerminalMod.LOGGER.info("schematic originalFacing: {}", originalFacing);

            // Calculate optimal distance
            double distance = calculateOptimalDistance(clipboard);

            // Get the position player is looking at
            BlockPos pasteOrigin = getTargetPlacementPosition(player, level, distance);
            if (pasteOrigin == null) {
                player.displayClientMessage(Component.literal("§eCouldn't find placement position!"), true);
                return;
            }

            Direction playerFacing = getPlayerHorizontalFacing(player);

            Direction targetFacing = playerFacing.getOpposite();

            GTCEUTerminalMod.LOGGER.info("Player facing: {}, Rotating multiblock from {} to {} (front faces player)",
                    playerFacing, originalFacing, targetFacing);

            int rotationSteps = getRotationSteps(originalFacing, targetFacing);

            GTCEUTerminalMod.LOGGER.info("Rotation steps: {}", rotationSteps);

            int placed = 0;
            int failed = 0;
            int fromInventory = 0;
            int fromME = 0;

            for (var entry : clipboard.getBlocks().entrySet()) {
                BlockPos relativePos = entry.getKey();
                BlockState state = entry.getValue();

                BlockPos rotatedPos = rotatePositionSteps(relativePos, rotationSteps);
                BlockPos targetPos = pasteOrigin.offset(rotatedPos);

                BlockState rotatedState = rotateBlockStateSteps(state, rotationSteps);

                if (!level.isInWorldBounds(targetPos)) {
                    failed++;
                    continue;
                }

                boolean hadInInventory = hasBlockInInventory(player, rotatedState);

                if (tryPlaceBlock(player, level, targetPos, rotatedState)) {
                    placed++;
                    if (hadInInventory) {
                        fromInventory++;
                    } else {
                        fromME++;
                    }
                } else {
                    failed++;
                }
            }

            String message = "§aPasted " + placed + " blocks";
            if (fromInventory > 0 || fromME > 0) {
                message += " §7(Inv: " + fromInventory;
                if (fromME > 0) {
                    message += ", ME: " + fromME;
                }
                message += ")";
            }
            if (failed > 0) {
                message += " §e(" + failed + " failed)";
            }

            player.displayClientMessage(Component.literal(message), true);

            level.playSound(null, pasteOrigin, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.8f, 1.2f);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error pasting multiblock", e);
            player.displayClientMessage(Component.literal("§cError pasting multiblock!"), true);
        }
    }

    private int getRotationSteps(Direction from, Direction to) {
        return (to.get2DDataValue() - from.get2DDataValue() + 4) % 4;
    }

    private BlockPos rotatePositionSteps(BlockPos pos, int steps) {
        BlockPos result = pos;
        for (int i = 0; i < steps; i++) {
            result = new BlockPos(-result.getZ(), result.getY(), result.getX());
        }
        return result;
    }

    private BlockState rotateBlockStateSteps(BlockState state, int steps) {
        BlockState result = state;
        for (int i = 0; i < steps; i++) {
            result = rotateBlockState90(result);
        }
        return result;
    }

    private BlockState rotateBlockState90(BlockState state) {
        try {
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise());
            }

            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, facing.getClockWise());
                }
            }
        } catch (Exception e) {
        }

        return state;
    }

    private double calculateOptimalDistance(SchematicData schematic) {
        BlockPos size = schematic.getSize();
        int maxDimension = Math.max(size.getX(), Math.max(size.getY(), size.getZ()));

        double distance = 4.0 + (maxDimension / 2.0);
        return Math.min(15.0, Math.max(4.0, distance));
    }

    private BlockPos getTargetPlacementPosition(Player player, Level level, double distance) {
        double raycastDistance = Math.max(10.0, distance + 5.0);
        var hitResult = player.pick(raycastDistance, 0.0f, false);
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            var blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }

        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 lookVec = player.getLookAngle();
        net.minecraft.world.phys.Vec3 targetVec = eyePos.add(lookVec.scale(distance));

        return new BlockPos((int)Math.floor(targetVec.x), (int)Math.floor(targetVec.y), (int)Math.floor(targetVec.z));
    }

    private Direction getPlayerHorizontalFacing(Player player) {
        float yaw = (player.getYRot() % 360 + 360) % 360;

        if (yaw >= 315 || yaw < 45) {
            return Direction.SOUTH;
        } else if (yaw >= 45 && yaw < 135) {
            return Direction.WEST;
        } else if (yaw >= 135 && yaw < 225) {
            return Direction.NORTH;
        } else {
            return Direction.EAST;
        }
    }

    private BlockPos rotatePosition(BlockPos pos, Direction facing) {
        return switch (facing) {
            case SOUTH -> pos;
            case WEST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case NORTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case EAST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }

    private BlockState rotateBlockState(BlockState state, Direction playerFacing) {
        try {
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                var rotated = rotateDirection(facing, playerFacing);
                return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, rotated);
            }

            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                var facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                if (facing.getAxis().isHorizontal()) {
                    var rotated = rotateDirection(facing, playerFacing);
                    return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, rotated);
                }
            }
        } catch (Exception e) {
        }

        return state;
    }

    private Direction rotateDirection(Direction original, Direction playerFacing) {
        int rotations = (playerFacing.get2DDataValue() - Direction.SOUTH.get2DDataValue() + 4) % 4;

        Direction result = original;
        for (int i = 0; i < rotations; i++) {
            result = result.getClockWise();
        }

        return result;
    }

    private void pasteMultiblockWithOrientation(ItemStack stack, Player player, Level level, BlockPos clickPos, Direction face) {
        try {
            CompoundTag stackTag = stack.getTag();
            if (stackTag == null || !stackTag.contains("Clipboard")) {
                player.displayClientMessage(Component.literal("No schematic in clipboard! Copy a multiblock first."), true);
                return;
            }

            SchematicData clipboard = SchematicData.fromNBT(
                    stackTag.getCompound("Clipboard"),
                    level.registryAccess()
            );

            BlockPos pasteOrigin = calculatePasteOrigin(clickPos, face, player);

            int placed = 0;
            int failed = 0;

            for (var entry : clipboard.getBlocks().entrySet()) {
                BlockPos relativePos = entry.getKey();
                BlockState state = entry.getValue();

                BlockPos adjustedPos = adjustPositionForFace(relativePos, face);
                BlockPos targetPos = pasteOrigin.offset(adjustedPos);

                if (!level.isInWorldBounds(targetPos)) {
                    failed++;
                    continue;
                }

                if (level.setBlock(targetPos, state, 3)) {
                    placed++;
                } else {
                    failed++;
                }
            }

            player.displayClientMessage(
                    Component.literal("§aPasted " + placed + " blocks" + (failed > 0 ? " §e(" + failed + " failed)" : "!")),
                    true
            );

            level.playSound(null, pasteOrigin, SoundEvents.ANVIL_USE,
                    SoundSource.BLOCKS, 0.8f, 1.2f);

        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error pasting multiblock", e);
            player.displayClientMessage(Component.literal("§cError pasting multiblock!"), true);
        }
    }

    private BlockPos calculatePasteOrigin(BlockPos clickPos, Direction face, Player player) {
        BlockPos origin = clickPos.relative(face);

        if (face == Direction.UP || face == Direction.DOWN) {
            Direction playerFacing = player.getDirection();
            return origin;
        }

        return origin;
    }

    private BlockPos adjustPositionForFace(BlockPos relativePos, Direction face) {
        if (face == Direction.UP) {
            return relativePos;
        } else if (face == Direction.DOWN) {
            return new BlockPos(relativePos.getX(), -relativePos.getY(), relativePos.getZ());
        }

        return switch (face) {
            case NORTH -> new BlockPos(relativePos.getX(), relativePos.getY(), relativePos.getZ());
            case SOUTH -> new BlockPos(-relativePos.getX(), relativePos.getY(), -relativePos.getZ());
            case EAST -> new BlockPos(-relativePos.getZ(), relativePos.getY(), relativePos.getX());
            case WEST -> new BlockPos(relativePos.getZ(), relativePos.getY(), -relativePos.getX());
            default -> relativePos;
        };
    }

    private void openSchematicGUI(ItemStack stack, Player player) {
        List<SchematicData> schematics = loadSchematics(stack, player.level());

        SchematicInterfaceScreen screen = new SchematicInterfaceScreen(
                stack,
                schematics,
                action -> handleGUIAction(stack, player, action)
        );

        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    private void handleGUIAction(ItemStack stack, Player player, SchematicInterfaceScreen.SchematicAction action) {
    }

    private List<SchematicData> loadSchematics(ItemStack stack, Level level) {
        List<SchematicData> schematics = new ArrayList<>();

        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("SavedSchematics")) {
            return schematics;
        }

        ListTag savedList = stackTag.getList("SavedSchematics", 10);
        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                schematics.add(SchematicData.fromNBT(schematicTag, level.registryAccess()));
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error loading schematic {}: {}", i, e.getMessage());
            }
        }

        return schematics;
    }

    private Set<BlockPos> scanMultiblockArea(IMultiController controller, Level level) {
        Set<BlockPos> positions = new HashSet<>();

        try {
            Collection<BlockPos> cachePos = controller.getMultiblockState().getCache();
            if (cachePos != null && !cachePos.isEmpty()) {
                positions.addAll(cachePos);
                return positions;
            }
        } catch (Exception e) {
        }

        BlockPos controllerPos = controller.self().getPos();
        var parts = controller.getParts();

        if (parts.isEmpty()) {
            return positions;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (var part : parts) {
            BlockPos pos = part.self().getPos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        minX -= 3; minY -= 3; minZ -= 3;
        maxX += 3; maxY += 3; maxZ += 3;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir()) {
                        String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
                        if (namespace.equals("gtceu")) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean extractBlockFromME(Player player, Level level, BlockState state) {
        try {
            net.minecraft.world.item.Item item = state.getBlock().asItem();
            if (item == net.minecraft.world.item.Items.AIR) {
                return false;
            }

            // Try to extract from ME Network (it doesnt work, next update maybe)
            long extracted = com.gtceuterminal.common.ae2.MENetworkExtractor
                    .extractFromNearbyMENetwork(player, level, item, 1, 16);

            if (extracted > 0) {
                GTCEUTerminalMod.LOGGER.debug("Extracted {} from ME Network", item);
                return true;
            }

            return false;
        } catch (Exception e) {
            GTCEUTerminalMod.LOGGER.error("Error extracting from ME", e);
            return false;
        }
    }

    private boolean isBlockInME(Player player, Level level, BlockState state) {
        try {
            net.minecraft.world.item.Item item = state.getBlock().asItem();
            if (item == net.minecraft.world.item.Items.AIR) {
                return false;
            }

            long count = com.gtceuterminal.common.ae2.MENetworkScanner
                    .getTotalInMENetworks(player, level, item, 16);

            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasBlockInInventory(Player player, BlockState state) {
        net.minecraft.world.item.Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return false;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return true;
            }
        }

        return false;
    }

    boolean removeBlockFromInventory(Player player, BlockState state) {
        net.minecraft.world.item.Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            return false;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                stack.shrink(1);
                return true;
            }
        }

        return false;
    }

    private boolean tryPlaceBlock(Player player, Level level, BlockPos pos, BlockState state) {
        if (player.isCreative()) {
            return level.setBlock(pos, state, 3);
        }

        if (hasBlockInInventory(player, state)) {
            if (level.setBlock(pos, state, 3)) {
                removeBlockFromInventory(player, state);
                return true;
            }
        }

        // Try ME Network if inventory failed (it doesnt work, next update maybe)
        if (extractBlockFromME(player, level, state)) {

            if (level.setBlock(pos, state, 3)) {
                removeBlockFromInventory(player, state);
                return true;
            }
        }

        return false;
    }
}
