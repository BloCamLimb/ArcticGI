/*
 * The Arctic Graphics Engine.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arctic is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arctic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arctic. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcticgi.engine;

import icyllis.arcticgi.core.RefCnt;
import icyllis.arcticgi.core.SharedPtr;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static icyllis.arcticgi.engine.Engine.*;

/**
 * Represents 2D textures can be sampled by shaders, can also be used as attachments
 * of render targets.
 * <p>
 * By default, a Texture is not renderable (not created with a RenderTarget), all
 * mipmaps (including the base level) are dirty. But it can be promoted to renderable
 * whenever needed (i.e. lazy initialization), then we call it a RenderTexture or
 * TextureRenderTarget. The texture will be the main color buffer of the single
 * sample framebuffer of the render target. So we can cache these framebuffers with
 * texture. With promotion, the scratch key is changed and the sample count (MSAA)
 * is locked. Additionally, it may create more surfaces and attach them to it. These
 * surfaces are budgeted but cannot be reused. In most cases, we reuse textures, so
 * these surfaces are reused together. When renderable is not required, the cache
 * will give priority to the texture without promotion. See {@link RenderTextureProxy}.
 */
public abstract class Texture extends Resource implements Surface {

    protected final int mWidth;
    protected final int mHeight;

    /**
     * Note budgeted is a dynamic state, it can be returned by {@link #getFlags()}.
     * This field is OR-ed only and immutable when created.
     */
    protected int mFlags = SurfaceFlag_BackingFit;

    /**
     * Only valid when isMipmapped=true.
     * By default, we can't say mipmaps dirty or not, since texel data is undefined.
     */
    private boolean mMipmapsDirty;

    @SharedPtr
    private ReleaseCallback mReleaseCallback;

    protected Texture(Server server, int width, int height) {
        super(server);
        assert width > 0 && height > 0;
        mWidth = width;
        mHeight = height;
    }

    /**
     * @return the width of the texture
     */
    @Override
    public final int getWidth() {
        return mWidth;
    }

    /**
     * @return the height of the texture
     */
    @Override
    public final int getHeight() {
        return mHeight;
    }

    /**
     * @return true if this surface has mipmaps and have been allocated
     */
    public final boolean isMipmapped() {
        return (mFlags & SurfaceFlag_Mipmapped) != 0;
    }

    /**
     * The pixel values of this surface cannot be modified (e.g. doesn't support write pixels or
     * mipmap regeneration). To be exact, only wrapped textures, external textures, stencil
     * attachments and MSAA color attachments can be read only.
     *
     * @return true if pixels in this surface are read-only
     */
    public final boolean isReadOnly() {
        return (mFlags & SurfaceFlag_ReadOnly) != 0;
    }

    /**
     * @return true if we are working with protected content
     */
    @Override
    public final boolean isProtected() {
        return (mFlags & SurfaceFlag_Protected) != 0;
    }

    /**
     * Surface flags, but no render target level flags.
     *
     * <ul>
     * <li>{@link Engine#SurfaceFlag_Budgeted} -
     *  Indicates whether an allocation should count against a cache budget. Budgeted when
     *  set, otherwise not budgeted. {@link Texture} only.
     * </li>
     *
     * <li>{@link Engine#SurfaceFlag_Mipmapped} -
     *  Used to say whether a texture has mip levels allocated or not. Mipmaps are allocated
     *  when set, otherwise mipmaps are not allocated. {@link Texture} only.
     * </li>
     *
     * <li>{@link Engine#SurfaceFlag_Renderable} -
     *  Used to say whether a surface can be rendered to, whether a texture can be used as
     *  color attachments. Renderable when set, otherwise not renderable.
     * </li>
     *
     * <li>{@link Engine#SurfaceFlag_Protected} -
     *  Used to say whether texture is backed by protected memory. Protected when set, otherwise
     *  not protected.
     * </li>
     *
     * <li>{@link Engine#SurfaceFlag_ReadOnly} -
     *  Means the pixels in the texture are read-only. {@link Texture} only.
     * </li>
     *
     * @return combination of the above flags, always has {@link Engine#SurfaceFlag_BackingFit}
     */
    @Override
    public final int getFlags() {
        int flags = mFlags;
        if (getBudgetType() == BudgetType_Budgeted) {
            flags |= SurfaceFlag_Budgeted;
        }
        return flags;
    }

    /**
     * @return either {@link Engine#TextureType_2D} or {@link Engine#TextureType_External}
     */
    public abstract int getTextureType();

    /**
     * @return the backend texture of this texture
     */
    @Nonnull
    public abstract BackendTexture getBackendTexture();

    /**
     * Return <code>true</code> if mipmaps are dirty and need to regenerate before sampling.
     * The value is valid only when {@link #isMipmapped()} returns <code>true</code>.
     *
     * @return whether mipmaps are dirty
     */
    public final boolean isMipmapsDirty() {
        assert isMipmapped();
        return mMipmapsDirty;
    }

