package io.github.mattidragon.extendeddrawers.drawer;

import io.github.mattidragon.extendeddrawers.config.CommonConfig;
import io.github.mattidragon.extendeddrawers.item.UpgradeItem;
import io.github.mattidragon.extendeddrawers.misc.ItemUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public final class DrawerSlot extends SnapshotParticipant<DrawerSlot.Snapshot> implements SingleSlotStorage<ItemVariant>, Comparable<DrawerSlot> {
    // Fields are encapsulated to ensure updates on change
    private final BooleanConsumer onChange;
    private final double capacityMultiplier;
    private boolean itemChanged;
    private ItemVariant item = ItemVariant.blank();
    @Nullable
    private UpgradeItem upgrade = null;
    private long amount;
    private boolean locked;
    
    public DrawerSlot(BooleanConsumer onChange, double capacityMultiplier) {
        this.onChange = onChange;
        this.capacityMultiplier = capacityMultiplier;
    }
    
    public void setLocked(boolean locked) {
        this.locked = locked;
        itemChanged = true;
        if (!locked && amount == 0) item = ItemVariant.blank();
        update();
    }

    public void changeUpgrade(@Nullable UpgradeItem newUpgrade, World world, BlockPos pos, Direction side, @Nullable PlayerEntity player) {
        ItemUtils.offerOrDrop(world, pos, side, player, new ItemStack(upgrade));
        upgrade = newUpgrade;
        dumpExcess(world, pos, side, player);
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!resource.equals(item) && !item.isBlank()) return 0;
        
        updateSnapshots(transaction);
        var inserted = Math.min(getCapacity() - amount, maxAmount);
        amount += inserted;
        if (item.isBlank()) {
            item = resource;
            itemChanged = true;
        }
        return inserted;
    }
    
    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        if (!resource.equals(item)) return 0;
        
        updateSnapshots(transaction);
        var extracted = Math.min(amount, maxAmount);
        amount -= extracted;
        if (amount == 0 && !locked) {
            item = ItemVariant.blank();
            itemChanged = true;
        }
        return extracted;
    }
    
    @Override
    public boolean isResourceBlank() {
        return item.isBlank();
    }
    
    @Override
    public ItemVariant getResource() {
        return item;
    }
    
    @Override
    public long getAmount() {
        return amount;
    }
    
    @Override
    public long getCapacity() {
        var config = CommonConfig.HANDLE.get();
        var capacity = (long) (config.defaultCapacity() * this.capacityMultiplier);
        if (config.stackSizeAffectsCapacity())
            capacity /= 64.0 / item.getItem().getMaxCount();
        if (upgrade != null)
            capacity = upgrade.modifier.applyAsLong(capacity);
        return capacity;
    }
    
    @Override
    protected Snapshot createSnapshot() {
        return new Snapshot(new ResourceAmount<>(item, amount), itemChanged);
    }
    
    @Override
    protected void readSnapshot(Snapshot snapshot) {
        item = snapshot.contents.resource();
        amount = snapshot.contents.amount();
        itemChanged = snapshot.itemChanged;
    }
    
    @Override
    protected void onFinalCommit() {
        update();
    }
    
    public void update() {
        onChange.accept(itemChanged);
        itemChanged = false;
    }
    
    public void dumpExcess(World world, BlockPos pos, Direction side, @Nullable PlayerEntity player) {
        if (amount > getCapacity()) {
            ItemUtils.offerOrDropStacks(world, pos, side, player, item, amount - getCapacity());
            amount = getCapacity();
        }
        update();
    }
    
    @Override
    public int compareTo(@NotNull DrawerSlot other) {
        if (this.isResourceBlank() != other.isResourceBlank())
            return this.isResourceBlank() ? 1 : -1;
        if (this.locked != other.locked)
            return this.locked ? -1 : 1;
        
        return 0;
    }

    public void readNbt(NbtCompound nbt) {
        item = ItemVariant.fromNbt(nbt.getCompound("item"));
        amount = nbt.getLong("amount");
        locked = nbt.getBoolean("locked");
        upgrade = Registry.ITEM.get(Identifier.tryParse(nbt.getString("upgrade"))) instanceof UpgradeItem upgrade ? upgrade : null;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.put("item", item.toNbt());
        nbt.putLong("amount", amount);
        nbt.putBoolean("locked", locked);
        nbt.putString("upgrade", Registry.ITEM.getId(upgrade).toString());
    }

    public ItemVariant getItem() {
        return item;
    }

    public boolean isLocked() {
        return locked;
    }

    public @Nullable UpgradeItem getUpgrade() {
        return upgrade;
    }

    record Snapshot(ResourceAmount<ItemVariant> contents, boolean itemChanged) {}
}
