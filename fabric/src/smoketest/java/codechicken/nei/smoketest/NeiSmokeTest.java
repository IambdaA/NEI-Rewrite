package codechicken.nei.smoketest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;

/** Fabric entry of NEI's test driver ({@link SmokeCore}): ticks and screen setup. */
public final class NeiSmokeTest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SmokeCore core = new SmokeCore();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> core.tick());
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            Button b = core.fakeWidget(screen);
            if (b != null) Screens.getButtons(screen).add(b);
        });
    }
}
