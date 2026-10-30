package codechicken.nei;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The mod's own small config system, the same on every loader (NeoForge's {@code ModConfigSpec} does not exist on
 * Fabric). The builder mirrors {@code ModConfigSpec}: {@code comment(...).define(name, default)}. The file is
 * {@code config/notenoughitems-client.json}, one flat object of option names.
 */
public final class Cfg {

    private Cfg() {}

    /** One option. */
    public static class ConfigValue<T> implements Supplier<T> {

        final String name;
        final String comment;
        final T fallback;
        final Predicate<Object> valid;
        T value;

        ConfigValue(String name, String comment, T fallback, Predicate<Object> valid) {
            this.name = name;
            this.comment = comment;
            this.fallback = fallback;
            this.valid = valid;
            this.value = fallback;
        }

        @Override
        public T get() {
            return value;
        }

        public void set(T v) {
            if (v != null && valid.test(v)) value = v;
        }

        public T getDefault() {
            return fallback;
        }

        JsonElement toJson() {
            return new Gson().toJsonTree(value);
        }

        void fromJson(JsonElement e) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> implements BooleanSupplier {

        BooleanValue(String name, String comment, boolean fallback) {
            super(name, comment, fallback, o -> o instanceof Boolean);
        }

        @Override
        public boolean getAsBoolean() {
            return value;
        }

        @Override
        void fromJson(JsonElement e) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) value = e.getAsBoolean();
        }
    }

    public static final class IntValue extends ConfigValue<Integer> implements IntSupplier {

        final int min;
        final int max;

        IntValue(String name, String comment, int fallback, int min, int max) {
            super(name, comment, fallback, o -> o instanceof Integer);
            this.min = min;
            this.max = max;
        }

        @Override
        public int getAsInt() {
            return value;
        }

        /** A value outside the range is moved to the nearest end. */
        @Override
        public void set(Integer v) {
            if (v != null) value = Math.max(min, Math.min(max, v));
        }

        @Override
        void fromJson(JsonElement e) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) set(e.getAsInt());
        }
    }

    public static final class StringValue extends ConfigValue<String> {

        StringValue(String name, String comment, String fallback, Predicate<Object> valid) {
            super(name, comment, fallback, valid);
        }

        @Override
        void fromJson(JsonElement e) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) set(e.getAsString());
        }
    }

    /** {@code ModConfigSpec.Builder} in miniature: sections ({@code push} / {@code pop}) are accepted and ignored. */
    public static final class Builder {

        private final List<ConfigValue<?>> values = new ArrayList<>();
        private String comment = "";

        public Builder comment(String text) {
            comment = text;
            return this;
        }

        public Builder push(String section) {
            comment = "";
            return this;
        }

        public Builder pop() {
            comment = "";
            return this;
        }

        public BooleanValue define(String name, boolean fallback) {
            final BooleanValue v = new BooleanValue(name, take(), fallback);
            values.add(v);
            return v;
        }

        public StringValue define(String name, String fallback) {
            return define(name, fallback, o -> o instanceof String);
        }

        public StringValue define(String name, String fallback, Predicate<Object> valid) {
            final StringValue v = new StringValue(name, take(), fallback, valid);
            values.add(v);
            return v;
        }

        public IntValue defineInRange(String name, int fallback, int min, int max) {
            final IntValue v = new IntValue(name, take(), fallback, min, max);
            values.add(v);
            return v;
        }

        private String take() {
            final String c = comment;
            comment = "";
            return c;
        }

        public Spec build() {
            return new Spec(List.copyOf(values));
        }
    }

    /** All options of the mod and their file. */
    public static final class Spec {

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        private final List<ConfigValue<?>> values;
        private Path file;

        Spec(List<ConfigValue<?>> values) {
            this.values = values;
        }

        /** Reads the file (if it exists) and remembers it for {@link #save}. A damaged file leaves the defaults. */
        public void load(Path path) {
            file = path;
            if (!Files.isRegularFile(path)) return;
            try (Reader in = Files.newBufferedReader(path)) {
                final JsonObject root = GSON.fromJson(in, JsonObject.class);
                if (root == null) return;
                for (ConfigValue<?> v : values) {
                    if (root.has(v.name)) {
                        try {
                            v.fromJson(root.get(v.name));
                        } catch (RuntimeException e) {
                            NotEnoughItems.LOGGER.warn("Ignoring the config value {}: {}", v.name, e.toString());
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                NotEnoughItems.LOGGER.warn("Could not read the config {}: {}", path, e.toString());
            }
        }

        public void save() {
            if (file == null) return;
            final JsonObject root = new JsonObject();
            for (ConfigValue<?> v : values) root.add(v.name, v.toJson());
            try {
                Files.createDirectories(file.getParent());
                try (Writer out = Files.newBufferedWriter(file)) {
                    GSON.toJson(root, out);
                }
            } catch (IOException e) {
                NotEnoughItems.LOGGER.warn("Could not save the config {}: {}", file, e.toString());
            }
        }
    }
}
