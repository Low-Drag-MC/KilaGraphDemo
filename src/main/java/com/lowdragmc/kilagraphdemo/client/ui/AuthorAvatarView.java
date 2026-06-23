package com.lowdragmc.kilagraphdemo.client.ui;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Renders a player avatar (3D, slow-rotating) for a work's author, inside an LDLib2 {@link Scene}. The
 * skin is resolved from the author's UUID — a default skin shows immediately and is upgraded to the real
 * skin once {@link net.minecraft.client.resources.SkinManager} resolves it. Reuses one avatar entity;
 * selecting a different author just swaps its skin (the {@link PlayerSkin} also carries slim/wide model).
 */
public class AuthorAvatarView extends UIElement {

    @Nullable
    private final Scene scene;
    @Nullable
    private SkinnedMannequin avatar;
    /** The author currently being displayed; async skin callbacks for any other author are discarded. */
    @Nullable
    private UUID pendingAuthor;

    public AuthorAvatarView() {
        getLayout().widthPercent(100).heightPercent(100).alignSelf(AlignItems.CENTER);
        if (Minecraft.getInstance() == null) {
            scene = null;
            return;
        }
        scene = new Scene();
        scene.createScene(new TrackedDummyWorld());
        scene.useOrtho(true);
        scene.setCenter(new Vector3f(0f, 1.45f, 0f)); // frame head + torso (upper body)
        scene.setOrthoRange(0.7f);                    // zoom in on the upper body
        scene.setZoom(1.0f);
        scene.setCameraYawAndPitch(175f, 20f);        // fixed oblique 3/4 angle
        scene.setDraggable(false);
        scene.setScalable(false);
        scene.setIntractable(false);
        scene.getLayout().widthPercent(100).heightPercent(100);
        addChild(scene);
    }

    /** Show the avatar for {@code uuid}/{@code name}; default skin immediately, real skin when resolved. */
    public void setAuthor(UUID uuid, String name) {
        if (scene == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (avatar == null) {
            avatar = new SkinnedMannequin(scene.getDummyWorld(), mc.playerSkinRenderCache());
            avatar.setPos(0, 0, 0);
            // Turn the entity around 180° (not the camera) so the lit front faces the fixed camera.
            avatar.setYRot(180f - 70);
            avatar.setYBodyRot(180f - 70);
            avatar.setYHeadRot(180f - 70);
            avatar.yBodyRotO = 180f - 70;
            avatar.yHeadRotO = 180f - 70;
            scene.getDummyWorld().addEntity(avatar);
        }
        pendingAuthor = uuid;
        avatar.setSkin(DefaultPlayerSkin.get(uuid));
        mc.getSkinManager().get(new GameProfile(uuid, name)).thenAccept(opt ->
                mc.execute(() -> opt.ifPresent(skin -> {
                    // Discard a late callback if a different author has since been selected (race guard).
                    if (avatar != null && uuid.equals(pendingAuthor)) avatar.setSkin(skin);
                })));
    }

    /** A mannequin whose rendered skin we set directly (the avatar renderer reads {@link #getSkin()}). */
    private static final class SkinnedMannequin extends ClientMannequin {
        @Nullable
        private PlayerSkin skin;

        SkinnedMannequin(Level level, PlayerSkinRenderCache cache) {
            super(level, cache);
        }

        void setSkin(PlayerSkin skin) {
            this.skin = skin;
        }

        @Override
        public PlayerSkin getSkin() {
            return skin != null ? skin : super.getSkin();
        }
    }
}
