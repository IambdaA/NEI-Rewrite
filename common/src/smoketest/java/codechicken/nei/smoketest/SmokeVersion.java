package codechicken.nei.smoketest;

import net.minecraft.client.Minecraft;

/** The few game calls of the test driver that differ between Minecraft versions. */
final class SmokeVersion {

    private SmokeVersion() {}

    static long dayTime(Minecraft mc) {
        return mc.level == null ? 0 : mc.level.getDayTime();
    }
}
