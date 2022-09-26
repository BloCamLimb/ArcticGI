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

import icyllis.arcticgi.core.Kernel32;
import icyllis.arcticgi.engine.*;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.system.Platform;

import javax.annotation.Nonnull;

import static icyllis.arcticgi.engine.Engine.*;
import static icyllis.arcticgi.opengl.GLCore.*;

/**
 * Represents OpenGL 2D textures, can be used as textures and color attachments.
 */
public final class GLTexture extends Texture {

    private final GLTextureInfo mInfo;
    private final GLBackendTexture mBackendTexture;
    private final boolean mOwnership;

    private final long mMemorySize;

    public GLTexture(GLServer server,
                     int width, int height,
                     GLTextureInfo info,
                     BackendFormat format,
                     boolean budgeted,
                     boolean ownership) {
        super(server, width, height);
        assert info.mTexture != 0;
        assert format.getGLFormat() != GLTypes.FORMAT_UNKNOWN;
        mInfo = info;
        mBackendTexture = new GLBackendTexture(width, height, info, new GLTextureParameters(), format);
        mOwnership = ownership;

        if (glFormatIsCompressed(format.getGLFormat()) || format.getTextureType() == TextureType_External) {
            mFlags |= SurfaceFlag_ReadOnly;
        }
        if (mBackendTexture.isMipmapped()) {
            mFlags |= SurfaceFlag_Mipmapped;
        }
        mFlags |= SurfaceFlag_Renderable;

        mMemorySize = computeSize(format, width, height, 1, info.mLevelCount);
        registerWithCache(budgeted);
    }

    // Constructor for instances wrapping backend objects.
    public GLTexture(GLServer server,
                     int width, int height,
                     GLTextureInfo info,
                     GLTextureParameters params,
                     BackendFormat format,
                     int ioType,
                     boolean cacheable,
                     boolean ownership) {
        super(server, width, height);
        assert info.mTexture != 0;
        assert format.getGLFormat() != GLTypes.FORMAT_UNKNOWN;
        mInfo = info;
        mBackendTexture = new GLBackendTexture(width, height, info, params, format);
        mOwnership = ownership;

        // compressed formats always set 'ioType' to READ
        assert (ioType == IOType_Read || glFormatIsCompressed(format.getGLFormat()));
        if (ioType == IOType_Read || format.getTextureType() == TextureType_External) {
            mFlags |= SurfaceFlag_ReadOnly;
        }
        if (mBackendTexture.isMipmapped()) {
            mFlags |= SurfaceFlag_Mipmapped;
        }
        mFlags |= SurfaceFlag_Renderable;

        mMemorySize = computeSize(format, width, height, 1, info.mLevelCount);
        registerWithCacheWrapped(cacheable);
    }

    @Nonnull
    @Override
    public BackendFormat getBackendFormat() {
        return mBackendTexture.getBackendFormat();
    }

    public int getTexture() {
        return mInfo.mTexture;
    }

    public int getFormat() {
        return getBackendFormat().getGLFormat();
    }

    @Nonnull
    public GLTextureParameters getParameters() {
        return mBackendTexture.mParams;
    }

    @Override
    public int getTextureType() {
        return mBackendTexture.getTextureType();
    }

    @Nonnull
    @Override
    public BackendTexture getBackendTexture() {
        return mBackendTexture;
    }

    @Override
    public int getMaxMipmapLevel() {
        return mInfo.mLevelCount - 1; // minus base level
    }

    @Override
    public long getMemorySize() {
        return mMemorySize;
    }

    @Override
    protected void onFree() {
        final GLTextureInfo info = mInfo;
        if (mOwnership) {
            if (info.mTexture != 0) {
                glDeleteTextures(info.mTexture);
            }
            if (info.mMemoryObject != 0) {
                EXTMemoryObject.glDeleteMemoryObjectsEXT(info.mMemoryObject);
            }
            if (info.mMemoryHandle != -1) {
                if (Platform.get() == Platform.WINDOWS) {
                    Kernel32.CloseHandle(info.mMemoryHandle);
                } // Linux transfers the fd
            }
        }
        info.mTexture = 0;
        info.mMemoryObject = 0;
        info.mMemoryHandle = -1;
        getServer().onTextureDestroyed(this);
        super.onFree();
    }

    @Override
    protected void onDrop() {
        final GLTextureInfo info = mInfo;
        info.mTexture = 0;
        info.mMemoryObject = 0;
        info.mMemoryHandle = -1;
        getServer().onTextureDestroyed(this);
        super.onDrop();
    }

    @Override
    protected GLServer getServer() {
        return (GLServer) super.getServer();
    }
}
