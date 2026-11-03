package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** NEI's delete and magnet packets ({@code SET_SLOT}, {@code DELETE_ALL_ITEMS}, {@code TOGGLE_MAGNET}); all go to the server. */
public final class CheatPackets {

    /** The slot number NEI uses for the stack on the cursor. */
    public static final int CARRIED = -999;

    private CheatPackets() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation(NotEnoughItems.MODID, path);
    }

    /** Make a slot of the open container (or {@link #CARRIED}) hold {@code stack}; the server only accepts removing items. */
    public record SetSlot(int slot, ItemStack stack) implements NEIPacket {

        public static final PacketType<SetSlot> TYPE = new PacketType<>(id("cheat_slot"),
                b -> new SetSlot(b.readVarInt(), b.readItem()));

        @Override
        public PacketType<SetSlot> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeItem(stack);
        }
    }

    /** Empty every slot of the open container (Shift + delete button). */
    public record DeleteAll() implements NEIPacket {

        public static final PacketType<DeleteAll> TYPE = new PacketType<>(id("cheat_delete_all"), b -> new DeleteAll());

        @Override
        public PacketType<DeleteAll> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {}
    }

    /** Switch the magnet on or off for the sender. */
    public record Magnet(boolean on) implements NEIPacket {

        public static final PacketType<Magnet> TYPE = new PacketType<>(id("cheat_magnet"), b -> new Magnet(b.readBoolean()));

        @Override
        public PacketType<Magnet> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(on);
        }
    }
}