    /**
     * Set whether mipmaps are dirty or not. Call only when {@link #isMipmapped()} returns <code>true</code>.
     *
     * @param mipmapsDirty whether mipmaps are dirty
     */
    public final void setMipmapsDirty(boolean mipmapsDirty) {
        assert isMipmapped();
        mMipmapsDirty = mipmapsDirty;
    }

    public abstract int getMaxMipmapLevel();

    /**
     * Unmanaged backends (e.g. Vulkan) may want to specially handle the release proc in order to
     * ensure it isn't called until GPU work related to the resource is completed.
     */
    public void setReleaseCallback(@SharedPtr ReleaseCallback callback) {
        mReleaseCallback = RefCnt.move(mReleaseCallback, callback);
    }

    @Override
    protected void onFree() {
        if (mReleaseCallback != null) {
            mReleaseCallback.unref();
        }
        mReleaseCallback = null;
    }

    @Override
    protected void onDrop() {
        if (mReleaseCallback != null) {
            mReleaseCallback.unref();
        }
        mReleaseCallback = null;
    }

    @Nullable
    @Override
    protected final ScratchKey computeScratchKey() {
        BackendFormat format = getBackendFormat();
        if (format.isCompressed()) {
            return null;
        }
        return new ScratchKey().compute(
                format,
                mWidth, mHeight,
                mFlags); // budgeted flag is not included, this method is called only when budgeted
    }

    public static long computeSize(BackendFormat format,
                                   int width, int height,
                                   int sampleCount,
                                   boolean mipmapped,
                                   boolean approx) {
        assert width > 0 && height > 0;
        assert sampleCount > 0;
        assert sampleCount == 1 || !mipmapped;
        // For external formats we do not actually know the real size of the resource, so we just return
        // 0 here to indicate this.
        if (format.getTextureType() == TextureType_External) {
            return 0;
        }
        if (approx) {
            width = ResourceProvider.makeApprox(width);
            height = ResourceProvider.makeApprox(height);
        }
        long size = DataUtils.numBlocks(format.getCompressionType(), width, height) *
                format.getBytesPerBlock();
        assert size > 0;
        if (mipmapped) {
            size = (size << 2) / 3;
        } else {
            size *= sampleCount;
        }
        assert size > 0;
        return size;
    }

    public static long computeSize(BackendFormat format,
                                   int width, int height,
                                   int sampleCount,
                                   int levelCount) {
        return computeSize(format, width, height, sampleCount, levelCount, false);
    }

    public static long computeSize(BackendFormat format,
                                   int width, int height,
                                   int sampleCount,
                                   int levelCount,
                                   boolean approx) {
        assert width > 0 && height > 0;
        assert sampleCount > 0 && levelCount > 0;
        assert sampleCount == 1 || levelCount == 1;
        // For external formats we do not actually know the real size of the resource, so we just return
        // 0 here to indicate this.
        if (format.getTextureType() == TextureType_External) {
            return 0;
        }
        if (approx) {
            width = ResourceProvider.makeApprox(width);
            height = ResourceProvider.makeApprox(height);
        }
        long size = DataUtils.numBlocks(format.getCompressionType(), width, height) *
                format.getBytesPerBlock();
        assert size > 0;
        if (levelCount > 1) {
            // geometric sequence, S=a1(1-q^n)/(1-q), q=2^(-2)
            size = ((size - (size >> (levelCount << 1))) << 2) / 3;
        } else {
            size *= sampleCount;
        }
        assert size > 0;
        return size;
    }

    /**
     * Storage key of {@link Texture}, may be compared with {@link TextureProxy}.
     */
    public static final class ScratchKey {

        public int mWidth;
        public int mHeight;
        public int mFormat;
        public int mFlags;

        /**
         * Compute a {@link Texture} key, format can not be compressed.
         *
         * @return the scratch key
         */
        @Nonnull
        public ScratchKey compute(BackendFormat format,
                                  int width, int height,
                                  int surfaceFlags) {
            assert (width > 0 && height > 0);
            assert (!format.isCompressed());
            mWidth = width;
            mHeight = height;
            mFormat = format.getKey();
            mFlags = surfaceFlags & (SurfaceFlag_Mipmapped |
                    SurfaceFlag_Renderable |
                    SurfaceFlag_Protected);
            return this;
        }

        /**
         * Keep {@link TextureProxy#hashCode()} sync with this.
         */
        @Override
        public int hashCode() {
            int result = mWidth;
            result = 31 * result + mHeight;
            result = 31 * result + mFormat;
            result = 31 * result + mFlags;
            return result;
        }

        /**
         * Keep {@link TextureProxy#equals(Object)}} sync with this.
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof ScratchKey key &&
                    mWidth == key.mWidth &&
                    mHeight == key.mHeight &&
                    mFormat == key.mFormat &&
                    mFlags == key.mFlags;
        }
    }
}
