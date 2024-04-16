package games.rednblack.h2d.extension.bvb;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.NumberUtils;
import com.badlogic.gdx.utils.ShortArray;
import com.esotericsoftware.spine.BlendMode;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.*;
import com.esotericsoftware.spine.utils.SkeletonClipping;
import com.esotericsoftware.spine.utils.TwoColorPolygonBatch;
import games.rednblack.h2d.extension.spine.HyperBlendMode;
import games.rednblack.h2d.extension.spine.SlotRange;
import games.rednblack.talos.runtime.ParticleEffectInstance;
import games.rednblack.talos.runtime.bvb.BoundEffect;
import games.rednblack.talos.runtime.bvb.SkeletonContainer;
import games.rednblack.talos.runtime.render.ParticleRenderer;

public class BVBSkeletonRenderSeparator {
    static private final short[] quadTriangles = {0, 1, 2, 2, 3, 0};

    private boolean pmaColors, pmaBlendModes;
    private final FloatArray vertices = new FloatArray(32);
    private final SkeletonClipping clipper = new SkeletonClipping();

    /** Renders the specified skeleton. If the batch is a PolygonSpriteBatch, {@link #draw(ParticleRenderer, PolygonSpriteBatch, SkeletonContainer, Skeleton, SlotRange)} is
     * called. If the batch is a TwoColorPolygonBatch, {@link #draw(ParticleRenderer, TwoColorPolygonBatch, SkeletonContainer, Skeleton, SlotRange)} is called. Otherwise the
     * skeleton is rendered without two color tinting and any mesh attachments will throw an exception.
     * <p>
     * This method may change the batch's {@link Batch#setBlendFunctionSeparate(int, int, int, int) blending function}. The
     * previous blend function is not restored, since that could result in unnecessary flushes, depending on what is rendered
     * next. */
    public void draw (ParticleRenderer particleRenderer, SkeletonContainer skeletonContainer, Skeleton skeleton, SlotRange slotRange) {
        if (particleRenderer == null) throw new IllegalArgumentException("ParticleRenderer cannot be null.");
        Batch batch = particleRenderer.getBatch();
        if (batch instanceof TwoColorPolygonBatch) {
            draw(particleRenderer, (TwoColorPolygonBatch) batch, skeletonContainer, skeleton, slotRange);
            return;
        }
        if (batch instanceof PolygonSpriteBatch) {
            draw(particleRenderer, (PolygonSpriteBatch) batch, skeletonContainer, skeleton, slotRange);
            return;
        }
        if (batch == null) throw new IllegalArgumentException("batch cannot be null.");
        if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");
        if (skeletonContainer == null) throw new IllegalArgumentException("skeleton container cannot be null.");

        boolean pmaColors = this.pmaColors, pmaBlendModes = this.pmaBlendModes;
        BlendMode blendMode = null;
        float[] vertices = this.vertices.items;
        Color skeletonColor = skeleton.getColor();
        float r = skeletonColor.r, g = skeletonColor.g, b = skeletonColor.b, a = skeletonColor.a;
        Object[] drawOrder = skeleton.getDrawOrder().items;
        for (int i = slotRange.start, n = slotRange.end; i < n; i++) {
            Slot slot = (Slot)drawOrder[i];
            if (!slot.getBone().isActive()) continue;

            BoundEffect boundEffect = skeletonContainer.findEffect(slot);
            if (boundEffect != null && boundEffect.isNested() && boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }

            Attachment attachment = slot.getAttachment();
            if (attachment instanceof RegionAttachment) {
                RegionAttachment region = (RegionAttachment)attachment;
                region.computeWorldVertices(slot, vertices, 0, 5);
                Color color = region.getColor(), slotColor = slot.getColor();
                float alpha = a * slotColor.a * color.a * 255;
                float multiplier = pmaColors ? alpha : 255;

                BlendMode slotBlendMode = slot.getData().getBlendMode();
                if (slotBlendMode != blendMode) {
                    if (slotBlendMode == BlendMode.additive && pmaColors) {
                        slotBlendMode = BlendMode.normal;
                        alpha = 0;
                    }
                    blendMode = slotBlendMode;
                    HyperBlendMode.apply(blendMode, batch, pmaBlendModes);
                }

                float c = NumberUtils.intToFloatColor((int)alpha << 24 //
                        | (int)(b * slotColor.b * color.b * multiplier) << 16 //
                        | (int)(g * slotColor.g * color.g * multiplier) << 8 //
                        | (int)(r * slotColor.r * color.r * multiplier));
                float[] uvs = region.getUVs();
                for (int u = 0, v = 2; u < 8; u += 2, v += 5) {
                    vertices[v] = c;
                    vertices[v + 1] = uvs[u];
                    vertices[v + 2] = uvs[u + 1];
                }

                batch.draw(region.getRegion().getTexture(), vertices, 0, 20);

            } else if (attachment instanceof ClippingAttachment) {
                throw new RuntimeException(batch.getClass().getSimpleName()
                        + " cannot perform clipping, PolygonSpriteBatch or TwoColorPolygonBatch is required.");

            } else if (attachment instanceof MeshAttachment) {
                throw new RuntimeException(batch.getClass().getSimpleName()
                        + " cannot render meshes, PolygonSpriteBatch or TwoColorPolygonBatch is required.");

            } else if (attachment instanceof SkeletonAttachment) {
                Skeleton attachmentSkeleton = ((SkeletonAttachment)attachment).getSkeleton();
                if (attachmentSkeleton != null) draw(particleRenderer, skeletonContainer, attachmentSkeleton, slotRange);
            }

            if (boundEffect != null && boundEffect.isNested() && !boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }
        }
    }

