package com.lowdragmc.kilagraphdemo.slideshow.mixin;

import com.lowdragmc.kilagraphdemo.slideshow.IProjectorGraphHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.teacon.slides.block.ProjectorBlockEntity;

/**
 * Adds a single piece of state to SlideShow's projector — the uid of the {@code SlideShowGraph} work it
 * renders through — and makes it persist + sync exactly like the demo's {@code ServerHologramBlockEntity}:
 * written in {@code saveAdditional}/{@code getUpdateTag}, read in {@code loadAdditional}/{@code handleUpdateTag}.
 * An empty uid means "render vanilla" (no override).
 *
 * <p>Declared {@code extends BlockEntity} so the inherited {@code setChanged}/{@code getLevel}/
 * {@code getBlockPos}/{@code getBlockState} are callable from the added setter; the dummy constructor is
 * never applied by Mixin.</p>
 */
@Mixin(ProjectorBlockEntity.class)
public abstract class ProjectorBlockEntityMixin extends BlockEntity implements IProjectorGraphHolder {

    @Unique
    private static final String KILAGRAPHDEMO_KEY = "kilagraphdemo_graph";

    @Unique
    private String kilagraphdemo$graphWorkUid = "";

    private ProjectorBlockEntityMixin() {
        super(null, null, null); // never invoked — required by javac because BlockEntity has no no-arg ctor
    }

    @Override
    public String kilagraphdemo$getGraphWorkUid() {
        return kilagraphdemo$graphWorkUid;
    }

    @Override
    public void kilagraphdemo$setGraphWorkUid(String uid) {
        this.kilagraphdemo$graphWorkUid = uid == null ? "" : uid;
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void kilagraphdemo$save(ValueOutput output, CallbackInfo ci) {
        output.putString(KILAGRAPHDEMO_KEY, kilagraphdemo$graphWorkUid);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void kilagraphdemo$load(ValueInput input, CallbackInfo ci) {
        kilagraphdemo$graphWorkUid = input.getStringOr(KILAGRAPHDEMO_KEY, "");
    }

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void kilagraphdemo$getUpdateTag(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putString(KILAGRAPHDEMO_KEY, kilagraphdemo$graphWorkUid);
    }

    @Inject(method = "handleUpdateTag", at = @At("TAIL"))
    private void kilagraphdemo$handleUpdateTag(ValueInput input, CallbackInfo ci) {
        kilagraphdemo$graphWorkUid = input.getStringOr(KILAGRAPHDEMO_KEY, "");
    }
}
