package codechicken.nei.smoketest;

import net.minecraft.client.gui.components.Button;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge entry of NEI's test driver ({@link SmokeCore}): ticks and screen setup. */
@Mod("nei_smoketest")
public final class NeiSmokeTest {

    public NeiSmokeTest() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        SmokeCore core = new SmokeCore();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) core.tick();
        });
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            Button b = core.fakeWidget(e.getScreen());
            if (b != null) e.addListener(b);
        });
    }
}
