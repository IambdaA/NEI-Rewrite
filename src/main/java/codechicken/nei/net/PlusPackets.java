package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** NEI's creative+ packets ({@code SET_CREATIVE_PLUS_MODE}, {@code SET_SLOT}, {@code CYCLE_CREATIVE_INV}); all go to the server. */
public final class PlusPackets {

    private PlusPackets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, path));
    }

    /** Open the extended inventory. */
    public record Open() implements CustomPacketPayload {

        public static final Type<Open> TYPE = typeOf("plus_open");
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Put a stack into a slot (0-53) of the open extended inventory. */
    public record Slot(int slot, ItemStack stack) implements CustomPacketPayload {

        public static final Type<Slot> TYPE = typeOf("plus_slot");
        public static final StreamCodec<RegistryFriendlyByteBuf, Slot> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
                Slot::slot, ItemStack.OPTIONAL_STREAM_CODEC, Slot::stack, Slot::new);

        @Override
        public Type<Slot> type() {
            return TYPE;
        }
    }

    /** Move the rows of the extended inventory, inventory and hotbar by {@code steps}. */
    public record Cycle(int steps) implements CustomPacketPayload {

        public static final Type<Cycle> TYPE = typeOf("plus_cycle");
        public static final StreamCodec<ByteBuf, Cycle> CODEC = ByteBufCodecs.VAR_INT.map(Cycle::new, Cycle::steps);

        @Override
        public Type<Cycle> type() {
            return TYPE;
        }
    }
}
