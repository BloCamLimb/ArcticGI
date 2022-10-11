/*
 * Akashi GI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Akashi GI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Akashi GI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Akashi GI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.akashigi.opengl;

import icyllis.akashigi.core.SharedPtr;
import icyllis.akashigi.engine.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static icyllis.akashigi.opengl.GLCore.*;

/**
 * Renderbuffer can be only used as attachments of framebuffers as an optimization.
 * Renderbuffer can neither be accessed by shaders nor have mipmaps, but can be
 * multisampled.
 */
public final class GLAttachment extends Attachment {

    // may be zero for external stencil buffers associated with external render targets
    // (we don't require the client to give us the id, just tell us how many bits of stencil there are)
    private int mRenderbuffer;

    private final int mFormat;

    private BackendFormat mBackendFormat;

    private final long mMemorySize;

    private GLAttachment(GLServer server, int width, int height,
                         int sampleCount, int format, int renderbuffer) {
        super(server, width, height, sampleCount);
        mRenderbuffer = renderbuffer;
        mFormat = format;

        // color buffers may be compressed
        mMemorySize = DataUtils.numBlocks(glFormatCompressionType(format), width, height) *
                glFormatBytesPerBlock(format) * sampleCount;

        registerWithCache(true);
    }

    @Nullable
    @SharedPtr
    public static GLAttachment makeStencil(GLServer server,
                                           int width, int height,
                                           int sampleCount,
                                           int format) {
        assert sampleCount > 0 && glFormatStencilBits(format) > 0;
        int renderbuffer = glCreateRenderbuffers();
        if (renderbuffer == 0) {
            return null;
        }
        if (server.getCaps().skipErrorChecks()) {
            // GL has a concept of MSAA rasterization with a single sample, but we do not.
            if (sampleCount > 1) {
                glNamedRenderbufferStorageMultisample(renderbuffer, sampleCount, format, width, height);
            } else {
                // glNamedRenderbufferStorage is equivalent to calling glNamedRenderbufferStorageMultisample
                // with the samples set to zero. But we don't think sampleCount=1 is multisampled.
                glNamedRenderbufferStorage(renderbuffer, format, width, height);
            }
        } else {
            glClearErrors();
            if (sampleCount > 1) {
                glNamedRenderbufferStorageMultisample(renderbuffer, sampleCount, format, width, height);
            } else {
                glNamedRenderbufferStorage(renderbuffer, format, width, height);
            }
            if (glGetError() != GL_NO_ERROR) {
                glDeleteRenderbuffers(renderbuffer);
                return null;
            }
        }

        return new GLAttachment(server, width, height, sampleCount, format, renderbuffer);
    }

    @Nullable
    @SharedPtr
    public static GLAttachment makeMSAA(GLServer server,
                                        int width, int height,
                                        int sampleCount,
                                        int format) {
        assert sampleCount > 1;
        int renderbuffer = glCreateRenderbuffers();
        if (renderbuffer == 0) {
            return null;
        }
        int internalFormat = server.getCaps().getRenderbufferInternalFormat(format);
        if (server.getCaps().skipErrorChecks()) {
            glNamedRenderbufferStorageMultisample(renderbuffer, sampleCount, internalFormat, width, height);
        } else {
            glClearErrors();
            glNamedRenderbufferStorageMultisample(renderbuffer, sampleCount, internalFormat, width, height);
            if (glGetError() != GL_NO_ERROR) {
                glDeleteRenderbuffers(renderbuffer);
                return null;
            }
        }

        return new GLAttachment(server, width, height, sampleCount, format, renderbuffer);
    }

    @Nonnull
    @SharedPtr
    public static GLAttachment makeWrapped(GLServer server,
                                           int width, int height,
                                           int sampleCount,
                                           int format,
                                           int renderbuffer) {
        assert sampleCount > 0;
        return new GLAttachment(server, width, height, sampleCount, format, renderbuffer);
    }

    @Nonnull
    @Override
    public BackendFormat getBackendFormat() {
        if (mBackendFormat == null) {
            mBackendFormat = GLBackendFormat.make(mFormat);
        }
        return mBackendFormat;
    }

    public int getRenderbufferID() {
        return mRenderbuffer;
    }

    public int getFormat() {
        return mFormat;
    }

    @Override
    public long getMemorySize() {
        return mMemorySize;
    }

    @Override
    protected void onRelease() {
        if (mRenderbuffer != 0) {
            glDeleteRenderbuffers(mRenderbuffer);
        }
        mRenderbuffer = 0;
    }

    @Override
    protected void onDiscard() {
        mRenderbuffer = 0;
    }
}
