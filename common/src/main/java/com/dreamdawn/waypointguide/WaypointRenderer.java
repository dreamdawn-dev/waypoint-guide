package com.dreamdawn.waypointguide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class WaypointRenderer {

    private static final float DIAMOND_SIZE_CLOSE = 12.0f;
    private static final float DIAMOND_SIZE_FAR = 8.0f;
    private static final float DISTANCE_THRESHOLD = 20.0f;
    private static final float EDGE_MARGIN = 15.0f;
    private static final float ARROW_SIZE = 10.0f;
    private static final float FLOAT_AMPLITUDE = 2.0f;
    private static final float FLOAT_PERIOD = 40.0f;
    private static final float CROSSHAIR_SCALE_CLOSE = 1.5f;
    private static final float CROSSHAIR_SCALE_FAR = 2.0f;
    private static final float CROSSHAIR_TOLERANCE = 4.0f;
    private static final float SIZE_LERP_SPEED = 0.03f;
    private static final float CROSSHAIR_LERP_SPEED = 0.03f;
    private static final float ARROW_ANIM_SPEED = 0.015f;
    // 与 SIZE_LERP_SPEED / CROSSHAIR_LERP_SPEED 相同的指数插值速度，保证淡入淡出与图标缩放动画手感一致
    private static final float VISIBILITY_LERP_SPEED = 0.03f;
    private static final float DISMISS_DURATION = 1.5f;
    private static final float DISMISS_MAX_SCALE = 1.3f;
    private static final float DISMISS_GROW_PHASE = 0.3f;
    private static final float DISMISS_CLICK_RANGE = 5.0f;

    private static final Map<String, Float> animatedSizes = new HashMap<>();
    private static final Map<String, Float> arrowAnimations = new HashMap<>();
    private static final Set<String> lastOffScreen = new HashSet<>();
    private static final Map<String, Float> crosshairScales = new HashMap<>();
    private static final Set<String> currentCrosshairWps = new HashSet<>();
    private static final Map<String, Float> visibilityProgress = new HashMap<>();

    private static class DismissAnimation {
        final Waypoint waypoint;
        final float startTime;
        final float distance;
        boolean particlesSpawned;
        float lastScreenX;
        float lastScreenY;
        boolean hasLastScreen;

        DismissAnimation(Waypoint wp, float startTime, float distance) {
            this.waypoint = wp;
            this.startTime = startTime;
            this.distance = distance;
            this.particlesSpawned = false;
            this.hasLastScreen = false;
        }
    }

    private static final Map<String, DismissAnimation> dismissAnimations = new HashMap<>();

    /** 右键消除请求回调（由平台入口设置为发送 C2S 数据包） */
    public static Consumer<String> dismissRequestCallback;

    static {
        ClientWaypointData.onWaypointsRemoved = WaypointRenderer::handleWaypointsRemoved;
    }

    private WaypointRenderer() {}

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        String currentDim = mc.player.level().dimension().location().toString();

        List<Waypoint> wps = ClientWaypointData.getWaypoints();
        if (wps.isEmpty() && dismissAnimations.isEmpty()) return;

        Set<String> activeIds = new HashSet<>();
        for (Waypoint wp : wps) {
            activeIds.add(wp.getId());
        }
        animatedSizes.keySet().removeIf(id -> !activeIds.contains(id));
        crosshairScales.keySet().removeIf(id -> !activeIds.contains(id));
        visibilityProgress.keySet().removeIf(id -> !activeIds.contains(id));

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float centerX = screenW / 2.0f;
        float centerY = screenH / 2.0f;

        long gameTime = mc.level.getGameTime();
        float smoothTime = gameTime + partialTick;

        double fov = mc.options.fov().get();
        double tanHalfFov = Math.tan(Math.toRadians(fov / 2.0));
        double aspectRatio = (double) screenW / screenH;

        Vector3f lookVec = camera.getLookVector();
        Vector3f upVec = camera.getUpVector();
        Vector3f leftVec = camera.getLeftVector();

        Set<String> currentOffScreen = new HashSet<>();
        currentCrosshairWps.clear();

        Font font = mc.font;

        for (Waypoint wp : wps) {
            if (!wp.getDimension().equals(currentDim)) continue;

            if (dismissAnimations.containsKey(wp.getId())) continue;

            Vec3 rel = new Vec3(wp.getX() - camPos.x, wp.getY() - camPos.y, wp.getZ() - camPos.z);

            double camX = -(rel.x * leftVec.x() + rel.y * leftVec.y() + rel.z * leftVec.z());
            double camY = rel.x * upVec.x() + rel.y * upVec.y() + rel.z * upVec.z();
            double camZ = rel.x * lookVec.x() + rel.y * lookVec.y() + rel.z * lookVec.z();

            double distance = rel.length();

            // 带距离限制的标记在进入/离开可视距离时以指数插值淡入淡出（速度与图标缩放动画一致）
            boolean rangeLimited = wp.getMaxRenderDistance() > 0;
            boolean inRange = !rangeLimited || distance <= wp.getMaxRenderDistance();

            float targetProgress = inRange ? 1.0f : 0.0f;
            float progress;
            if (!rangeLimited) {
                progress = 1.0f;
            } else {
                progress = visibilityProgress.getOrDefault(wp.getId(), 0.0f);
                progress += (targetProgress - progress) * VISIBILITY_LERP_SPEED;
                if (Math.abs(targetProgress - progress) < 0.01f) {
                    progress = targetProgress;
                }
                visibilityProgress.put(wp.getId(), progress);
            }

            if (camZ <= 0.05) {
                if (inRange) {
                    currentOffScreen.add(wp.getId());
                    drawOffScreenArrow(graphics, screenW, screenH, centerX, centerY, camX, camY, wp, distance, progress);
                }
                continue;
            }

            double screenX = (camX / camZ) / (tanHalfFov * aspectRatio);
            double screenY = -(camY / camZ) / tanHalfFov;

            double pixelX = (screenX + 1.0) / 2.0 * screenW;
            double pixelY = (screenY + 1.0) / 2.0 * screenH;

            boolean onScreen = pixelX >= 0 && pixelX <= screenW && pixelY >= 0 && pixelY <= screenH;

            if (!onScreen) {
                if (inRange) {
                    currentOffScreen.add(wp.getId());
                    drawOffScreenArrow(graphics, screenW, screenH, centerX, centerY, camX, camY, wp, distance, progress);
                }
                continue;
            }

            // 淡入淡出过程中若已完全不可见则不再绘制
            if (progress <= 0.01f) continue;

            boolean isClose = distance <= DISTANCE_THRESHOLD;
            float targetBaseSize = isClose ? DIAMOND_SIZE_CLOSE : DIAMOND_SIZE_FAR;

            float currentBaseSize = animatedSizes.getOrDefault(wp.getId(), targetBaseSize);
            currentBaseSize += (targetBaseSize - currentBaseSize) * SIZE_LERP_SPEED;
            if (Math.abs(targetBaseSize - currentBaseSize) < 0.05f) {
                currentBaseSize = targetBaseSize;
            }
            animatedSizes.put(wp.getId(), currentBaseSize);

            float floatOffset = (float) (Math.sin(smoothTime / FLOAT_PERIOD * Math.PI * 2.0) * FLOAT_AMPLITUDE);

            float dx = (float) pixelX - centerX;
            float dy = (float) pixelY - centerY;
            float distToCenter = (float) Math.sqrt(dx * dx + dy * dy);
            float halfSize = currentBaseSize / 2.0f;
            boolean crosshairOn = distToCenter < (halfSize + CROSSHAIR_TOLERANCE);
            boolean fullyVisible = progress >= 1.0f;

            float wpCrosshairTarget = crosshairOn ? (isClose ? CROSSHAIR_SCALE_CLOSE : CROSSHAIR_SCALE_FAR) : 1.0f;
            if (crosshairOn && fullyVisible) {
                currentCrosshairWps.add(wp.getId());
            }
            if (!fullyVisible) {
                wpCrosshairTarget = 1.0f;
            }
            float wpCrosshairScale = crosshairScales.getOrDefault(wp.getId(), 1.0f);
            wpCrosshairScale += (wpCrosshairTarget - wpCrosshairScale) * CROSSHAIR_LERP_SPEED;
            crosshairScales.put(wp.getId(), wpCrosshairScale);

            float finalSize = halfSize * wpCrosshairScale * progress;
            float drawX = (float) pixelX;
            float drawY = (float) pixelY + floatOffset;

            drawDiamond(graphics, drawX, drawY, finalSize, wp.getColor());

            String nameText = wp.getName();
            String distText = String.format("%.0fm", distance);
            int nameColor = applyAlpha(wp.getNameColor(), progress);
            int distColor = applyAlpha(0xBBBBBBBB, progress);

            int nameWidth = font.width(nameText);
            int distWidth = font.width(distText);

            float gapProgress = (currentBaseSize - DIAMOND_SIZE_FAR) / (DIAMOND_SIZE_CLOSE - DIAMOND_SIZE_FAR);
            // 间距与锚点同样乘以动画进度，让文字从图标中心向外缩放（与 20 格缩放一致）
            float nameGapF = (8.0f + (12.0f - 8.0f) * gapProgress) * progress;
            float distGapF = (2.0f + (4.0f - 2.0f) * gapProgress) * progress;

            float totalScale = (currentBaseSize / DIAMOND_SIZE_CLOSE) * wpCrosshairScale * progress;
            float invScale = 1.0f / totalScale;
            float textAnchorY = currentBaseSize / 2.0f * wpCrosshairScale * progress;

            float centerXI = drawX;
            float nameScreenX = centerXI - nameWidth * totalScale / 2.0f;
            float distScreenX = centerXI - distWidth * totalScale / 2.0f;
            float nameScreenY = drawY - textAnchorY - nameGapF * wpCrosshairScale;
            float distScreenY = drawY + textAnchorY + distGapF * wpCrosshairScale;

            if (Math.abs(totalScale - 1.0f) > 0.005f) {
                PoseStack poseStack = graphics.pose();
                poseStack.pushPose();
                poseStack.translate(drawX, drawY, 0);
                poseStack.scale(totalScale, totalScale, 1.0f);
                poseStack.translate(-drawX, -drawY, 0);

                float nameScaledX = drawX + (nameScreenX - drawX) * invScale;
                float distScaledX = drawX + (distScreenX - drawX) * invScale;
                float nameScaledY = drawY + (nameScreenY - drawY) * invScale;
                float distScaledY = drawY + (distScreenY - drawY) * invScale;

                float nameFracX = nameScaledX - (float) Math.floor(nameScaledX);
                float nameFracY = nameScaledY - (float) Math.floor(nameScaledY);
                float distFracX = distScaledX - (float) Math.floor(distScaledX);
                float distFracY = distScaledY - (float) Math.floor(distScaledY);

                poseStack.pushPose();
                poseStack.translate(nameFracX, nameFracY, 0);
                graphics.drawString(font, nameText,
                        (int) Math.floor(nameScaledX),
                        (int) Math.floor(nameScaledY),
                        nameColor);
                poseStack.popPose();

                poseStack.pushPose();
                poseStack.translate(distFracX, distFracY, 0);
                graphics.drawString(font, distText,
                        (int) Math.floor(distScaledX),
                        (int) Math.floor(distScaledY),
                        distColor);
                poseStack.popPose();

                poseStack.popPose();
            } else {
                int nameXI = (int) Math.floor(nameScreenX);
                int nameYI = (int) Math.floor(nameScreenY);
                float nameFracX = nameScreenX - nameXI;
                float nameFracY = nameScreenY - nameYI;

                int distXI = (int) Math.floor(distScreenX);
                int distYI = (int) Math.floor(distScreenY);
                float distFracX = distScreenX - distXI;
                float distFracY = distScreenY - distYI;

                PoseStack ps = graphics.pose();
                ps.pushPose();
                ps.translate(nameFracX, nameFracY, 0);
                graphics.drawString(font, nameText, nameXI, nameYI, nameColor);
                ps.popPose();

                ps.pushPose();
                ps.translate(distFracX, distFracY, 0);
                graphics.drawString(font, distText, distXI, distYI, distColor);
                ps.popPose();
            }

            arrowAnimations.remove(wp.getId());
        }

        for (Map.Entry<String, DismissAnimation> entry : new ArrayList<>(dismissAnimations.entrySet())) {
            DismissAnimation dismissAnim = entry.getValue();
            Waypoint wp = dismissAnim.waypoint;
            Vec3 rel = new Vec3(wp.getX() - camPos.x, wp.getY() - camPos.y, wp.getZ() - camPos.z);
            double camXD = -(rel.x * leftVec.x() + rel.y * leftVec.y() + rel.z * leftVec.z());
            double camYD = rel.x * upVec.x() + rel.y * upVec.y() + rel.z * upVec.z();
            double camZD = rel.x * lookVec.x() + rel.y * lookVec.y() + rel.z * lookVec.z();

            float drawX, drawY;
            if (camZD > 0.05) {
                double sx = (camXD / camZD) / (tanHalfFov * aspectRatio);
                double sy = -(camYD / camZD) / tanHalfFov;
                drawX = (float) ((sx + 1.0) / 2.0 * screenW);
                drawY = (float) ((sy + 1.0) / 2.0 * screenH);
                dismissAnim.lastScreenX = drawX;
                dismissAnim.lastScreenY = drawY;
                dismissAnim.hasLastScreen = true;
            } else if (dismissAnim.hasLastScreen) {
                drawX = dismissAnim.lastScreenX;
                drawY = dismissAnim.lastScreenY;
            } else {
                drawX = (float) screenW / 2.0f;
                drawY = (float) screenH / 2.0f;
            }
            float floatOffset = (float) (Math.sin(smoothTime / FLOAT_PERIOD * Math.PI * 2.0) * FLOAT_AMPLITUDE);
            renderDismissAnimation(graphics, dismissAnim, smoothTime, font, wp, drawX, drawY + floatOffset);
        }

        for (String id : currentOffScreen) {
            if (!lastOffScreen.contains(id)) {
                arrowAnimations.put(id, 0.0f);
            }
            float anim = arrowAnimations.getOrDefault(id, 0.0f);
            anim += (1.0f - anim) * ARROW_ANIM_SPEED;
            if (anim > 0.99f) anim = 1.0f;
            arrowAnimations.put(id, anim);
        }
        arrowAnimations.keySet().removeIf(id -> !currentOffScreen.contains(id));
        lastOffScreen.clear();
        lastOffScreen.addAll(currentOffScreen);
    }

    private static void drawDiamond(GuiGraphics graphics, float cx, float cy, float halfSize, int color) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(cx, cy, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(45));

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int glowAlpha = Math.min(a * 2, 255);
        int glowColor = (glowAlpha / 4) << 24 | (r << 16) | (g << 8) | b;

        float halfSide = halfSize / 1.414f;
        float glowExtra = halfSize * 0.4f / 1.414f;

        float totalHalf = halfSide + glowExtra;

        float glowScale = totalHalf / 4.0f;
        poseStack.pushPose();
        poseStack.scale(glowScale, glowScale, 1.0f);
        graphics.fill(-4, -4, 4, 4, glowColor);
        poseStack.popPose();

        float mainScale = halfSide / 4.0f;
        poseStack.pushPose();
        poseStack.scale(mainScale, mainScale, 1.0f);
        graphics.fill(-4, -4, 4, 4, color);
        poseStack.popPose();

        float innerSize = halfSide * 0.35f;
        if (innerSize > 0.5f) {
            float innerScale = innerSize / 2.0f;
            poseStack.pushPose();
            poseStack.scale(innerScale, innerScale, 1.0f);
            graphics.fill(-2, -2, 2, 2, 0xFFFFFFFF);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (color >>> 24) & 0xFF;
        int fadedA = (int) (a * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (fadedA << 24) | (color & 0x00FFFFFF);
    }

    private static void drawOffScreenArrow(GuiGraphics graphics, int screenW, int screenH,
                                           float centerX, float centerY,
                                           double camX, double camY,
                                           Waypoint wp, double distance, float progress) {
        double dirX = camX;
        double dirY = -camY;

        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1e-6) return;

        dirX /= len;
        dirY /= len;

        float margin = EDGE_MARGIN;
        float left = margin;
        float right = screenW - margin;
        float top = margin;
        float bottom = screenH - margin;

        float tMin = Float.MAX_VALUE;
        float t = (left - centerX) / (float) dirX;
        if (t > 0) {
            float iy = centerY + t * (float) dirY;
            if (iy >= top && iy <= bottom) tMin = Math.min(tMin, t);
        }
        t = (right - centerX) / (float) dirX;
        if (t > 0) {
            float iy = centerY + t * (float) dirY;
            if (iy >= top && iy <= bottom) tMin = Math.min(tMin, t);
        }
        t = (top - centerY) / (float) dirY;
        if (t > 0) {
            float ix = centerX + t * (float) dirX;
            if (ix >= left && ix <= right) tMin = Math.min(tMin, t);
        }
        t = (bottom - centerY) / (float) dirY;
        if (t > 0) {
            float ix = centerX + t * (float) dirX;
            if (ix >= left && ix <= right) tMin = Math.min(tMin, t);
        }

        if (tMin == Float.MAX_VALUE) return;

        float arrowX = centerX + tMin * (float) dirX;
        float arrowY = centerY + tMin * (float) dirY;

        float angle = (float) Math.atan2(dirY, dirX);

        float arrowAnim = arrowAnimations.getOrDefault(wp.getId(), 1.0f);
        drawArrow(graphics, arrowX, arrowY, angle, wp.getColor(), arrowAnim * progress);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String namePart = wp.getName();
        String distPart = String.format(" %.0fm", distance);
        int nameW = font.width(namePart);
        int distW = font.width(distPart);
        int totalW = nameW + distW;

        float textX = arrowX - totalW / 2.0f;
        float textY = arrowY - 16.0f;

        textX = Math.max(2, Math.min(screenW - totalW - 2, textX));
        textY = Math.max(2, Math.min(screenH - 12, textY));

        graphics.drawString(font, namePart, (int) textX, (int) textY, applyAlpha(0xFFFFFFFF, progress));
        graphics.drawString(font, distPart, (int) (textX + nameW), (int) textY, applyAlpha(0xBBBBBBBB, progress));
    }

    private static void drawArrow(GuiGraphics graphics, float x, float y, float angle, int color, float anim) {
        if (anim <= 0.0f) return;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.mulPose(Axis.ZP.rotation(angle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(45));

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int animA = (int)(a * anim);
        int animColor = (animA << 24) | (r << 16) | (g << 8) | b;

        int glowAlpha = Math.min(animA * 2, 255);
        int glowColor = (glowAlpha / 4) << 24 | (r << 16) | (g << 8) | b;

        float halfSize = ARROW_SIZE * 0.5f * anim;
        float halfSide = halfSize / 1.414f;
        float glowExtra = halfSize * 0.4f / 1.414f;
        float totalHalf = halfSide + glowExtra;

        float glowScale = totalHalf / 4.0f;
        poseStack.pushPose();
        poseStack.scale(glowScale, glowScale, 1.0f);
        graphics.fill(-4, -4, 4, 4, glowColor);
        poseStack.popPose();

        float mainScale = halfSide / 4.0f;
        poseStack.pushPose();
        poseStack.scale(mainScale, mainScale, 1.0f);
        graphics.fill(-4, -4, 4, 4, animColor);
        poseStack.popPose();

        float innerSize = halfSide * 0.35f;
        if (innerSize > 0.5f) {
            float innerScale = innerSize / 2.0f;
            poseStack.pushPose();
            poseStack.scale(innerScale, innerScale, 1.0f);
            graphics.fill(-2, -2, 2, 2, 0xFFFFFFFF);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void renderDismissAnimation(GuiGraphics graphics, DismissAnimation anim, float currentTime, Font font, Waypoint wp, float drawX, float drawY) {
        float elapsed = currentTime - anim.startTime;
        float t = Math.min(elapsed / 20.0f / DISMISS_DURATION, 1.0f);

        float scale;
        float alpha;
        if (t < DISMISS_GROW_PHASE) {
            float p = t / DISMISS_GROW_PHASE;
            float easeOut = 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
            scale = 1.0f + (DISMISS_MAX_SCALE - 1.0f) * easeOut;
            alpha = 1.0f;
        } else {
            float p = (t - DISMISS_GROW_PHASE) / (1.0f - DISMISS_GROW_PHASE);
            float easeIn = p * p * p;
            scale = DISMISS_MAX_SCALE * (1.0f - easeIn);
            alpha = 1.0f - p * p;
        }

        if (t >= 1.0f) {
            if (!anim.particlesSpawned) {
                spawnDismissParticles(anim.waypoint);
                anim.particlesSpawned = true;
            }
            dismissAnimations.remove(anim.waypoint.getId());
            return;
        }

        int color = wp.getColor();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int fadedColor = ((int)(a * alpha) << 24) | (r << 16) | (g << 8) | b;

        int nameColor = wp.getNameColor();
        int nameA = (nameColor >> 24) & 0xFF;
        int nameR = (nameColor >> 16) & 0xFF;
        int nameG = (nameColor >> 8) & 0xFF;
        int nameB = nameColor & 0xFF;
        int fadedNameColor = ((int)(nameA * alpha) << 24) | (nameR << 16) | (nameG << 8) | nameB;

        int fadedDistColor = ((int)(0xBB * alpha) << 24) | (0xBB << 16) | (0xBB << 8) | 0xBB;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(drawX, drawY, 0);
        poseStack.scale(scale, scale, 1.0f);
        poseStack.translate(-drawX, -drawY, 0);

        float halfSize = DIAMOND_SIZE_CLOSE / 2.0f;
        drawDiamond(graphics, drawX, drawY, halfSize, fadedColor);

        String nameText = wp.getName();
        String distText = String.format("%.0fm", anim.distance);

        int nameWidth = font.width(nameText);
        int distWidth = font.width(distText);

        int nameGap = 12;
        int distGap = 4;

        float nameScreenX = drawX - nameWidth / 2.0f;
        float distScreenX = drawX - distWidth / 2.0f;
        float nameScreenY = drawY - halfSize - nameGap;
        float distScreenY = drawY + halfSize + distGap;

        int nameXI = (int) Math.floor(nameScreenX);
        int nameYI = (int) Math.floor(nameScreenY);
        float nameFracX = nameScreenX - nameXI;
        float nameFracY = nameScreenY - nameYI;

        int distXI = (int) Math.floor(distScreenX);
        int distYI = (int) Math.floor(distScreenY);
        float distFracX = distScreenX - distXI;
        float distFracY = distScreenY - distYI;

        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(nameFracX, nameFracY, 0);
        graphics.drawString(font, nameText, nameXI, nameYI, fadedNameColor);
        ps.popPose();

        ps.pushPose();
        ps.translate(distFracX, distFracY, 0);
        graphics.drawString(font, distText, distXI, distYI, fadedDistColor);
        ps.popPose();

        poseStack.popPose();
    }

    private static void spawnDismissParticles(Waypoint wp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i = 0; i < 15; i++) {
            double px = wp.getX() + (Math.random() - 0.5) * 0.5;
            double py = wp.getY() + (Math.random() - 0.5) * 0.5;
            double pz = wp.getZ() + (Math.random() - 0.5) * 0.5;
            double vx = (Math.random() - 0.5) * 0.1;
            double vy = Math.random() * 0.15;
            double vz = (Math.random() - 0.5) * 0.1;
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
        }
    }

    public static boolean handleRightClickCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (mc.screen != null) return false;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        for (String wpId : currentCrosshairWps) {
            if (dismissAnimations.containsKey(wpId)) continue;

            Waypoint wp = findWaypoint(wpId);
            if (wp == null || !wp.isInteractive()) continue;

            Vec3 rel = new Vec3(wp.getX() - camPos.x, wp.getY() - camPos.y, wp.getZ() - camPos.z);
            double distance = rel.length();

            if (distance > DISMISS_CLICK_RANGE) continue;

            if (dismissRequestCallback != null) {
                dismissRequestCallback.accept(wpId);
                return true;
            }
        }
        return false;
    }

    private static void handleWaypointsRemoved(List<Waypoint> removed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long gameTime = mc.level.getGameTime();
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        for (Waypoint wp : removed) {
            Vec3 rel = new Vec3(wp.getX() - camPos.x, wp.getY() - camPos.y, wp.getZ() - camPos.z);
            float distance = (float) rel.length();
            dismissAnimations.put(wp.getId(), new DismissAnimation(wp, gameTime, distance));
        }
    }

    private static Waypoint findWaypoint(String id) {
        for (Waypoint wp : ClientWaypointData.getWaypoints()) {
            if (wp.getId().equals(id)) return wp;
        }
        return null;
    }
}
