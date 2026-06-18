package com.lowdragmc.kilagraphdemo.slideshow;

/**
 * Duck-typing interface mixed into SlideShow's {@code ProjectorBlockEntity} to carry the uid of the
 * KilaGraph {@link SlideShowGraph} work this projector renders through. Defined here (no SlideShow imports)
 * so common/server code (e.g. the {@code C2SSetProjectorGraph} packet) can read/write it by casting the
 * block entity, without depending on the SlideShow mod being present at compile or run time.
 *
 * <p>An empty uid means "use SlideShow's vanilla render type" (no graph override).</p>
 */
public interface IProjectorGraphHolder {

    String kilagraphdemo$getGraphWorkUid();

    /** Server-side: set the uid and persist + sync it to tracking clients. */
    void kilagraphdemo$setGraphWorkUid(String uid);
}
