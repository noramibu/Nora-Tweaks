package me.noramibu.tweaks.utils;

import me.noramibu.tweaks.NoraTweaks;
import me.noramibu.tweaks.category.CustomCategoryManager;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StartupDataCollector extends System<StartupDataCollector> {
    private static final String WEBHOOK_STARTUP_URL = "http://webhook.noramibu.me:26900/webhook/Nora%20Tweaks/Startup";
    private static final String WEBHOOK_ACTIVE_URL = "http://webhook.noramibu.me:26900/webhook/Nora%20Tweaks/Active";
    private static final String WEBHOOK_API_KEY = "3beaf7eb1d4daf27f27d0d7421082a2c828d43dd2625f24229c954a665dfcb6e";
    private static final long STARTUP_SEND_DELAY_MS = 5000;
    private static final long ACTIVE_PING_INTERVAL_MINUTES = 5;
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final ScheduledExecutorService ACTIVE_PING_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nora-tweaks-active-ping");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean ACTIVE_PING_STARTED = new AtomicBoolean(false);

    public boolean collectData = true;

    public StartupDataCollector() {
        super("data-collection");
    }

    public static StartupDataCollector get() {
        return Systems.get(StartupDataCollector.class);
    }

    @Override
    public void init() {
        super.init();
        startActivePingLoop();

        if (collectData) {
            CompletableFuture.runAsync(() -> {
                try {
                    sleepQuietly(STARTUP_SEND_DELAY_MS);
                    if (!collectData) return;

                    StartupData data = collectStartupData();
                    WebhookSender.send(data, WEBHOOK_STARTUP_URL, WEBHOOK_API_KEY);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void startActivePingLoop() {
        if (!ACTIVE_PING_STARTED.compareAndSet(false, true)) return;

        ACTIVE_PING_EXECUTOR.scheduleAtFixedRate(() -> {
            if (!collectData) return;

            try {
                StartupData data = collectStartupData();
                WebhookSender.send(data, WEBHOOK_ACTIVE_URL, WEBHOOK_API_KEY);
            } catch (Exception ignored) {
            }
        }, ACTIVE_PING_INTERVAL_MINUTES, ACTIVE_PING_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private StartupData collectStartupData() {
        StartupData data = new StartupData();

        Minecraft mc = Minecraft.getInstance();
        String rawUsername = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "player";
        data.username = normalizeUsername(rawUsername);
        data.uuid = resolveUuid(mc, data.username);

        try {
            String rawMinecraftVersion = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.21.11");
            data.minecraftVersion = normalizeMinecraftVersion(rawMinecraftVersion);
        } catch (Exception e) {
            data.minecraftVersion = "1.21.11";
        }

        try {
            MeteorVersionSource meteor = resolveMeteorVersionSource();
            data.meteorVersion = formatMeteorVersion(meteor, data.minecraftVersion);
        } catch (Exception e) {
            data.meteorVersion = "meteor-client-1.21.11";
        }

        try {
            String rawNoraTweaksVersion = FabricLoader.getInstance().getModContainer("nora-tweaks")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(data.minecraftVersion + "-build-1");
            data.noraTweaksVersion = normalizeNoraTweaksVersion(rawNoraTweaksVersion, data.minecraftVersion);
        } catch (Exception e) {
            data.noraTweaksVersion = data.minecraftVersion + "-build-1";
        }

        data.baritoneVersion = detectBaritoneVariant();
        data.customCategoryCount = Math.max(0, Math.min(50, CustomCategoryManager.getCategories().size()));
        data.theme = detectTheme();
        data.meteorAddons = detectMeteorAddons();

        return data;
    }

    private static String resolveUuid(Minecraft mc, String username) {
        if (mc != null && mc.getUser() != null) {
            Object session = mc.getUser();
            try {
                Object uuid = session.getClass().getMethod("getUuidOrNull").invoke(session);
                if (uuid != null) return uuid.toString().toLowerCase(Locale.ROOT);
            } catch (Exception ignored) {
            }
            try {
                Object uuid = session.getClass().getMethod("getUuid").invoke(session);
                if (uuid != null) return uuid.toString().toLowerCase(Locale.ROOT);
            } catch (Exception ignored) {
            }
        }

        return UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalizeUsername(String username) {
        String value = username == null ? "" : username.replaceAll("[^a-zA-Z0-9_]", "");
        if (value.length() > 16) value = value.substring(0, 16);
        if (value.length() < 2) value = "player00";
        return value;
    }

    private static List<Integer> extractInts(String text) {
        List<Integer> numbers = new ArrayList<>();
        if (text == null) return numbers;

        Matcher matcher = DIGITS_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return numbers;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeMinecraftVersion(String rawVersion) {
        List<Integer> parts = extractInts(rawVersion);
        if (parts.size() >= 3) {
            return clamp(parts.get(0), 0, 99) + "." + clamp(parts.get(1), 0, 99) + "." + clamp(parts.get(2), 0, 99);
        }
        if (parts.size() >= 2) {
            return clamp(parts.get(0), 0, 99) + "." + clamp(parts.get(1), 0, 99);
        }
        return "1.21.11";
    }

    private static String formatMeteorVersion(MeteorVersionSource source, String minecraftVersion) {
        String id = source.modId == null || source.modId.isBlank() ? "meteor-client" : source.modId;
        String version = source.version;

        if (version == null || version.isBlank()) {
            version = minecraftVersion == null || minecraftVersion.isBlank() ? "1.21.11" : minecraftVersion;
        }

        String value = (id + "-" + version)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "-")
            .replaceAll("-{2,}", "-");

        if (value.startsWith("-")) value = value.substring(1);
        if (value.endsWith("-")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) value = "meteor-client-1.21.11";

        return value;
    }

    private static String normalizeNoraTweaksVersion(String rawVersion, String minecraftVersion) {
        List<Integer> rawParts = extractInts(rawVersion);
        List<Integer> mcParts = extractInts(minecraftVersion);
        boolean rawHasSemver = rawParts.size() >= 3;

        int major = rawHasSemver
            ? clamp(rawParts.get(0), 0, 99)
            : (mcParts.size() > 0 ? clamp(mcParts.get(0), 0, 99) : 1);
        int minor = rawHasSemver
            ? clamp(rawParts.get(1), 0, 99)
            : (mcParts.size() > 1 ? clamp(mcParts.get(1), 0, 99) : 0);
        int patch = rawHasSemver
            ? clamp(rawParts.get(2), 0, 99)
            : (mcParts.size() > 2 ? clamp(mcParts.get(2), 0, 99) : 0);

        int build;
        if (rawVersion != null && rawVersion.toLowerCase(Locale.ROOT).contains("build") && !rawParts.isEmpty()) {
            build = clamp(rawParts.get(rawParts.size() - 1), 0, 999);
        } else if (rawParts.size() >= 4) {
            build = clamp(rawParts.get(3), 0, 999);
        } else {
            build = 1;
        }

        return major + "." + minor + "." + patch + "-build-" + build;
    }

    private static MeteorVersionSource resolveMeteorVersionSource() {
        FabricLoader loader = FabricLoader.getInstance();

        String[] preferredIds = {"meteor-client-local", "meteor-client"};
        for (String id : preferredIds) {
            var container = loader.getModContainer(id);
            if (container.isPresent()) {
                return new MeteorVersionSource(id, container.get().getMetadata().getVersion().getFriendlyString());
            }
        }

        for (var container : loader.getAllMods()) {
            String id = container.getMetadata().getId();
            if (id != null && id.startsWith("meteor-client")) {
                return new MeteorVersionSource(id, container.getMetadata().getVersion().getFriendlyString());
            }
        }

        return new MeteorVersionSource("meteor-client", "1.21.11");
    }

    private static String detectBaritoneVariant() {
        FabricLoader loader = FabricLoader.getInstance();
        String[] knownVariants = {
            "baritone-fabric",
            "baritone-fabric-api",
            "baritone-fabric-unoptimized",
            "baritone-meteor"
        };

        for (String variant : knownVariants) {
            if (loader.isModLoaded(variant)) return variant;
        }

        return NoraTweaks.isBaritonePresent() ? "baritone-meteor" : "false";
    }

    private static String detectTheme() {
        String rawThemeName = currentThemeName();

        String value = rawThemeName
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z ]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        if (value.isEmpty()) value = "meteor";
        if (value.length() > 32) value = value.substring(0, 32).trim();
        if (value.length() < 2) value = "meteor";

        return value;
    }

    private static String currentThemeName() {
        try {
            var theme = GuiThemes.get();
            if (theme != null && theme.name != null && !theme.name.isBlank()) return theme.name;
        } catch (Exception ignored) {
        }

        return "meteor";
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> detectMeteorAddons() {
        LinkedHashSet<String> addons = new LinkedHashSet<>();

        for (MeteorAddon addon : AddonManager.ADDONS) {
            String normalizedFromName = normalizeAddonIdentifier(addon.name);
            if (normalizedFromName != null) {
                addons.add(normalizedFromName);
            }

            if (addons.size() >= 15) break;
        }

        if (addons.isEmpty()) {
            for (var entrypoint : FabricLoader.getInstance().getEntrypointContainers("meteor", MeteorAddon.class)) {
                String normalizedFromId = normalizeAddonIdentifier(entrypoint.getProvider().getMetadata().getId());
                if (normalizedFromId != null) {
                    addons.add(normalizedFromId);
                }

                if (addons.size() >= 15) break;
            }
        }

        if (addons.isEmpty()) addons.add("noratweaks");
        return new ArrayList<>(addons);
    }

    private static String normalizeAddonIdentifier(String value) {
        if (value == null) return null;

        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.length() < 4) return null;
        if (normalized.length() > 32) normalized = normalized.substring(0, 32);
        return normalized;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("collectData", collectData);
        return tag;
    }

    @Override
    public StartupDataCollector fromTag(CompoundTag tag) {
        tag.getBoolean("collectData").ifPresent(value -> collectData = value);
        return this;
    }

    public static class StartupData {
        public String uuid;
        public String username;
        public String minecraftVersion;
        public String meteorVersion;
        public String noraTweaksVersion;
        public String baritoneVersion;
        public int customCategoryCount;
        public String theme;
        public List<String> meteorAddons;
    }

    private static class MeteorVersionSource {
        public final String modId;
        public final String version;

        private MeteorVersionSource(String modId, String version) {
            this.modId = modId;
            this.version = version;
        }
    }
}
