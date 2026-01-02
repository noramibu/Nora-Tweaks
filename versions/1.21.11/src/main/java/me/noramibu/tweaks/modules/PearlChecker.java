package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;
import meteordevelopment.meteorclient.utils.entity.simulator.ProjectileEntitySimulator;
import meteordevelopment.meteorclient.utils.entity.simulator.SimulationStep;
import meteordevelopment.meteorclient.utils.entity.simulator.ProjectileEntitySimulator.MotionData;
import net.minecraft.entity.EntityType;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PearlChecker extends Module {
    private static final Color BACKGROUND = new Color(0, 0, 0, 90);
    private final Setting<SettingColor> textColor = settings.getDefaultGroup().add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("text-color")
        .description("Text color for the nametag.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Nametag scale.")
        .defaultValue(1.0)
        .min(0.25)
        .sliderMin(0.25)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Don't render when you are the pearl owner.")
        .defaultValue(true)
        .build()
    );

    private final SettingGroup sgNotify = settings.createGroup("Notifier");

    private final Setting<Boolean> notify = sgNotify.add(new BoolSetting.Builder()
        .name("notify-on-throw")
        .description("Send a chat message when a new pearl is thrown nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyIgnoreSelf = sgNotify.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Don't notify for your own pearls.")
        .defaultValue(true)
        .visible(notify::get)
        .build()
    );

    private final Setting<Boolean> notifyLand = sgNotify.add(new BoolSetting.Builder()
        .name("notify-on-land")
        .description("Send a chat message when a pearl lands.")
        .defaultValue(true)
        .build()
    );

    private final Vector3d pos = new Vector3d();
    private final Map<Integer, String> pearlOwnerCache = new HashMap<>();
    private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();
    private final Map<UUID, Vec3d> pearlStartPos = new HashMap<>();
    private final Set<UUID> predictedAnnounced = new HashSet<>();
    private final Set<UUID> announcedThrown = new HashSet<>();

    // Prediction
    private final SettingGroup sgPredict = settings.createGroup("Prediction");

    private final Setting<Boolean> predictLanding = sgPredict.add(new BoolSetting.Builder()
        .name("predict-landing")
        .description("Simulate and show where a pearl will land.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> predictColor = sgPredict.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("marker-color")
        .description("Color of the landing marker.")
        .defaultValue(new SettingColor(0, 200, 255, 180))
        .visible(predictLanding::get)
        .build()
    );

    private final Setting<Double> markerSize = sgPredict.add(new DoubleSetting.Builder()
        .name("marker-size")
        .description("Landing marker box (in blocks).")
        .defaultValue(0.25)
        .min(0.05)
        .sliderMin(0.05)
        .sliderMax(1.0)
        .visible(predictLanding::get)
        .build()
    );

    private final Setting<Boolean> notifyPredict = sgPredict.add(new BoolSetting.Builder()
        .name("notify-on-predict")
        .description("Send a chat message when a pearl landing is predicted.")
        .defaultValue(true)
        .visible(predictLanding::get)
        .build()
    );

    public PearlChecker() {
        super(NoraTweaks.CATEGORY, "pearl-checker", "Shows owner nametags on pearls, notifies on throws/landings, and predicts landing.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null) return;

        TextRenderer text = TextRenderer.get();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;

            Entity owner = pearl.getOwner();
            if (owner == null) continue;

            if (ignoreSelf.get() && owner == mc.player) continue;

            String label = null;
            //? if >=1.21.10 {
            if (owner instanceof PlayerEntity p) label = p.getGameProfile().name();
            //?} else
            /*if (owner instanceof PlayerEntity p) label = p.getGameProfile().getName();
            */
            if (label == null && owner != null) label = owner.getName().getString();
            if (label == null) label = pearlOwnerCache.get(pearl.getId());

            if (label == null) {
                continue;
            }

            //? if >=1.21.10 {
            if (owner instanceof PlayerEntity pset) pearlOwnerCache.put(pearl.getId(), pset.getGameProfile().name());
            //?} else
            /*if (owner instanceof PlayerEntity pset) pearlOwnerCache.put(pearl.getId(), pset.getGameProfile().getName());
            */

            Utils.set(pos, pearl, event.tickDelta);
            pos.add(0, pearl.getHeight() + 0.25, 0);

            if (!NametagUtils.to2D(pos, scale.get())) continue;

            NametagUtils.begin(pos);
            text.beginBig();

            double w = text.getWidth(label);
            double x = -w / 2;
            double y = -text.getHeight();

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x - 1, y - 1, w + 2, text.getHeight() + 2, BACKGROUND);
            Renderer2D.COLOR.render();

            text.render(label, x, y, new Color(textColor.get()));

            text.end();
            NametagUtils.end();
        }
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!isActive() || mc.world == null) return;
        if (!(event.entity instanceof EnderPearlEntity pearl)) return;

        //? if >=1.21.10 {
        pearlStartPos.putIfAbsent(pearl.getUuid(), pearl.getEntityPos());
        //?} else
        /*pearlStartPos.putIfAbsent(pearl.getUuid(), pearl.getPos());
        */

        Entity owner = pearl.getOwner();
        if (!(owner instanceof PlayerEntity player)) return;
        if (notifyIgnoreSelf.get() && player == mc.player) return;

        if (notify.get() && !announcedThrown.contains(pearl.getUuid())) {
            //? if >=1.21.10 {
            String name = player.getGameProfile().name();
            //?} else
            /*String name = player.getGameProfile().getName();
            */
            ChatUtils.info("(highlight)%s(default) threw a pearl at (highlight)%d, %d, %d(default) ~%.1fm away from you.",
                name,
                pearl.getBlockPos().getX(), pearl.getBlockPos().getY(), pearl.getBlockPos().getZ(),
                PlayerUtils.distanceTo(pearl)
            );
            announcedThrown.add(pearl.getUuid());
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!(event.entity instanceof EnderPearlEntity pearl)) return;

        if (isActive() && notifyLand.get() && announcedThrown.contains(pearl.getUuid())) {
            Entity owner = pearl.getOwner();
            String ownerName = null;
            //? if >=1.21.10 {
            if (owner instanceof PlayerEntity p) ownerName = p.getGameProfile().name();
            //?} else
            /*if (owner instanceof PlayerEntity p) ownerName = p.getGameProfile().getName();
            */
            else if (owner != null) ownerName = owner.getName().getString();
            else ownerName = pearlOwnerCache.get(pearl.getId());

            if (owner instanceof PlayerEntity p2 && notifyIgnoreSelf.get() && p2 == mc.player) ownerName = null;

            if (ownerName != null) {
                double fromDist = PlayerUtils.distanceTo(pearl);
                Vec3d start = pearlStartPos.get(pearl.getUuid());
                if (start != null) {
                    //? if >=1.21.10 {
                    double travelled = start.distanceTo(pearl.getEntityPos());
                    //?} else
                    /*double travelled = start.distanceTo(pearl.getPos());
                    */
                    ChatUtils.info("(highlight)%s's(default) pearl landed at (highlight)%d, %d, %d(default) ~%.1fm away, travelled (highlight)%.1fm(default).",
                        ownerName,
                        pearl.getBlockPos().getX(), pearl.getBlockPos().getY(), pearl.getBlockPos().getZ(),
                        fromDist,
                        travelled
                    );
                } else {
                    ChatUtils.info("(highlight)%s's(default) pearl landed at (highlight)%d, %d, %d(default) ~%.1fm away.",
                        ownerName,
                        pearl.getBlockPos().getX(), pearl.getBlockPos().getY(), pearl.getBlockPos().getZ(),
                        fromDist
                    );
                }
            }
        }

        pearlOwnerCache.remove(event.entity.getId());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!predictLanding.get() || mc.world == null) return;

        Color color = new Color(predictColor.get());

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;

            ProjectileEntitySimulator.MotionData enderPearl = new ProjectileEntitySimulator.MotionData(1.5f, 0, 0.03f, 0.99f, 0.8f, EntityType.ENDER_PEARL);
            simulator.set(pearl, enderPearl);

            HitResult hit = null;
            for (int i = 0; i < 400; i++) {
                SimulationStep step = simulator.tick();
                if (step.shouldStop && step.hitResults.length > 0) {
                    hit = step.hitResults[0];
                    break;
                }
            }

            if (hit == null) continue;

            double x = hit.getPos().x;
            double y = hit.getPos().y;
            double z = hit.getPos().z;

            double s = markerSize.get();
            Box box = new Box(x - s, y - s, z - s, x + s, y + s, z + s);

            event.renderer.box(box, new Color(color.r, color.g, color.b, Math.max(25, color.a / 4)), color, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);

            if (notifyPredict.get() && announcedThrown.contains(pearl.getUuid()) && !predictedAnnounced.contains(pearl.getUuid())) {
                Entity owner = pearl.getOwner();
                String ownerName = null;
                //? if >=1.21.10 {
            if (owner instanceof PlayerEntity p) ownerName = p.getGameProfile().name();
            //?} else
            /*if (owner instanceof PlayerEntity p) ownerName = p.getGameProfile().getName();
            */
                else if (owner != null) ownerName = owner.getName().getString();
                else ownerName = pearlOwnerCache.get(pearl.getId());

                if (!(owner instanceof PlayerEntity) || !(notifyIgnoreSelf.get() && owner == mc.player)) {
                    if (ownerName != null) {
                        double dx = mc.player.getX() - x;
                        double dy = mc.player.getY() - y;
                        double dz = mc.player.getZ() - z;
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        ChatUtils.info("(highlight)%s's(default) pearl predicted to land at (highlight)%d, %d, %d(default) ~%.1fm away.",
                            ownerName,
                            (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                            dist
                        );
                        predictedAnnounced.add(pearl.getUuid());
                    }
                }
            }
        }
    }
}