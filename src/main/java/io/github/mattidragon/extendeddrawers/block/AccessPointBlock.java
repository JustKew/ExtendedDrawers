package io.github.mattidragon.extendeddrawers.block;

import com.kneelawk.graphlib.graph.BlockNode;
import io.github.mattidragon.extendeddrawers.block.base.DrawerInteractionHandler;
import io.github.mattidragon.extendeddrawers.block.base.NetworkBlock;
import io.github.mattidragon.extendeddrawers.drawer.DrawerSlot;
import io.github.mattidragon.extendeddrawers.network.node.AccessPointBlockNode;
import io.github.mattidragon.extendeddrawers.network.NetworkStorageCache;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.List;

import static io.github.mattidragon.extendeddrawers.misc.DrawerInteractionStatusManager.getAndResetInsertStatus;

@SuppressWarnings({"UnstableApiUsage", "deprecation"}) // transfer api and mojank block method deprecation
public class AccessPointBlock extends NetworkBlock implements DrawerInteractionHandler {

    public AccessPointBlock(Settings settings) {
        super(settings);
    
        ItemStorage.SIDED.registerForBlocks((world, pos, state, entity, dir) -> world instanceof ServerWorld serverWorld ? NetworkStorageCache.get(serverWorld, pos) : Storage.empty(), this);
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!player.canModifyBlocks()) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.CONSUME_PARTIAL;

        var storage = NetworkStorageCache.get(serverWorld, pos);

        try (var t = Transaction.openOuter()) {
            int inserted;
    
            var playerStack = player.getStackInHand(hand);
            var isDoubleClick = getAndResetInsertStatus(player, pos, 0);
            
            if (isDoubleClick) {
                inserted = (int) StorageUtil.move(PlayerInventoryStorage.of(player), storage, itemVariant -> StorageUtil.findStoredResource(storage, (itemVariant1) -> itemVariant1.equals(itemVariant)) != null, Long.MAX_VALUE, t);
            } else {
                if (playerStack.isEmpty()) return ActionResult.PASS;
            
                inserted = (int) storage.insert(ItemVariant.of(playerStack), playerStack.getCount(), t);
                playerStack.decrement(inserted);
            }
            if (inserted == 0) return ActionResult.CONSUME_PARTIAL;
        
            t.commit();
            return ActionResult.SUCCESS;
        }
    }
    
    @Override
    public ActionResult toggleLock(BlockState state, World world, BlockPos pos, Vec3d hitPos, Direction side) {
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
        var storages = NetworkStorageCache.get(serverWorld, pos).parts;
        var currentState = storages.stream()
                .map(DrawerSlot::isLocked)
                .reduce(0, (count, value) -> count + (value ? 1 : -1), Integer::sum) > 0;
        storages.forEach(storage -> storage.setLocked(!currentState));
    
        return storages.size() == 0 ? ActionResult.PASS : ActionResult.SUCCESS;
    }

    @Override
    public Collection<BlockNode> createNodes() {
        return List.of(new AccessPointBlockNode());
    }
}
