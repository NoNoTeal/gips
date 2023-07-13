package teal.gips.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.container.Container;
import net.minecraft.container.PlayerContainer;
import net.minecraft.container.Slot;
import net.minecraft.entity.player.PlayerInventory;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import teal.gips.GipsToast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import static teal.gips.Gips.*;

@Mixin(ContainerScreen.class)
public abstract class HandledScreenMixin <T extends Container> extends Screen {

    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow @Final protected T container;
    @Shadow protected int y;

    @Shadow @Final protected PlayerInventory playerInventory;
    @Unique private static final int FUCKINGVALUE = 36;
    @Unique private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    protected void init(CallbackInfo ci) {
        final int offsetY = container instanceof CreativeInventoryScreen.CreativeContainer ? -30 : 0;
        final int centerX = (this.width/2) - (65/2);
        addButton(new ButtonWidget(centerX - 65, y - 18 + offsetY, 60, 14, I18n.translate("teal.gips.key.copynbt"), b -> copyNBT(getContainerNBT(), false)));
        addButton(new ButtonWidget(centerX, y - 18 + offsetY, 65, 14, I18n.translate("teal.gips.key.copyname"), b -> copyName(title)));
        addButton(new ButtonWidget(centerX + 70, y - 18 + offsetY, 60, 14, I18n.translate("teal.gips.dumpnbt"), this::writeToFile));
    }

    @Override
    public void onClose() {
        writeToFile(null);
    }

    @Unique
    private void writeToFile(@Nullable ButtonWidget b) {
        try {
            if (dumpNbt) {
                if (!gipsFolder.exists()) gipsFolder.mkdirs();

                CompoundTag BET = getContainerNBT();
                ListTag playerInventory = minecraft.player.inventory.serialize(new ListTag());

                String title = this.getTitle().getString();
                String inventory = this.playerInventory.getName().getString();
                String date = dateFormat.format(new Date());
                // Order of "best" name: Name of container -> "Inventory" -> Name of obfuscated class (though when modding it shows the deobfuscated class)
                String bestName = (date + '-' + (title.isEmpty() ? inventory.isEmpty() ? this.getClass().getSimpleName() : inventory : title)).replaceAll(Pattern.quote(File.separator), "");
                FileWriter fileWriter = new FileWriter(String.format("./gips/%s.nbt", bestName.substring(0, Math.min(bestName.length(), 252))));
                fileWriter.write(BET.asString());
                fileWriter.close();

                bestName = date + "+InventoryDump";
                FileWriter fileWriter2 = new FileWriter(String.format("./gips/%s.nbt", bestName.substring(0, Math.min(bestName.length(), 252))));
                fileWriter2.write(playerInventory.asString());
                fileWriter2.close();

                if (b != null && minecraft != null) {
                    minecraft.getToastManager().add(new GipsToast("Dumped NBT", false));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            if(b == null) {
                minecraft.player.addChatMessage(new LiteralText("Couldn't dump NBT to file, see logs for details.").formatted(Formatting.RED), false);
            } else {
                b.setMessage(Formatting.RED + "Dump NBT");
            }
        }
    }

    @Unique
    private CompoundTag getContainerNBT() {
        boolean isCreativeScreen = container instanceof CreativeInventoryScreen.CreativeContainer;
        boolean isSurvivalScreen = container instanceof PlayerContainer;
        final int offsetSlots = isCreativeScreen ? 9*3 : 0;
        List<ItemStack> itemStacks = container.slots.stream().map(Slot::getStack).toList();
        if (isCreativeScreen) {
            // 47 = the 9*4 slots of inventory space, 5 of armor + shield, 5 for the invisible crafting, and 1 for destroy item(?)
            // 47 = 36                               +5                   +5                                +1
            if (itemStacks.size() != 47) {
                itemStacks = ((CreativeInventoryScreen.CreativeContainer) this.container).itemList;
            }
        } else if (!isSurvivalScreen) {
            int sliceIndex = itemStacks.size() - FUCKINGVALUE + offsetSlots;
            itemStacks = itemStacks.subList(0, sliceIndex);
        }
        CompoundTag nbt = new CompoundTag();
        CompoundTag BET = new CompoundTag();
        ListTag Items = new ListTag();
        for(int i=0; i < itemStacks.size(); i++) {
            ItemStack itemStack = itemStacks.get(i);
            if(itemStack.isEmpty()) continue;
            CompoundTag nbtCompound = itemStack.toTag(new CompoundTag());
            nbtCompound.putInt("Slot", i);
            Items.add(nbtCompound);
        }
        BET.put("Items", Items);
        BET.putString("CustomName", Text.Serializer.toJson(title));
        nbt.put("BlockEntityTag", BET);
        return nbt;
    }

    @Inject(
            method = "keyPressed",
            at = @At("TAIL")
    )
    public void keyPressedInject(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        super.keyPressed(keyCode, scanCode, modifiers);
        if (focusedSlot != null) {
            if (GetNBTKeybind.matchesKey(keyCode, scanCode)) copyNBT(focusedSlot.getStack().getOrCreateTag(), false);
            else if (GetNameKeybind.matchesKey(keyCode, scanCode)) copyName(focusedSlot.getStack().getName());
        }
    }
}