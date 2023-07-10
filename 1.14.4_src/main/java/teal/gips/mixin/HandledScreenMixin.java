package teal.gips.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.container.Container;
import net.minecraft.container.PlayerContainer;
import net.minecraft.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static teal.gips.Gips.*;

@Mixin(ContainerScreen.class)
public abstract class HandledScreenMixin <T extends Container> extends Screen {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow @Final protected T container;

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int containerWidth;

    private static final int FUCKINGVALUE = 36;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    protected void init(CallbackInfo ci) {
        final int offsetY = container instanceof CreativeInventoryScreen.CreativeContainer ? -30 : 0;

        addButton(new ButtonWidget(x + containerWidth - 175,  y - 18 + offsetY, 60, 14, "Copy NBT", b -> copyNBT(getItemStacks(false))));
        addButton(new ButtonWidget(x + containerWidth - 110, y - 18 + offsetY, 65, 14, "Copy Name", b -> copyName(title)));
    }

    @Override
    public void onClose() {
        try {
            if (dumpNbt) {
                if (!gipsFolder.exists()) gipsFolder.mkdirs();

                ListTag nbtList = new ListTag();
                List<ItemStack> itemStacks = getItemStacks(true);

                for(ItemStack itemStack : itemStacks)
                    nbtList.add(itemStack.isEmpty() ? EMPTY : itemStack.toTag(new CompoundTag()));

                FileWriter fileWriter = new FileWriter(String.format("./gips/%s.nbt", System.currentTimeMillis()));
                fileWriter.write(nbtList.asString());
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            minecraft.player.addChatMessage(new LiteralText("Couldn't dump NBT to file, see logs for details.").formatted(Formatting.RED), false);
        }
    }

    private List<ItemStack> getItemStacks(boolean preserveInventory) {
        boolean isCreativeScreen = container instanceof CreativeInventoryScreen.CreativeContainer;
        boolean isSurvivalScreen = container instanceof PlayerContainer;
        final int offsetSlots = isCreativeScreen ? 9*3 : 0;
        List<ItemStack> itemStacks = container.slots.stream().map(Slot::getStack).toList();
        int sliceIndex = itemStacks.size();
        if (isCreativeScreen) {
            // 47 = the 9*4 slots of inventory space, 5 of armor + shield, 5 for the invisible crafting, and 1 for destroy item(?)
            // 47 = 36                               +5                   +5                                +1
            if (itemStacks.size() != 47) {
                return ((CreativeInventoryScreen.CreativeContainer) this.container).itemList;
            }
        } else if (!isSurvivalScreen) sliceIndex = itemStacks.size() - FUCKINGVALUE + offsetSlots;
        if(preserveInventory) sliceIndex = itemStacks.size();
        return itemStacks.subList(0, sliceIndex);
    }

    @Inject(
            method = "keyPressed",
            at = @At("TAIL")
    )
    public void keyPressedInject(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        super.keyPressed(keyCode, scanCode, modifiers);
        if (focusedSlot != null) {
            if (GetNBTKeybind.matchesKey(keyCode, scanCode)) copyNBT(List.of(focusedSlot.getStack()));
            else if (GetNameKeybind.matchesKey(keyCode, scanCode)) copyName(focusedSlot.getStack().getName());
        }
    }
}