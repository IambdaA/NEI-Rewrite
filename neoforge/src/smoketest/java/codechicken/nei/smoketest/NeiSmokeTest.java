package codechicken.nei.smoketest;

import net.minecraft.client.gui.components.Button;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge entry of NEI's test driver ({@link SmokeCore}): ticks and screen setup. */
@Mod(value = "nei_smoketest", dist = Dist.CLIENT)
public final class NeiSmokeTest {

    public NeiSmokeTest() {
        SmokeCore core = new SmokeCore();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> core.tick());
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            Button b = core.fakeWidget(e.getScreen());
            if (b != null) e.addListener(b);
        });
    }
}
