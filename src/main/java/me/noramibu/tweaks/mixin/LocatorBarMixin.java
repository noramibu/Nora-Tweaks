//? if >=1.21.7 {
package me.noramibu.tweaks.mixin;

import me.noramibu.tweaks.modules.BetterLocator;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocatorBar.class)
public abstract class LocatorBarMixin {
    @Shadow @Final private Minecraft minecraft;

    @Unique private static final Identifier XP_BACKGROUND = Identifier.withDefaultNamespace("hud/experience_bar_background");
    @Unique private static final Identifier XP_PROGRESS = Identifier.withDefaultNamespace("hud/experience_bar_progress");

    @Unique private BetterLocator module;

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void renderBarHead(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        module = Modules.get().get(BetterLocator.class);
        if (module == null || !module.isActive()) return;

        if (module.alwaysShowExperience.get()) {
            int maxExp = minecraft.player.getXpNeededForNextLevel();
            if (maxExp > 0) {
                int cx = getCenterX();
                int cy = getCenterY();
                int progress = (int) (minecraft.player.experienceProgress * 183.0F);

                context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACKGROUND, cx, cy, 182, 5);
                if (progress > 0) {
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, XP_PROGRESS, 182, 5, 0, 0, cx, cy, progress, 5);
                }

                renderOverlays(context, cx, cy);
                ci.cancel();
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderBarReturn(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (module != null && module.isActive()) {
            renderOverlays(context, getCenterX(), getCenterY());
            renderTrackedPlayerWaypoints(context, tickCounter, getCenterY());
        }
    }

    @Unique
    private void renderOverlays(GuiGraphicsExtractor context, int cx, int cy) {
        if (module.showDirections.get()) {
            float yaw = Mth.wrapDegrees(minecraft.gameRenderer.mainCamera().yRot());
            int centerX = cx + 91;
            drawDirection(context, "S", 0, yaw, centerX, cy);
            drawDirection(context, "W", 90, yaw, centerX, cy);
            drawDirection(context, "N", 180, yaw, centerX, cy);
            drawDirection(context, "E", -90, yaw, centerX, cy);
        }
        renderMeteorWaypoints(context, cx + 91, cy);
    }

    @Unique
    private int getCenterX() {
        return (minecraft.getWindow().getGuiScaledWidth() - 182) / 2;
    }

    @Unique
    private int getCenterY() {
        return minecraft.getWindow().getGuiScaledHeight() - 29;
    }

    @Unique
    private void drawDirection(GuiGraphicsExtractor context, String text, float dirYaw, float playerYaw, int centerX, int y) {
        float relative = Mth.wrapDegrees(dirYaw - playerYaw);
        if (relative >= -60 && relative <= 60) {
            renderText(context, text, centerX + Mth.floor(relative * 1.4416667), y, 0xFFFFFFFF);
        }
    }

    @Unique
    private void renderMeteorWaypoints(GuiGraphicsExtractor context, int centerX, int centerY) {
        if (!module.displayWaypoints.get()) return;

        //? if >=1.21.11 {
        Vec3 cameraPos = minecraft.gameRenderer.mainCamera().position();
        //?} else
        /*Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        */
        float playerYaw = Mth.wrapDegrees(minecraft.gameRenderer.mainCamera().yRot());
        boolean showData = (module.displayWaypointName.get() || module.displayWaypointDistance.get()) 
            && (!module.displayWaypointOnlyOnTab.get() || minecraft.options.keyPlayerList.isDown());

        for (Waypoint waypoint : Waypoints.get()) {
            if (!waypoint.visible.get() || !Waypoints.checkDimension(waypoint)) continue;

            BlockPos pos = waypoint.getPos();
            double dx = pos.getX() - cameraPos.x;
            double dz = pos.getZ() - cameraPos.z;

            double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float relative = Mth.wrapDegrees((float) angle - playerYaw);

            if (relative >= -60 && relative <= 60) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                int x = centerX + Mth.floor(relative * 1.4416667);
                int color = waypoint.color.get().getPacked();

                float progress = getProgress((float) dist, WaypointStyle.DEFAULT_NEAR_DISTANCE, WaypointStyle.DEFAULT_FAR_DISTANCE);
                float size = Mth.lerp(progress, 3.0f, 6.0f);
                int offset = (int) size / 2;
                int yOffset = centerY + 1 + (3 - (int) size) / 2;

                context.fill(x - offset, yOffset, x + offset + 1, yOffset + (int) size, color);

                if (showData) {
                    String text = module.displayWaypointName.get() ? waypoint.name.get() : "";
                    if (module.displayWaypointDistance.get()) text += (text.isEmpty() ? "" : " ") + "(" + (int) dist + "m)";
                    renderText(context, text, x + 0.5f, centerY - 5.0f, color);
                }
            }
        }
    }

    @Unique
    private void renderTrackedPlayerWaypoints(GuiGraphicsExtractor context, DeltaTracker tickCounter, int top) {
        if (module == null || !module.isActive()) return;
        if (!module.displayHeads.get() && !module.displayPlayerData.get()) return;
        if (minecraft.player == null || minecraft.player.connection == null) return;

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) return;

