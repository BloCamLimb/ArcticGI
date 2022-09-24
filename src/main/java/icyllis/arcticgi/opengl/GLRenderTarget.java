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

package icyllis.arcticgi.opengl;

import icyllis.arcticgi.core.SharedPtr;
import icyllis.arcticgi.engine.*;

import javax.annotation.Nonnull;

import static icyllis.arcticgi.opengl.GLCore.*;

/**
 * The base class is only used with wrapped framebuffers, such as the default framebuffer.
 */
public class GLRenderTarget extends RenderTarget {

    protected final GLServer mServer;

    /**
     * The render target format for all color attachments.
     *
     * @see GLTypes#FORMAT_RGBA8
     */
    private final int mFormat;

    // single sample framebuffer, may be resolved by MSAA framebuffer
    private int mFramebuffer;
    private int mResolveFramebuffer;

    // if we need bind stencil buffers on next framebuffer bind call
    private boolean mRebindStencilBuffer;

    // should we delete framebuffers ourselves?
    private final boolean mOwnership;

    private BackendFormat mBackendFormat;
    private BackendRenderTarget mBackendRenderTarget;

    // Constructor for texture render targets.
    protected GLRenderTarget(GLServer server,
                             int width, int height,
                             int format,
                             int sampleCount,
                             int framebuffer,
                             int resolveFramebuffer) {
        super(width, height, sampleCount);
        assert (sampleCount > 0);
        assert ((sampleCount > 1 && framebuffer != resolveFramebuffer) ||
                (sampleCount == 1 && framebuffer == resolveFramebuffer));
        assert (framebuffer != 0 && resolveFramebuffer != 0);
        mServer = server;
        mFormat = format;
        mFramebuffer = framebuffer;
        mResolveFramebuffer = resolveFramebuffer;
        mOwnership = true;
    }

    // Constructor for wrapped render targets.
    private GLRenderTarget(GLServer server,
                           int width, int height,
                           int format,
                           int sampleCount,
                           int framebuffer,
                           boolean ownership,
                           @SharedPtr GLRenderbuffer stencilBuffer) {
        super(width, height, sampleCount);
        assert (sampleCount > 0);
        assert (framebuffer != 0 || !ownership);
        mServer = server;
        mFormat = format;
        mFramebuffer = framebuffer;
        mResolveFramebuffer = framebuffer;
        mOwnership = ownership;
        mStencilBuffer = stencilBuffer; // std::move
        if (framebuffer == 0) {
            mFlags |= EngineTypes.SurfaceFlag_GLWrapDefaultFramebuffer;
        }
    }

    /**
     * Make a {@link GLRenderTarget} that wraps existing framebuffers without
     * accessing their backing buffers (texture and stencil).
     *
     * @param width  the effective width of  framebuffer
     * @param height the effective height of framebuffer
     */
    @Nonnull
    @SharedPtr
    public static GLRenderTarget makeWrapped(GLServer server,
                                             int width, int height,
                                             int format,
                                             int sampleCount,
                                             int framebuffer,
                                             int stencilBits,
                                             boolean ownership) {
        assert (sampleCount > 0);
        assert (framebuffer != 0 || !ownership);
        GLRenderbuffer stencilBuffer = null;
        if (stencilBits > 0) {
            // We pick a "fake" actual format that matches the number of stencil bits. When wrapping
            // an FBO with some number of stencil bits all we care about in the future is that we have
            // a format with the same number of stencil bits. We don't even directly use the format or
            // any other properties. Thus, it is fine for us to just assign an arbitrary format that
            // matches the stencil bit count.
            int stencilFormat = switch (stencilBits) {
                // We pick the packed format here so when we query total size we are at least not
                // underestimating the total size of the stencil buffer. However, in reality this
                // rarely matters since we usually don't care about the size of wrapped objects.
                case 8 -> GLTypes.FORMAT_DEPTH24_STENCIL8;
                case 16 -> GLTypes.FORMAT_STENCIL_INDEX16;
                default -> GLTypes.FORMAT_UNKNOWN;
            };

            // We don't have the actual renderbufferID, but we need to make an attachment for the stencil,
            // so we just set it to an invalid value of 0 to make sure we don't explicitly use it or try
            // and delete it.
            stencilBuffer = GLRenderbuffer.makeWrapped(server,
                    width, height,
                    sampleCount,
                    stencilFormat,
                    0);
        }
        return new GLRenderTarget(server,
                width, height,
                format,
                sampleCount,
                framebuffer,
                ownership,
                stencilBuffer);
    }

