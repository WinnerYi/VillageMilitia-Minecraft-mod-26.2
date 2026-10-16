package com.example.examplemod.menu;

import com.example.examplemod.entity.VillageMilitiaEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

public class MilitiaEquipmentMenu extends AbstractContainerMenu {
    public static final MenuType<MilitiaEquipmentMenu> TYPE = net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
        MilitiaEquipmentMenu::new
    );
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND,
        EquipmentSlot.OFFHAND
    };

    private final VillageMilitiaEntity militia;

    public MilitiaEquipmentMenu(int containerId, Inventory playerInventory, VillageMilitiaEntity militia) {
        super(TYPE, containerId);
        this.militia = militia;

        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.HEAD, 8, 8));
        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.CHEST, 8, 26));
        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.LEGS, 8, 44));
        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.FEET, 8, 62));
        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.MAINHAND, 77, 26));
        this.addSlot(new EquipmentSlotAdapter(militia, EquipmentSlot.OFFHAND, 77, 62));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                    8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    public MilitiaEquipmentMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory,
            (VillageMilitiaEntity) playerInventory.player.level().getEntity(data.readVarInt()));
    }

    public VillageMilitiaEntity getMilitia() {
        return militia;
    }

    @Override
    public boolean stillValid(Player player) {
        Player owner = militia.getOwner();
        return militia.isAlive()
            && player.distanceToSqr(militia) <= 64.0D
            && (owner == null || owner == player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem().copy();
        ItemStack moving = slot.getItem();
        if (slotIndex < EQUIPMENT_SLOTS.length) {
            if (!this.moveItemStackTo(moving, EQUIPMENT_SLOTS.length, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int equipmentIndex = 0; equipmentIndex < EQUIPMENT_SLOTS.length; equipmentIndex++) {
                Slot equipmentSlot = this.slots.get(equipmentIndex);
                if (equipmentSlot.mayPlace(moving) && !equipmentSlot.hasItem()
                    && this.moveItemStackTo(moving, equipmentIndex, equipmentIndex + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (moving.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private static final class EquipmentSlotAdapter extends Slot {
        private final VillageMilitiaEntity militia;
        private final EquipmentSlot equipmentSlot;

        private EquipmentSlotAdapter(VillageMilitiaEntity militia, EquipmentSlot equipmentSlot, int x, int y) {
            super(new EquipmentContainer(militia, equipmentSlot), 0, x, y);
            this.militia = militia;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (equipmentSlot == EquipmentSlot.MAINHAND
                || equipmentSlot == EquipmentSlot.OFFHAND) {
                return !stack.isEmpty();
            }
            return stack.has(DataComponents.EQUIPPABLE)
                && stack.canEquip(equipmentSlot, militia);
        }

        @Override
        public ItemStack getItem() {
            return militia.getItemBySlot(equipmentSlot);
        }

        @Override
        public void set(ItemStack stack) {
            militia.setItemSlot(equipmentSlot, stack);
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            set(stack);
            setChanged();
        }

        @Override
        public Identifier getNoItemIcon() {
            String slotName = switch (equipmentSlot) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                case MAINHAND -> "sword";
                case OFFHAND -> "shield";
                default -> throw new IllegalStateException("Unsupported militia equipment slot");
            };
            return Identifier.withDefaultNamespace("container/slot/" + slotName);
        }

    }

    private static final class EquipmentContainer implements Container {
        private final VillageMilitiaEntity militia;
        private final EquipmentSlot equipmentSlot;

        private EquipmentContainer(VillageMilitiaEntity militia, EquipmentSlot equipmentSlot) {
            this.militia = militia;
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? militia.getItemBySlot(equipmentSlot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = militia.getItemBySlot(equipmentSlot);
            ItemStack result = current.split(amount);
            militia.setItemSlot(equipmentSlot, current);
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            ItemStack current = militia.getItemBySlot(equipmentSlot);
            militia.setItemSlot(equipmentSlot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot == 0) {
                militia.setItemSlot(equipmentSlot, stack);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return militia.isAlive() && player.distanceToSqr(militia) <= 64.0D;
        }

        @Override
        public void clearContent() {
            militia.setItemSlot(equipmentSlot, ItemStack.EMPTY);
        }

    }
}
