package com.lowdragmc.kilagraphdemo.client.editor;

import com.lowdragmc.kilagraph.editor.ShaderFunctionGraphResource;
import com.lowdragmc.kilagraph.rendertype.ShaderFunctionGraph;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Client-side store for the player's local Shader-Function graphs (a dedicated folder), shared by the
 * resource panel, the graph reference resolver, and dependency packaging. Backed by a
 * {@link FileResourceProvider} so paths and serialization match what the editor expects.
 */
public final class LocalShaderFunctions {

    private static final File DIR = new File(Minecraft.getInstance().gameDirectory,
            "kilagraphdemo/shaderfunctions");

    private final FileResourceProvider<CompoundTag> provider;

    public LocalShaderFunctions() {
        DIR.mkdirs();
        provider = new FileResourceProvider<>(ShaderFunctionGraphResource.INSTANCE.getResourceInstance(), DIR);
        provider.setName("local");
    }

    public FileResourceProvider<CompoundTag> provider() {
        return provider;
    }

    @Nullable
    public CompoundTag getRawTag(IResourcePath path) {
        return path == null ? null : provider.getResource(path);
    }

    /** Write a raw Shader-Function resource into the local folder (used when caching a downloaded work). */
    public void writeRaw(IResourcePath path, CompoundTag tag) {
        if (path != null && tag != null) provider.addResource(path, tag);
    }

    @Nullable
    public ShaderFunctionGraph deserialize(@Nullable CompoundTag tag) {
        if (tag == null) return null;
        ShaderFunctionGraph graph = ShaderFunctionGraphResource.INSTANCE.createGraph();
        graph.graphModel.deserialize(TagValueInput.create(
                ProblemReporter.Collector.DISCARDING, Platform.getFrozenRegistry(), tag));
        return graph;
    }

    @Nullable
    public ShaderFunctionGraph resolve(IResourcePath path) {
        return deserialize(getRawTag(path));
    }
}