        Level level = cameraEntity.level();
        TickRateManager tickRateManager = level.tickRateManager();
        PartialTickSupplier partialTickSupplier = entity -> tickCounter.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
        int screenMiddle = Mth.ceil((context.guiWidth() - 9) / 2.0F);

        minecraft.player.connection.getWaypointManager().forEachWaypoint(cameraEntity, waypoint -> {
            if (waypoint.id().left().map(uuid -> uuid.equals(cameraEntity.getUUID())).orElse(false)) return;

            double angle = waypoint.yawAngleToCamera(level, minecraft.gameRenderer.mainCamera(), partialTickSupplier);
            if (angle <= -60.0 || angle > 60.0) return;

            int dotPosition = Mth.floor(angle * 173.0 / 2.0 / 60.0);
            int x = screenMiddle + dotPosition;
            int y = top - 2;
            renderTrackedPlayerWaypointData(context, waypoint, x, y);
        });
    }

    @Unique
    private void renderTrackedPlayerWaypointData(GuiGraphicsExtractor context, TrackedWaypoint waypoint, int x, int y) {
        PlayerInfo entry = waypoint.id().left().map(uuid -> minecraft.getConnection().getPlayerInfo(uuid)).orElse(null);
        if (entry == null) return;

        double dist = Math.sqrt(waypoint.distanceSquared(minecraft.getCameraEntity()));
        WaypointStyle style = minecraft.gui.hud.getWaypointStyles().get(waypoint.icon().style);
        float styleDistance =
            Double.isFinite(dist) ? (float) Math.min(dist, Float.MAX_VALUE) : Float.POSITIVE_INFINITY;
        float scale = Mth.lerp(getProgress(styleDistance, style.nearDistance(), style.farDistance()), 0.5f, 1.0f);
        float size = 9 * scale;
        float drawY = y - (size - 9) / 2;

        if (module.displayHeads.get()) {
            float drawX = x - (size - 9) / 2;
            //? if >=1.21.9 {
            Identifier skin = entry.getSkin().body().texturePath();
            context.blit(RenderPipelines.GUI_TEXTURED, skin, (int) drawX, (int) drawY, 8.0f, 8.0f, (int) size, (int) size, 8, 8, 64, 64);
            context.blit(RenderPipelines.GUI_TEXTURED, skin, (int) drawX, (int) drawY, 40.0f, 8.0f, (int) size, (int) size, 8, 8, 64, 64);
            //?} else {
            /*Identifier skin = entry.getSkinTextures().texture();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) drawX, (int) drawY, 8.0f, 8.0f, (int) size, (int) size, 8, 8, 64, 64);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) drawX, (int) drawY, 40.0f, 8.0f, (int) size, (int) size, 8, 8, 64, 64);
            */
            //?}
        }

        if (module.displayPlayerData.get() && (!module.displayPlayerOnlyOnTab.get() || minecraft.options.keyPlayerList.isDown())) {
            int textColor = -1;
            if (module.respectPlayerColor.get()) {
                textColor = (Integer) waypoint.icon().color.orElseGet(() ->
                    waypoint.id().map(
                        u -> ARGB.setBrightness(ARGB.color(255, u.hashCode()), 0.9F),
                        n -> ARGB.setBrightness(ARGB.color(255, n.hashCode()), 0.9F)
                    )
                );
            }

            String distanceText = formatPlayerDistance(dist);
            String text = "";
            if (module.displayPlayerName.get()) {
                Component name = entry.getTabListDisplayName();
                //? if >=1.21.9 {
                text = (name != null ? name : Component.nullToEmpty(entry.getProfile().name())).getString();
                //?} else
                /*text = (name != null ? name : Text.of(entry.getProfile().getName())).getString();
                */
                if (module.displayPlayerDistance.get()) text += " (" + distanceText + ")";
            } else {
                text = distanceText;
            }
            renderText(context, text, x + 4.5f, drawY - 5, textColor);
        }
    }

    @Unique
    private String formatPlayerDistance(double dist) {
        return Double.isFinite(dist) ? (int) dist + "m" : "???";
    }

    @Unique
    private float getProgress(float dist, float near, float far) {
        return 1.0f - Mth.clamp((dist - near) / (far - near), 0.0f, 1.0f);
    }

    @Unique
    private void renderText(GuiGraphicsExtractor context, String text, float x, float y, int color) {
        if (text.isEmpty()) return;
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(0.5f, 0.5f);
        int width = minecraft.font.width(text);

        if (color == 0xFFFFFFFF) {
            context.text(minecraft.font, text, -width / 2, 0, color, true);
        } else {
            context.textWithBackdrop(minecraft.font, Component.nullToEmpty(text), -width / 2, 0, width, color);
        }

        context.pose().popMatrix();
    }
}
//?} else {
/*package me.noramibu.tweaks.mixin;

// Stub class for versions before 1.21.7 where LocatorBar doesn't exist.
// The MixinPlugin will skip applying this mixin.
public class LocatorBarMixin {}
*/
//?}
