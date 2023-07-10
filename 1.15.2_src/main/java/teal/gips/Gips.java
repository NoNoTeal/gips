package teal.gips;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandException;
import net.minecraft.command.arguments.ItemStackArgumentType;
import net.minecraft.command.arguments.NbtCompoundTagArgumentType;
import net.minecraft.command.arguments.NbtPathArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.predicate.NbtPredicate;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;
import java.util.function.Predicate;

@Environment(EnvType.CLIENT)
public class Gips implements ClientModInitializer {

    public static final MinecraftClient minecraft = MinecraftClient.getInstance();
    public static final CompoundTag EMPTY = new CompoundTag();
    public static final KeyBinding GetNBTKeybind = new KeyBinding("teal.gips.key.copynbt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "teal.gips");
    public static final KeyBinding GetNameKeybind = new KeyBinding("teal.gips.key.copyname", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "teal.gips");
    public static final File gipsFolder = new File("./gips/");
    public static boolean dumpNbt = true;

    // Prevents spam by making sure the element isn't saved multiple times and creating 10 billion toasts.
    private static Tag nbtCache;

    private static void setClipboard(String contents) {
        minecraft.keyboard.setClipboard(contents);
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(GetNBTKeybind);
        KeyBindingHelper.registerKeyBinding(GetNameKeybind);

        ClientTickEvents.END_CLIENT_TICK.register(Gips::tickEvent);

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(
                CommandManager.literal("gips").then(
                        CommandManager.literal("viewnbt")
                                .executes(Gips::getNbt)
                                .then(CommandManager.argument("path", NbtPathArgumentType.nbtPath())
                                        .executes(Gips::getNbt)
                                )
                ).then(
                        CommandManager.literal("copynbt")
                                .executes(context -> getNbt(context, true))
                                .then(CommandManager.argument("path", NbtPathArgumentType.nbtPath())
                                        .executes(context -> getNbt(context, true))
                                )
                ).then(
                        CommandManager.literal("modifynbt")
                                .then(CommandManager.argument("data", NbtCompoundTagArgumentType.nbtCompound())
                                        .executes(Gips::setNbt)
                                )
                ).then(
                        CommandManager.literal("dump")
                                .executes(Gips::dumpNbt)
                ).then(
                        CommandManager.literal("give")
                                .then(CommandManager.argument("item", ItemStackArgumentType.itemStack())
                                        .executes(Gips::giveItem)
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(Gips::giveItem)
                                        )
                                )
                )
        ));
    }

    private static int giveItem(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (minecraft.player == null) throw new CommandException(new LiteralText("Could not get player."));
        if (!minecraft.player.isCreative()) throw new CommandException(new LiteralText("You need to be in creative mode."));
        int amount = 1;
        try {
            amount = IntegerArgumentType.getInteger(context, "count");
        } catch (IllegalArgumentException IAE) {

        }
        for(int slot = 0; slot < 9; slot++) {
            if(!minecraft.player.inventory.getInvStack(slot).isEmpty()) continue;
            minecraft.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(slot + 36, ItemStackArgumentType.getItemStackArgument(context, "item").createStack(amount, true)));
            minecraft.player.sendMessage(new LiteralText("Created item."));
            return 0;
        }
        throw new CommandException(new LiteralText("Your hotbar is full."));
    }

    private static int dumpNbt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        dumpNbt = !dumpNbt;
        context.getSource().getPlayer().addChatMessage(new LiteralText("Turned NBT dumping ").append(new LiteralText(dumpNbt ? "ON" : "OFF").formatted(dumpNbt ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)), true);
        return 0;
    }

    private static int setNbt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (minecraft.player == null) throw new CommandException(new LiteralText("Could not get player."));
        if (!minecraft.player.isCreative()) throw new CommandException(new LiteralText("You need to be in creative mode."));
        ItemStack heldItem = minecraft.player.getMainHandStack();
        if (heldItem.isEmpty()) throw new CommandException(new LiteralText("You need to hold an item."));
        heldItem.setTag(NbtCompoundTagArgumentType.getCompoundTag(context, "data"));
        context.getSource().getPlayer().addChatMessage(new LiteralText("Modified item."), true);
        return 0;
    }

    private static int getNbt(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return getNbt(context, false);
    }

    private static int getNbt(CommandContext<ServerCommandSource> context, boolean copy) throws CommandSyntaxException {
        if (minecraft.player == null) throw new CommandException(new LiteralText("Could not get player."));
        ItemStack heldItem = minecraft.player.getMainHandStack();
        if (heldItem.isEmpty()) throw new CommandException(new LiteralText("You need to hold an item."));

        NbtPathArgumentType.NbtPath path;
        CompoundTag nbt = heldItem.getOrCreateTag();

        try {
            path = context.getArgument("path", NbtPathArgumentType.NbtPath.class);
            // Pray and hope that the first element exists.
            nbt = (CompoundTag) path.get(heldItem.getOrCreateTag()).get(0);
        } catch (IllegalArgumentException IAE) {

        } catch (CommandSyntaxException e) {
            throw new CommandException(new LiteralText("Invalid NBT Path."));
        }

        if(copy) {
            setClipboard(nbt.asString());

            context.getSource().getPlayer().addChatMessage(new LiteralText("Copied NBT to clipboard."), true);
        } else {
            Text msg = new LiteralText("Properties of ").append(heldItem.getName()).append("\n");
            msg.append(nbt.asString());

            context.getSource().getPlayer().addChatMessage(msg, false);
        }

        return 0;
    }

    private static void tickEvent(MinecraftClient client) {
        boolean gNBT = GetNBTKeybind.wasPressed();
        if(gNBT) {
            Entity entity = minecraft.getCameraEntity();
            if(entity != null) {
                HitResult blockHit = entity.rayTrace(50.0D, 0.0F, false);
                HitResult fluidHit = entity.rayTrace(50.0D, 0.0F, true);
                Entity entityHit = null;
                Vec3d vec3d = new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
                Vec3d vec3d2 = entity.getRotationVec(1.0F).multiply(50.0F);
                Vec3d vec3d3 = vec3d.add(vec3d2);
                Box box = entity.getBoundingBox().stretch(vec3d2).expand(1.0D);
                Predicate<Entity> predicate = (entityx) -> !entityx.isSpectator() && entityx.collides();
                EntityHitResult entityHitResult = ProjectileUtil.rayTrace(entity, vec3d, vec3d3, box, predicate, 50*50);
                if (entityHitResult != null && !(vec3d.squaredDistanceTo(entityHitResult.getPos()) > (double)50*50)) entityHit = entityHitResult.getEntity();
                if(entityHit != null) {
                    copyNBT(NbtPredicate.entityToTag(entityHit));
                } else if(blockHit.getType() == HitResult.Type.BLOCK) {
                    BlockState blockState = client.world.getBlockState(((BlockHitResult) blockHit).getBlockPos());
                    if(blockState != null) {
                        copyNBT(NbtHelper.fromBlockState(blockState));
                    }
                } else if(fluidHit.getType() == HitResult.Type.BLOCK) {
                    BlockState blockState = client.world.getBlockState(((BlockHitResult) fluidHit).getBlockPos());
                    if(blockState != null) {
                        copyNBT(NbtHelper.fromBlockState(blockState));
                    }
                }
            }
        }
    }

    public static void copyName(Text title) {
        Gips.setClipboard(Text.Serializer.toJson(title));
        minecraft.getToastManager().add(new GipsToast("Copied stack name", "to clipboard!", false));
    }

    public static void copyNBT(List<ItemStack> itemStacks) {
        switch(itemStacks.size()) {
            case 0 -> minecraft.getToastManager().add(new GipsToast("No slot selected.", true));
            case 1 -> copyNBT(itemStacks.get(0).getOrCreateTag());
            default -> {
                ListTag nbtList = new ListTag();
                for (ItemStack itemStack : itemStacks) {
                    nbtList.add(itemStack.isEmpty() ? EMPTY : itemStack.toTag(new CompoundTag()));
                }
                copyNBT(nbtList);
            }
        }
    }

    private static void copyNBT(Tag nbt) {
        if(nbt.equals(nbtCache)) return;
        nbtCache = nbt;
        Gips.setClipboard(nbt.asString());
        minecraft.getToastManager().add(new GipsToast("Copied NBT to clipboard!", false));
    }
}