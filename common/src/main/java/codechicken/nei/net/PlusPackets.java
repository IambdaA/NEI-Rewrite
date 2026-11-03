package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** NEI's creative+ packets ({@code SET_CREATIVE_PLUS_MODE}, {@code SET_SLOT}, {@code CYCLE_CREATIVE_INV}); all go to the server. */
public final class PlusPackets {

    private PlusPackets() {}

    private static ResourceLocation id(String path) {
        return new ResourceLocation(NotEnoughItems.MODID, path);
    }

    /** Open the extended inventory. */
    public record Open() implements NEIPacket {

        public static final PacketType<Open> TYPE = new PacketType<>(id("plus_open"), b -> new Open());

        @Override
        public PacketType<Open> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {}
    }

    /** Put a stack into a slot (0-53) of the open extended inventory. */
    public record Slot(int slot, ItemStack stack) implements NEIPacket {

        public static final PacketType<Slot> TYPE = new PacketType<>(id("plus_slot"), b -> new Slot(b.readVarInt(), b.readItem()));

        @Override
        public PacketType<Slot> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeItem(stack);
        }
    }

    /** Move the rows of the extended inventory, inventory and hotbar by {@code steps}. */
    public record Cycle(int steps) implements NEIPacket {

        public static final PacketType<Cycle> TYPE = new PacketType<>(id("plus_cycle"), b -> new Cycle(b.readVarInt()));

        @Override
        public PacketType<Cycle> type() {
            return TYPE;
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(steps);
        }
    }
}