    /**
     * The render target format for all color attachments.
     *
     * @see GLTypes#FORMAT_RGBA8
     */
    public int getFormat() {
        return mFormat;
    }

    public int getFramebuffer() {
        return mFramebuffer;
    }

    public int getResolveFramebuffer() {
        return mResolveFramebuffer;
    }

    /**
     * Make sure the stencil attachment is valid. Even though a color buffer op doesn't use stencil,
     * our FBO still needs to be "framebuffer complete". If this render target is already bound,
     * this method ensures the stencil attachment is valid (attached or detached), if stencil buffers
     * reset right before.
     */
    public void bindStencil() {
        if (!mRebindStencilBuffer) {
            return;
        }
        int framebuffer = mFramebuffer;
        GLRenderbuffer stencilBuffer = (GLRenderbuffer) mStencilBuffer;
        if (stencilBuffer != null) {
            glNamedFramebufferRenderbuffer(framebuffer,
                    GL_STENCIL_ATTACHMENT,
                    GL_RENDERBUFFER,
                    stencilBuffer.getRenderbuffer());
            if (glFormatIsPackedDepthStencil(stencilBuffer.getFormat())) {
                glNamedFramebufferRenderbuffer(framebuffer,
                        GL_DEPTH_ATTACHMENT,
                        GL_RENDERBUFFER,
                        stencilBuffer.getRenderbuffer());
            } else {
                glNamedFramebufferRenderbuffer(framebuffer,
                        GL_DEPTH_ATTACHMENT,
                        GL_RENDERBUFFER,
                        0);
            }
        } else {
            glNamedFramebufferRenderbuffer(framebuffer,
                    GL_STENCIL_ATTACHMENT,
                    GL_RENDERBUFFER,
                    0);
            glNamedFramebufferRenderbuffer(framebuffer,
                    GL_DEPTH_ATTACHMENT,
                    GL_RENDERBUFFER,
                    0);
        }
        mRebindStencilBuffer = false;
    }

    @Nonnull
    @Override
    public BackendFormat getBackendFormat() {
        if (mBackendFormat == null) {
            mBackendFormat = GLBackendFormat.make(glFormatToEnum(mFormat), EngineTypes.TextureType_2D);
        }
        return mBackendFormat;
    }

    @Nonnull
    @Override
    public BackendRenderTarget getBackendRenderTarget() {
        if (mBackendRenderTarget == null) {
            final GLFramebufferInfo info = new GLFramebufferInfo();
            info.mFramebuffer = mFramebuffer;
            info.mFormat = glFormatToEnum(mFormat);
            mBackendRenderTarget = new GLBackendRenderTarget(
                    getWidth(), getHeight(), getSampleCount(), getStencilBits(), info);
        }
        return mBackendRenderTarget;
    }

    @Override
    protected boolean canAttachStencil() {
        // Only modify the framebuffer attachments if we have created it.
        // Public APIs do not currently allow for wrap-only ownership,
        // so we can safely assume that if an object is owner, we created it.
        return mOwnership;
    }

    @Override
    protected void attachStencilBuffer(@SharedPtr Surface stencilBuffer) {
        if (stencilBuffer == null && mStencilBuffer == null) {
            // No need to do any work since we currently don't have a stencil attachment,
            // and we're not actually adding one.
            return;
        }

        // We defer attaching the new stencil buffer until the next time our framebuffer is bound.
        if (mStencilBuffer != stencilBuffer) {
            mRebindStencilBuffer = true;
        }

        mStencilBuffer = GpuResource.move(mStencilBuffer, stencilBuffer);
    }

    @Override
    protected void dispose() {
        super.dispose();
        if (mOwnership) {
            if (mFramebuffer != 0) {
                glDeleteFramebuffers(mFramebuffer);
            }
            if (mFramebuffer != mResolveFramebuffer) {
                assert (mResolveFramebuffer != 0);
                glDeleteFramebuffers(mResolveFramebuffer);
            }
        }
        mFramebuffer = 0;
        mResolveFramebuffer = 0;
    }
}
