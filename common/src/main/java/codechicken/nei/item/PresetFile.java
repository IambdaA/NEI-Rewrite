package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The presets file, in NEI's format ({@code saves/NEI/global/presets.ini}): a line {@code ; {"name": ..., "enabled": ...,
 * "mode": ...}} starts a preset, the lines after it are its items. Items are registry ids here (NEI wrote item stack
 * ids), so a file from NEI is not readable.
 */
public final class PresetFile {

    private PresetFile() {}

    public static List<Preset> parse(List<String> lines) {
        final List<Preset> out = new ArrayList<>();
        Preset preset = new Preset();

        for (String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith(";")) {
                final JsonObject settings;
                try {
                    settings = JsonParser.parseString(line.substring(1).trim()).getAsJsonObject();
                } catch (RuntimeException e) {
                    continue; // a damaged header line: its items join the previous preset, as in NEI
                }
                if (!preset.items.isEmpty()) { // NEI does not keep empty presets
                    out.add(preset);
                    preset = new Preset();
                }
                if (settings.has("name")) preset.name = settings.get("name").getAsString();
                if (settings.has("enabled")) preset.enabled = settings.get("enabled").getAsBoolean();
                if (settings.has("mode")) {
                    try {
                        preset.mode = Preset.Mode.valueOf(settings.get("mode").getAsString());
                    } catch (IllegalArgumentException e) {
                        // unknown mode: keep HIDE
                    }
                }
            } else {
                preset.items.add(line);
            }
        }
        if (!preset.items.isEmpty()) out.add(preset);
        return out;
    }

    public static List<String> lines(List<Preset> presets) {
        final List<String> out = new ArrayList<>();
        for (Preset p : presets) {
            final JsonObject settings = new JsonObject();
            settings.addProperty("name", p.name);
            settings.addProperty("enabled", p.enabled);
            settings.addProperty("mode", p.mode.name());
            out.add("; " + settings);
            out.addAll(p.items);
        }
        return out;
    }
}
