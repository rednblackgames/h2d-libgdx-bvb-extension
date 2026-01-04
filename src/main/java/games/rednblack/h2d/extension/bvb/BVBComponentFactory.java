package games.rednblack.h2d.extension.bvb;

import games.rednblack.editor.renderer.ecs.ComponentMapper;
import games.rednblack.editor.renderer.ecs.EntityTransmuterFactory;
import games.rednblack.editor.renderer.ecs.Engine;
import games.rednblack.editor.renderer.lights.RayHandler;
import games.rednblack.editor.renderer.components.normal.NormalMapRendering;
import games.rednblack.editor.renderer.resources.IResourceRetriever;
import games.rednblack.h2d.extension.spine.SpineComponent;
import games.rednblack.h2d.extension.spine.SpineComponentFactory;

public class BVBComponentFactory extends SpineComponentFactory {
    protected ComponentMapper<BVBComponent> bvbCM;
    protected ResourceManagerParticlePoolProvider resourceManagerParticlePoolProvider;

    @Override
    public void injectDependencies(Engine engine, RayHandler rayHandler, com.badlogic.gdx.physics.box2d.World world, IResourceRetriever rm) {
        super.injectDependencies(engine, rayHandler, world, rm);

        transmuter = new EntityTransmuterFactory(engine)
                .add(SpineComponent.class)
                .add(NormalMapRendering.class)
                .add(BVBComponent.class)
                .build();

        resourceManagerParticlePoolProvider = new ResourceManagerParticlePoolProvider(rm);
    }

    @Override
    protected void initializeTransientComponents(int entity) {
        super.initializeTransientComponents(entity);

        SpineComponent component = spineCM.get(entity);
        BVBDataObject bvbDataObject = (BVBDataObject) rm.getExternalItemType(getEntityType(), component.animationName);
        if (bvbDataObject.bvbData == null) {
            bvbCM.remove(entity);
            return;
        }

        BVBComponent bvbComponent = bvbCM.get(entity);
        bvbComponent.skeletonContainer = new SlotRangeSkeletonContainer(bvbDataObject.bvbData.skeleton);
        bvbComponent.skeletonContainer.setSkeleton(component.skeleton, component.getState(), resourceManagerParticlePoolProvider);
    }
}