    /** Renders the specified skeleton, including meshes, but without two color tinting.
     * <p>
     * This method may change the batch's {@link Batch#setBlendFunctionSeparate(int, int, int, int) blending function}. The
     * previous blend function is not restored, since that could result in unnecessary flushes, depending on what is rendered
     * next. */
    public void draw (ParticleRenderer particleRenderer, PolygonSpriteBatch batch, SkeletonContainer skeletonContainer, Skeleton skeleton, SlotRange slotRange) {
        if (batch == null) throw new IllegalArgumentException("batch cannot be null.");
        if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");
        if (skeletonContainer == null) throw new IllegalArgumentException("skeleton container cannot be null.");

        boolean pmaColors = this.pmaColors, pmaBlendModes = this.pmaBlendModes;
        BlendMode blendMode = null;
        int verticesLength = 0;
        float[] vertices = null, uvs = null;
        short[] triangles = null;
        Color color = null, skeletonColor = skeleton.getColor();
        float r = skeletonColor.r, g = skeletonColor.g, b = skeletonColor.b, a = skeletonColor.a;
        Object[] drawOrder = skeleton.getDrawOrder().items;
        for (int i = slotRange.start, n = slotRange.end; i < n; i++) {
            Slot slot = (Slot)drawOrder[i];
            if (!slot.getBone().isActive()) {
                clipper.clipEnd(slot);
                continue;
            }

            BoundEffect boundEffect = skeletonContainer.findEffect(slot);
            if (boundEffect != null && boundEffect.isNested() && boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }

            Texture texture = null;
            int vertexSize = clipper.isClipping() ? 2 : 5;
            Attachment attachment = slot.getAttachment();
            if (attachment instanceof RegionAttachment) {
                RegionAttachment region = (RegionAttachment)attachment;
                verticesLength = vertexSize << 2;
                vertices = this.vertices.items;
                region.computeWorldVertices(slot, vertices, 0, vertexSize);
                triangles = quadTriangles;
                texture = region.getRegion().getTexture();
                uvs = region.getUVs();
                color = region.getColor();

            } else if (attachment instanceof MeshAttachment) {
                MeshAttachment mesh = (MeshAttachment)attachment;
                int count = mesh.getWorldVerticesLength();
                verticesLength = (count >> 1) * vertexSize;
                vertices = this.vertices.setSize(verticesLength);
                mesh.computeWorldVertices(slot, 0, count, vertices, 0, vertexSize);
                triangles = mesh.getTriangles();
                texture = mesh.getRegion().getTexture();
                uvs = mesh.getUVs();
                color = mesh.getColor();

            } else if (attachment instanceof ClippingAttachment) {
                ClippingAttachment clip = (ClippingAttachment)attachment;
                clipper.clipStart(slot, clip);
                continue;

            } else if (attachment instanceof SkeletonAttachment) {
                Skeleton attachmentSkeleton = ((SkeletonAttachment)attachment).getSkeleton();
                if (attachmentSkeleton != null) draw(particleRenderer, batch, skeletonContainer, attachmentSkeleton, slotRange);
            }

            if (texture != null) {
                Color slotColor = slot.getColor();
                float alpha = a * slotColor.a * color.a * 255;
                float multiplier = pmaColors ? alpha : 255;

                BlendMode slotBlendMode = slot.getData().getBlendMode();
                if (slotBlendMode != blendMode) {
                    if (slotBlendMode == BlendMode.additive && pmaColors) {
                        slotBlendMode = BlendMode.normal;
                        alpha = 0;
                    }
                    blendMode = slotBlendMode;
                    HyperBlendMode.apply(blendMode, batch, pmaBlendModes);
                }

                float c = NumberUtils.intToFloatColor((int)alpha << 24 //
                        | (int)(b * slotColor.b * color.b * multiplier) << 16 //
                        | (int)(g * slotColor.g * color.g * multiplier) << 8 //
                        | (int)(r * slotColor.r * color.r * multiplier));

                if (clipper.isClipping()) {
                    clipper.clipTriangles(vertices, verticesLength, triangles, triangles.length, uvs, c, 0, false);
                    FloatArray clippedVertices = clipper.getClippedVertices();
                    ShortArray clippedTriangles = clipper.getClippedTriangles();
                    batch.draw(texture, clippedVertices.items, 0, clippedVertices.size, clippedTriangles.items, 0,
                            clippedTriangles.size);
                } else {
                    for (int v = 2, u = 0; v < verticesLength; v += 5, u += 2) {
                        vertices[v] = c;
                        vertices[v + 1] = uvs[u];
                        vertices[v + 2] = uvs[u + 1];
                    }
                    batch.draw(texture, vertices, 0, verticesLength, triangles, 0, triangles.length);
                }
            }

            clipper.clipEnd(slot);

            if (boundEffect != null && boundEffect.isNested() && !boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }
        }
        clipper.clipEnd();
    }

