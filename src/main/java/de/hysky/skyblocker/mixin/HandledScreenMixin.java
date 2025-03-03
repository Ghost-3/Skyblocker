package de.hysky.skyblocker.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.experiment.ChronomatronSolver;
import de.hysky.skyblocker.skyblock.experiment.ExperimentSolver;
import de.hysky.skyblocker.skyblock.experiment.SuperpairsSolver;
import de.hysky.skyblocker.skyblock.experiment.UltrasequencerSolver;
import de.hysky.skyblocker.skyblock.garden.VisitorHelper;
import de.hysky.skyblocker.skyblock.item.ItemProtection;
import de.hysky.skyblocker.skyblock.item.ItemRarityBackgrounds;
import de.hysky.skyblocker.skyblock.item.WikiLookup;
import de.hysky.skyblocker.skyblock.item.tooltip.BackpackPreview;
import de.hysky.skyblocker.skyblock.item.tooltip.CompactorDeletorPreview;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.gui.ContainerSolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.regex.Matcher;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {
    /**
     * This is the slot id returned for when a click is outside the screen's bounds
     */
    @Unique
    private static final int OUT_OF_BOUNDS_SLOT = -999;

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Shadow
    @Final
    protected T handler;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = "keyPressed")
    public void skyblocker$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.client != null && this.focusedSlot != null && keyCode != 256 && !this.client.options.inventoryKey.matchesKey(keyCode, scanCode) && WikiLookup.wikiLookup.matchesKey(keyCode, scanCode)) {
            WikiLookup.openWiki(this.focusedSlot, client.player);
        }
    }

    @Inject(at = @At("RETURN"), method = "render")
    public void skyblocker$renderScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Utils.isOnSkyblock()) return;

        if (SkyblockerConfigManager.get().general.visitorHelper && (Utils.getLocationRaw().equals("garden") && !getTitle().getString().contains("Logbook") || getTitle().getString().startsWith("Bazaar")))
            VisitorHelper.renderScreen(this.getTitle().getString(), context, textRenderer, handler, mouseX, mouseY);
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked")
    public void skyblocker$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SkyblockerConfigManager.get().general.visitorHelper && (Utils.getLocationRaw().equals("garden") && !getTitle().getString().contains("Logbook") || getTitle().getString().startsWith("Bazaar")))
            VisitorHelper.onMouseClicked(mouseX, mouseY, button, this.textRenderer);
    }

    @SuppressWarnings("DataFlowIssue")
    // makes intellij be quiet about this.focusedSlot maybe being null. It's already null checked in mixined method.
    @Inject(method = "drawMouseoverTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V"), cancellable = true)
    public void skyblocker$drawMouseOverTooltip(DrawContext context, int x, int y, CallbackInfo ci, @Local(ordinal = 0) ItemStack stack) {
        if (!Utils.isOnSkyblock()) return;

        // Hide Empty Tooltips
        if (SkyblockerConfigManager.get().general.hideEmptyTooltips && stack.getName().getString().equals(" ")) {
            ci.cancel();
        }

        // Backpack Preview
        boolean shiftDown = SkyblockerConfigManager.get().general.backpackPreviewWithoutShift ^ Screen.hasShiftDown();
        if (shiftDown && getTitle().getString().equals("Storage") && focusedSlot.inventory != client.player.getInventory() && BackpackPreview.renderPreview(context, this, focusedSlot.getIndex(), x, y)) {
            ci.cancel();
        }

        // Compactor Preview
        if (SkyblockerConfigManager.get().general.compactorDeletorPreview) {
            Matcher matcher = CompactorDeletorPreview.NAME.matcher(ItemUtils.getItemId(stack));
            if (matcher.matches() && CompactorDeletorPreview.drawPreview(context, stack, matcher.group("type"), matcher.group("size"), x, y)) {
                ci.cancel();
            }
        }
    }

    @ModifyVariable(method = "drawMouseoverTooltip", at = @At(value = "LOAD", ordinal = 0))
    private ItemStack skyblocker$experimentSolvers$replaceTooltipDisplayStack(ItemStack stack) {
        return skyblocker$experimentSolvers$getStack(focusedSlot, stack);
    }

    @ModifyVariable(method = "drawSlot", at = @At(value = "LOAD", ordinal = 3), ordinal = 0)
    private ItemStack skyblocker$experimentSolvers$replaceDisplayStack(ItemStack stack, DrawContext context, Slot slot) {
        return skyblocker$experimentSolvers$getStack(slot, stack);
    }

    @Unique
    private ItemStack skyblocker$experimentSolvers$getStack(Slot slot, @NotNull ItemStack stack) {
        ContainerSolver currentSolver = SkyblockerMod.getInstance().containerSolverManager.getCurrentSolver();
        if ((currentSolver instanceof SuperpairsSolver || currentSolver instanceof UltrasequencerSolver) && ((ExperimentSolver) currentSolver).getState() == ExperimentSolver.State.SHOW && slot.inventory instanceof SimpleInventory) {
            ItemStack itemStack = ((ExperimentSolver) currentSolver).getSlots().get(slot.getIndex());
            return itemStack == null ? stack : itemStack;
        }
        return stack;
    }

    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;clickSlot(IIILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void skyblocker$experimentSolvers$onSlotClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot != null) {
            ContainerSolver currentSolver = SkyblockerMod.getInstance().containerSolverManager.getCurrentSolver();
            if (currentSolver instanceof ExperimentSolver experimentSolver && experimentSolver.getState() == ExperimentSolver.State.SHOW && slot.inventory instanceof SimpleInventory) {
                if (experimentSolver instanceof ChronomatronSolver chronomatronSolver) {
                    Item item = chronomatronSolver.getChronomatronSlots().get(chronomatronSolver.getChronomatronCurrentOrdinal());
                    if ((slot.getStack().isOf(item) || ChronomatronSolver.TERRACOTTA_TO_GLASS.get(slot.getStack().getItem()) == item) && chronomatronSolver.incrementChronomatronCurrentOrdinal() >= chronomatronSolver.getChronomatronSlots().size()) {
                        chronomatronSolver.setState(ExperimentSolver.State.END);
                    }
                } else if (experimentSolver instanceof SuperpairsSolver superpairsSolver) {
                    superpairsSolver.setSuperpairsPrevClickedSlot(slot.getIndex());
                    superpairsSolver.setSuperpairsCurrentSlot(ItemStack.EMPTY);
                } else if (experimentSolver instanceof UltrasequencerSolver ultrasequencerSolver && slot.getIndex() == ultrasequencerSolver.getUltrasequencerNextSlot()) {
                    int count = ultrasequencerSolver.getSlots().get(ultrasequencerSolver.getUltrasequencerNextSlot()).getCount() + 1;
                    ultrasequencerSolver.getSlots().entrySet().stream().filter(entry -> entry.getValue().getCount() == count).findAny().map(Map.Entry::getKey).ifPresentOrElse(ultrasequencerSolver::setUltrasequencerNextSlot, () -> ultrasequencerSolver.setState(ExperimentSolver.State.END));
                }
            }
        }
    }

    /**
     * The naming of this method in yarn is half true, its mostly to handle slot/item interactions (which are mouse or keyboard clicks)
     * For example, using the drop key bind while hovering over an item will invoke this method to drop the players item
     */
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void skyblocker$onSlotInteract(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (Utils.isOnSkyblock()) {
            // When you try and drop the item by picking it up then clicking outside of the screen
            if (slotId == OUT_OF_BOUNDS_SLOT) {
                ItemStack cursorStack = this.handler.getCursorStack();

                if (ItemProtection.isItemProtected(cursorStack)) ci.cancel();
            }

            if (slot != null) {
                // When you click your drop key while hovering over an item
                if (actionType == SlotActionType.THROW) {
                    ItemStack stack = slot.getStack();

                    if (ItemProtection.isItemProtected(stack)) ci.cancel();
                }

                //Prevent salvaging
                if (this.getTitle().getString().equals("Salvage Items")) {
                    ItemStack stack = slot.getStack();

                    if (ItemProtection.isItemProtected(stack)) ci.cancel();
                }


                if (this.client != null && this.handler instanceof GenericContainerScreenHandler genericContainerScreenHandler && genericContainerScreenHandler.getRows() == 6) {
                    VisitorHelper.onSlotClick(slot, slotId, this.getTitle().getString());

                    //Prevent selling to NPC shops
                    ItemStack sellItem = this.handler.slots.get(49).getStack();

                    if (sellItem.getName().getString().equals("Sell Item") || skyblocker$doesLoreContain(sellItem, this.client, "buyback")) {
                        ItemStack stack = slot.getStack();

                        if (ItemProtection.isItemProtected(stack)) ci.cancel();
                    }
                }
            }
        }
    }

    //TODO make this a util method somewhere else, eventually
    private static boolean skyblocker$doesLoreContain(ItemStack stack, MinecraftClient client, String searchString) {
        return stack.getTooltip(client.player, TooltipContext.BASIC).stream().map(Text::getString).anyMatch(line -> line.contains(searchString));
    }

    @Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
    private void skyblocker$drawItemRarityBackground(DrawContext context, Slot slot, CallbackInfo ci) {
        if (Utils.isOnSkyblock() && SkyblockerConfigManager.get().general.itemInfoDisplay.itemRarityBackgrounds)
            ItemRarityBackgrounds.tryDraw(slot.getStack(), context, slot.x, slot.y);
    }
}
