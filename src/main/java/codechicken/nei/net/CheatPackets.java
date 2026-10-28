package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** NEI's delete and magnet packets ({@code SET_SLOT}, {@code DELETE_ALL_ITEMS}, {@code TOGGLE_MAGNET}); all go to the server. */
public final class CheatPackets {

    /** The slot number NEI uses for the stack on the cursor. */
    public static final int CARRIED = -999;

    private CheatPackets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, path));
    }

    /** Make a slot of the open container (or {@link #CARRIED}) hold {@code stack}; the server only accepts removing items. */
    public record SetSlot(int slot, ItemStack stack) implements CustomPacketPayload {

        public static final Type<SetSlot> TYPE = typeOf("cheat_slot");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSlot> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
                SetSlot::slot, ItemStack.OPTIONAL_STREAM_CODEC, SetSlot::stack, SetSlot::new);

        @Override
        public Type<SetSlot> type() {
            return TYPE;
        }
    }

    /** Empty every slot of the open container (Shift + delete button). */
    public record DeleteAll() implements CustomPacketPayload {

        public static final Type<DeleteAll> TYPE = typeOf("cheat_delete_all");
        public static final StreamCodec<ByteBuf, DeleteAll> CODEC = StreamCodec.unit(new DeleteAll());

        @Override
        public Type<DeleteAll> type() {
            return TYPE;
        }
    }

    /** Switch the magnet on or off for the sender. */
    public record Magnet(boolean on) implements CustomPacketPayload {

        public static final Type<Magnet> TYPE = typeOf("cheat_magnet");
        public static final StreamCodec<ByteBuf, Magnet> CODEC = ByteBufCodecs.BOOL.map(Magnet::new, Magnet::on);

        @Override
        public Type<Magnet> type() {
            return TYPE;
        }
    }
}