    /** Renders the specified skeleton, including meshes and two color tinting.
     * <p>
     * This method may change the batch's {@link Batch#setBlendFunctionSeparate(int, int, int, int) blending function}. The
     * previous blend function is not restored, since that could result in unnecessary flushes, depending on what is rendered
     * next. */
    public void draw (ParticleRenderer particleRenderer, TwoColorPolygonBatch batch, SkeletonContainer skeletonContainer, Skeleton skeleton, SlotRange slotRange) {
        if (batch == null) throw new IllegalArgumentException("batch cannot be null.");
        if (skeleton == null) throw new IllegalArgumentException("skeleton cannot be null.");
        if (skeletonContainer == null) throw new IllegalArgumentException("skeleton container cannot be null.");

        boolean pmaColors = this.pmaColors, pmaBlendModes = this.pmaBlendModes;
        batch.setPremultipliedAlpha(pmaColors);
        BlendMode blendMode = null;
        int verticesLength = 0;
        float[] vertices = null, uvs = null;
        short[] triangles = null;
        Color color = null, skeletonColor = skeleton.getColor();
        float r = skeletonColor.r, g = skeletonColor.g, b = skeletonColor.b, a = skeletonColor.a;
        Object[] drawOrder = skeleton.getDrawOrder().items;
        for (int i = slotRange.start, n = slotRange.end; i < n; i++) {
            Slot slot = (Slot)drawOrder[i];
            if (!slot.getBone().isActive()) {
                clipper.clipEnd(slot);
                continue;
            }

            BoundEffect boundEffect = skeletonContainer.findEffect(slot);
            if (boundEffect != null && boundEffect.isNested() && boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }

            Texture texture = null;
            int vertexSize = clipper.isClipping() ? 2 : 6;
            Attachment attachment = slot.getAttachment();
            if (attachment instanceof RegionAttachment) {
                RegionAttachment region = (RegionAttachment)attachment;
                verticesLength = vertexSize << 2;
                vertices = this.vertices.items;
                region.computeWorldVertices(slot, vertices, 0, vertexSize);
                triangles = quadTriangles;
                texture = region.getRegion().getTexture();
                uvs = region.getUVs();
                color = region.getColor();

            } else if (attachment instanceof MeshAttachment) {
                MeshAttachment mesh = (MeshAttachment)attachment;
                int count = mesh.getWorldVerticesLength();
                verticesLength = (count >> 1) * vertexSize;
                vertices = this.vertices.setSize(verticesLength);
                mesh.computeWorldVertices(slot, 0, count, vertices, 0, vertexSize);
                triangles = mesh.getTriangles();
                texture = mesh.getRegion().getTexture();
                uvs = mesh.getUVs();
                color = mesh.getColor();

            } else if (attachment instanceof ClippingAttachment) {
                ClippingAttachment clip = (ClippingAttachment)attachment;
                clipper.clipStart(slot, clip);
                continue;

            } else if (attachment instanceof SkeletonAttachment) {
                Skeleton attachmentSkeleton = ((SkeletonAttachment)attachment).getSkeleton();
                if (attachmentSkeleton != null) draw(particleRenderer, batch, skeletonContainer, attachmentSkeleton, slotRange);
            }

            if (texture != null) {
                Color lightColor = slot.getColor();
                float alpha = a * lightColor.a * color.a * 255;
                float multiplier = pmaColors ? alpha : 255;

                BlendMode slotBlendMode = slot.getData().getBlendMode();
                if (slotBlendMode != blendMode) {
                    if (slotBlendMode == BlendMode.additive && pmaColors) {
                        slotBlendMode = BlendMode.normal;
                        alpha = 0;
                    }
                    blendMode = slotBlendMode;
                    HyperBlendMode.apply(blendMode, batch, pmaBlendModes);
                }

                float red = r * color.r * multiplier;
                float green = g * color.g * multiplier;
                float blue = b * color.b * multiplier;
                float light = NumberUtils.intToFloatColor((int)alpha << 24 //
                        | (int)(blue * lightColor.b) << 16 //
                        | (int)(green * lightColor.g) << 8 //
                        | (int)(red * lightColor.r));
                Color darkColor = slot.getDarkColor();
                float dark = darkColor == null ? 0
                        : NumberUtils.intToFloatColor((int)(blue * darkColor.b) << 16 //
                        | (int)(green * darkColor.g) << 8 //
                        | (int)(red * darkColor.r));

                if (clipper.isClipping()) {
                    clipper.clipTriangles(vertices, verticesLength, triangles, triangles.length, uvs, light, dark, true);
                    FloatArray clippedVertices = clipper.getClippedVertices();
                    ShortArray clippedTriangles = clipper.getClippedTriangles();
                    batch.drawTwoColor(texture, clippedVertices.items, 0, clippedVertices.size, clippedTriangles.items, 0,
                            clippedTriangles.size);
                } else {
                    for (int v = 2, u = 0; v < verticesLength; v += 6, u += 2) {
                        vertices[v] = light;
                        vertices[v + 1] = dark;
                        vertices[v + 2] = uvs[u];
                        vertices[v + 3] = uvs[u + 1];
                    }
                    batch.drawTwoColor(texture, vertices, 0, verticesLength, triangles, 0, triangles.length);
                }
            }

            clipper.clipEnd(slot);

            if (boundEffect != null && boundEffect.isNested() && !boundEffect.isBehind()) {
                for (ParticleEffectInstance particleEffectInstance : boundEffect.getParticleEffects()) {
                    particleRenderer.render(particleEffectInstance);
                }
            }
        }
        clipper.clipEnd();
    }

    public boolean getPremultipliedAlphaColors () {
        return pmaColors;
    }

    /** If true, colors will be multiplied by their alpha before being sent to the GPU. Set to false if premultiplied alpha is not
     * being used or if the shader does the multiplication (libgdx's default batch shaders do not). Default is false. */
    public void setPremultipliedAlphaColors (boolean pmaColors) {
        this.pmaColors = pmaColors;
    }

    public boolean getPremultipliedAlphaBlendModes () {
        return pmaBlendModes;
    }

    /** If true, blend modes for premultiplied alpha will be used. Set to false if premultiplied alpha is not being used. Default
     * is false. */
    public void setPremultipliedAlphaBlendModes (boolean pmaBlendModes) {
        this.pmaBlendModes = pmaBlendModes;
    }

    /** Sets {@link #setPremultipliedAlphaColors(boolean)} and {@link #setPremultipliedAlphaBlendModes(boolean)}. */
    public void setPremultipliedAlpha (boolean pmaColorsAndBlendModes) {
        pmaColors = pmaColorsAndBlendModes;
        pmaBlendModes = pmaColorsAndBlendModes;
    }
}
