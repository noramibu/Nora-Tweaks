package me.noramibu.tweaks.commands;

import com.google.gson.Gson;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectBeginEvent;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;

public class HostInfoCommand extends Command {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private ServerAddress address;
    private String serverId;
    private String serverType;
    private String detectedVersion;
    private HostRequest activeRequest;

    public HostInfoCommand() {
        super("hostinfo", "Shows server host and Minecraft protocol information.");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void build(LiteralArgumentBuilder builder) {
        builder.executes(context -> {
            if (!ensureInGame()) return SINGLE_SUCCESS;
            load();
            return SINGLE_SUCCESS;
        });
    }

    private boolean ensureInGame() {
        if (mc.player != null && mc.getConnection() != null) return true;
        error("Join a server first.");
        return false;
    }

    private void load() {
        ServerAddress serverAddress = serverAddress();
        if (serverAddress == null || serverAddress.getHost().isBlank()) {
            printInfo(null, "no server address");
            return;
        }

        info("Checking host info...");
        HostRequest request = new HostRequest();
        activeRequest = request;
        CompletableFuture
            .supplyAsync(() -> loadHost(serverAddress.getHost()))
            .exceptionally(throwable -> {
                request.error = "lookup failed";
                return null;
            })
            .thenAccept(data -> mc.execute(() -> {
                if (request != activeRequest) return;
                activeRequest = null;
                printInfo(data, data == null ? request.error : null);
            }));
    }

    private IpData loadHost(String host) {
        try {
            String ip = InetAddress.getByName(host).getHostAddress();
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://ipinfo.io/" + ip + "/json"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return new IpData(ip, null, null, null, null, null);

            IpData data = GSON.fromJson(response.body(), IpData.class);
            return data == null || data.ip == null ? new IpData(ip, null, null, null, null, null) : data;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void printInfo(IpData host, String hostError) {
        ServerData server = mc.getCurrentServer();
        ServerAddress serverAddress = serverAddress();

        info("Nora host info:");
        field("Address", server == null ? null : server.ip);
        field("Host", serverAddress == null ? null : serverAddress.getHost());
        field("Port", serverAddress == null ? null : serverAddress.getPort());
        field("IP", host == null ? null : host.ip);
        field("Host name", host == null ? null : host.hostname);
        field("Host org", host == null ? null : host.org);
        field("Host location", host == null ? null : host.location());
        if (host == null) field("Host lookup", hostError);
        field("Minecraft version", server == null ? null : server.version.getString());
        field("Minecraft protocol", server == null ? null : server.protocol);
        field("Detected version", detectedVersion == null ? "<= 1.20.4" : detectedVersion);
        field("Brand", mc.getConnection().serverBrand());
        field("Login", serverType);
        field("Server ID", serverId);
        field("TPS", formatTps());
        field("Ping", ping() + " ms");
    }

    private void field(String name, Object value) {
        String text = value == null || value.toString().isBlank() ? "N/A" : value.toString();
        info("%s: (highlight)%s(default)", name, text);
    }

    private String formatTps() {
        float tps = TickRate.INSTANCE.getTickRate();
        return Float.isNaN(tps) ? "not ready" : String.format(Locale.ROOT, "%.2f", tps);
    }

    private int ping() {
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private ServerAddress serverAddress() {
        if (address != null) return address;
        ServerData server = mc.getCurrentServer();
        return server == null ? null : ServerAddress.parseString(server.ip);
    }

    @EventHandler
    private void onServerConnectBegin(ServerConnectBeginEvent event) {
        address = event.address;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        serverId = null;
        serverType = null;
        detectedVersion = null;
        address = null;
        activeRequest = null;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundSelectKnownPacks packet) {
            packet.knownPacks().stream()
                .filter(pack -> pack.isVanilla() && pack.id().equals("core"))
                .findFirst()
                .ifPresent(pack -> detectedVersion = pack.version());
        } else if (event.packet instanceof ClientboundHelloPacket packet) {
            serverId = packet.getServerId().isBlank() ? null : packet.getServerId();
            serverType = packet.shouldAuthenticate() ? "Premium" : "Cracked";
        }
    }

    private static class HostRequest {
        private String error = "lookup failed";
    }

    private record IpData(String ip, String hostname, String city, String region, String country, String org) {
        private String location() {
            List<String> parts = new ArrayList<>();
            if (city != null && !city.isBlank()) parts.add(city);
            if (region != null && !region.isBlank()) parts.add(region);
            if (country != null && !country.isBlank()) parts.add(country);
            return String.join(", ", parts);
        }
    }
}
