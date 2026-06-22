package com.lowdragmc.kilagraphdemo.slideshow.client;

import com.lowdragmc.kilagraph.rendertype.compiler.CompiledShaderGraph;
import com.lowdragmc.kilagraph.rendertype.compiler.ShaderGraphCompiler;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeFactory;
import com.lowdragmc.kilagraph.rendertype.runtime.RenderTypeGraphMaterial;
import com.lowdragmc.kilagraphdemo.client.ClientWorks;
import com.lowdragmc.kilagraphdemo.graph.LocalGraphStore;
import com.lowdragmc.kilagraphdemo.graph.WorkMeta;
import com.lowdragmc.kilagraphdemo.graph.WorkPackage;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraph;
import com.lowdragmc.kilagraphdemo.slideshow.SlideShowGraphResource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of compiled {@link SlideShowGraph} materials for projector rendering, plus the
 * auto-download trigger.
 *
 * <p><b>Reuse model.</b> A work's graph is compiled <em>once</em> per uid into a {@link CompiledShaderGraph}
 * (its expensive GLSL/pipeline is shared by content hash inside {@link RenderTypeFactory}); a lightweight
 * {@link RenderTypeGraphMaterial} — its own {@code RenderType} over that shared pipeline — is then created
 * <em>once per (uid, slide texture)</em>. So two projectors showing the same image through the same graph
 * reuse one material/RenderType (and their geometry batches correctly), while different images on the same
 * graph get distinct materials and never alias. The slide texture (stable per provider, even for animated
 * GIF/WebP whose contents update in place) is bound into the material once.</p>
 *
 * <p>All work runs on the render thread (the only caller is {@code TextureSequenceTextureMixin} during the
 * BER submit phase), as {@link RenderTypeFactory} and texture-view creation require.</p>
 */
public final class ClientProjectorGraphs {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per-uid compiled graph (with the work version it came from). {@code compiled == null} = not a
     *  slideshow work, or compilation failed. */
    private record Compiled(int version, @Nullable CompiledShaderGraph compiled) {}

    private static final Map<String, Compiled> COMPILED = new ConcurrentHashMap<>();
    /** uid -> (slide GpuTexture -> material). Inner map is identity-keyed; touched only on the render thread. */
    private static final Map<String, Map<GpuTexture, RenderTypeGraphMaterial>> MATERIALS = new ConcurrentHashMap<>();
    /** uids we've already asked the server for, so we don't spam download requests each frame. */
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    /** NEAREST/CLAMP — matches SlideShow's Sampler0 setup for the slide image. */
    @Nullable
    private static GpuSampler slideSampler;
    /** GpuTexture -> its view (created once; stays valid while the texture lives). Render-thread only. */
    private static final Map<GpuTexture, GpuTextureView> VIEW_CACHE = new IdentityHashMap<>();

    private ClientProjectorGraphs() {}

    /**
     * The material for ({@code uid}, {@code texture}), or {@code null} to render vanilla (work not downloaded
     * yet, not a slideshow work, or failed to compile). Triggers a download on first miss; creates + binds the
     * material lazily.
     */
    @Nullable
    public static RenderTypeGraphMaterial getMaterial(String uid, GpuTexture texture) {
        Compiled compiled = COMPILED.get(uid);
        if (compiled == null) {
            Optional<WorkPackage> loaded = LocalGraphStore.load(uid);
            // Missing, or superseded by a server re-upload — (re-)pull the fresh payload and render vanilla
            // until it lands rather than compiling the stale local copy.
            if (loaded.isEmpty() || ClientWorks.serverVersion(uid) > loaded.get().meta().version()) {
                requestDownload(uid);
                return null;
            }
            WorkPackage pkg = loaded.get();
            compiled = new Compiled(pkg.meta().version(), pkg.meta().isSlideShow() ? compile(pkg) : null);
            COMPILED.put(uid, compiled);
            REQUESTED.remove(uid);
        }
        if (compiled.compiled() == null) return null;

        Map<GpuTexture, RenderTypeGraphMaterial> perTexture =
                MATERIALS.computeIfAbsent(uid, k -> new IdentityHashMap<>());
        RenderTypeGraphMaterial material = perTexture.get(texture);
        if (material == null) {
            material = RenderTypeFactory.createMaterial(compiled.compiled());
            if (material == null) return null; // compile failed on the GPU; retry is cheap (cached as failed)
            bindSlide(material, texture);
            perTexture.put(texture, material);
        }
        return material;
    }

    @Nullable
    private static CompiledShaderGraph compile(WorkPackage pkg) {
        try {
            SlideShowGraph graph = pkg.loadGraph(SlideShowGraphResource.INSTANCE::deserializeGraph);
            return new ShaderGraphCompiler(graph).compile();
        } catch (RuntimeException e) {
            LOGGER.error("[KilaGraphDemo] failed to compile slideshow graph {}", pkg.meta().uid(), e);
            return null;
        }
    }

    /** Bind the slide texture into every custom sampler the material manages (all Sampler0 in a SlideShowGraph). */
    private static void bindSlide(RenderTypeGraphMaterial material, GpuTexture texture) {
        GpuTextureView view = VIEW_CACHE.computeIfAbsent(texture, t -> RenderSystem.getDevice().createTextureView(t));
        GpuSampler sampler = slideSampler();
        for (String name : material.managedSamplerNames()) {
            material.setTextureView(name, view, sampler);
        }
    }

    private static GpuSampler slideSampler() {
        if (slideSampler == null) {
            slideSampler = RenderSystem.getSamplerCache().getSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST, false);
        }
        return slideSampler;
    }

    private static void requestDownload(String uid) {
        if (REQUESTED.add(uid)) {
            ClientWorks.download(uid);
        }
    }

    /** Called when a work file is (re)written locally (download complete): drop the compiled graph + all its
     *  per-image materials so the next render recompiles from the new payload. */
    public static void onWorkSaved(WorkMeta meta) {
        onWorkSaved(meta.uid());
    }

    /** Drop the compiled graph + per-image materials cached for {@code uid} (a download/update landed) so the
     *  next render recompiles from the new payload. */
    public static void onWorkSaved(String uid) {
        COMPILED.remove(uid);
        Map<GpuTexture, RenderTypeGraphMaterial> perTexture = MATERIALS.remove(uid);
        if (perTexture != null) {
            for (RenderTypeGraphMaterial material : perTexture.values()) {
                if (material != null) material.close();
            }
        }
        REQUESTED.remove(uid);
    }
}
