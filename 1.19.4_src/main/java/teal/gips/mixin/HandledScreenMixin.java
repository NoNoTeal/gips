package teal.gips.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
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

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin <T extends ScreenHandler> extends Screen {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow @Final protected T handler;

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;

    @Shadow @Final protected Text playerInventoryTitle;
    private static final int FUCKINGVALUE = 36;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    protected void init(CallbackInfo ci) {
        final int offsetY = handler instanceof CreativeInventoryScreen.CreativeScreenHandler ? -30 : 0;
        addDrawableChild(ButtonWidget
                .builder(Text.literal("Copy NBT"), b -> copyNBT(getItemStacks(false)))
                .dimensions(x + backgroundWidth - 175, y - 18 + offsetY, 60, 14).build());

        addDrawableChild(ButtonWidget
                .builder(Text.literal("Copy Name"), b -> copyName(title))
                .dimensions(x + backgroundWidth - 110, y - 18 + offsetY, 65, 14).build());
    }

    @Inject(
            method = "close",
            at = @At("TAIL")
    )
    public void close(CallbackInfo ci) {
        try {
            if (dumpNbt) {
                if (!gipsFolder.exists()) gipsFolder.mkdirs();

                NbtList nbtList = new NbtList();
                List<ItemStack> itemStacks = getItemStacks(true);

                for(ItemStack itemStack : itemStacks)
                    nbtList.add(itemStack.isEmpty() ? EMPTY : itemStack.writeNbt(new NbtCompound()));

                FileWriter fileWriter = new FileWriter(String.format("./gips/%s.nbt", System.currentTimeMillis()));
                fileWriter.write(nbtList.asString());
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            minecraft.player.sendMessage(Text.literal("Couldn't dump NBT to file, see logs for details.").formatted(Formatting.RED));
        }
    }

    private List<ItemStack> getItemStacks(boolean preserveInventory) {
        boolean isCreativeScreen = handler instanceof CreativeInventoryScreen.CreativeScreenHandler;
        boolean isSurvivalScreen = handler instanceof PlayerScreenHandler;
        final int offsetSlots = isCreativeScreen ? 9*3 : 0;
        List<ItemStack> itemStacks = handler.slots.stream().map(Slot::getStack).toList();
        int sliceIndex = itemStacks.size();
        if (isCreativeScreen) {
            // 47 = the 9*4 slots of inventory space, 5 of armor + shield, 5 for the invisible crafting, and 1 for destroy item(?)
            // 47 = 36                               +5                   +5                                +1
            if (itemStacks.size() != 47) {
                return ((CreativeInventoryScreen.CreativeScreenHandler) this.handler).itemList;
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