package games.rednblack.h2d.extension.bvb;

import games.rednblack.editor.renderer.ecs.PooledComponent;

public class BVBComponent extends PooledComponent {

    public transient SlotRangeSkeletonContainer skeletonContainer = null;

    @Override
    protected void reset() {
        skeletonContainer = null;
    }
}
