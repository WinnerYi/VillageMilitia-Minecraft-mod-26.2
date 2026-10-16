package com.example.examplemod.client;

import com.example.examplemod.menu.MilitiaEquipmentMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MilitiaEquipmentScreen extends AbstractContainerScreen<MilitiaEquipmentMenu> {
    private static final Identifier INVENTORY_TEXTURE =
        Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    public MilitiaEquipmentScreen(MilitiaEquipmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            INVENTORY_TEXTURE,
            this.leftPos,
            this.topPos,
            0.0F,
            0.0F,
            this.imageWidth,
            this.imageHeight,
            256,
            256
        );
        graphics.fill(RenderPipelines.GUI, this.leftPos + 95, this.topPos + 8,
            this.leftPos + 171, this.topPos + 78, 0xFFC6C6C6);
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            this.leftPos + 13,
            this.topPos + 8,
            this.leftPos + 89,
            this.topPos + 78,
            30,
            0.0625F,
            mouseX,
            mouseY,
            this.menu.getMilitia()
        );
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String ownerName = this.menu.getMilitia().getOwner() == null
            ? "None"
            : this.menu.getMilitia().getOwner().getName().getString();
        String modeName = switch (this.menu.getMilitia().getMilitiaMode()) {
            case IDLE -> "Idle";
            case GUARD -> "Guard";
            case FOLLOW -> "Follow";
            case PATROL -> "Patrol";
        };

        graphics.textRenderer().accept(105, 32, Component.literal("Owner: " + ownerName));
        graphics.textRenderer().accept(105, 52, Component.literal("Mode: " + modeName));
    }
}
