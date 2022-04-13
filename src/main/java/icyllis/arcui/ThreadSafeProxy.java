/*
 * Arc UI.
 * Copyright (C) 2022-2022 BloCamLimb. All rights reserved.
 *
 * Arc UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc UI. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arcui;

import icyllis.arcui.text.TextBlobCache;
import org.intellij.lang.annotations.MagicConstant;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Can be used to perform actions related to the generating Context in a thread safe manner. The
 * proxy does not access the 3D API (e.g. OpenGL) that backs the generating Context.
 */
public final class ThreadSafeProxy {

    @MagicConstant(intValues = {OPENGL, VULKAN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface BackendApi {
    }

    // OpenGL 4.5 core profile
    public static final int OPENGL = 0;
    // Vulkan 1.1
    public static final int VULKAN = 1;

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(0);

    @BackendApi
    private final int mBackend;
    private final ContextOptions mOptions;
    private final int mContextID;

    private volatile Capabilities mCaps;
    private volatile TextBlobCache mTextBlobCache;
    private volatile ThreadSafeCache mThreadSafeCache;

    private final AtomicBoolean mClosed = new AtomicBoolean(false);

    public ThreadSafeProxy(@BackendApi int backend, ContextOptions options) {
        mBackend = backend;
        mOptions = options;
        mContextID = sNextGeneratedId.getAndIncrement();
    }

    public boolean isValid() {
        return mCaps != null;
    }

    void init(Capabilities caps) {
        mCaps = caps;
        mTextBlobCache = new TextBlobCache(mContextID);
        mThreadSafeCache = new ThreadSafeCache();
    }

    boolean matches(RecordingContext context) {
        return this == context.threadSafeProxy();
    }

    int backend() {
        return mBackend;
    }

    ContextOptions options() {
        return mOptions;
    }

    int contextID() {
        return mContextID;
    }

    Capabilities caps() {
        return mCaps;
    }

    TextBlobCache textBlobCache() {
        return mTextBlobCache;
    }

    ThreadSafeCache threadSafeCache() {
        return mThreadSafeCache;
    }

    void close() {
        if (!mClosed.compareAndExchange(false, true)) {
            mTextBlobCache.close();
        }
    }

    boolean isClosed() {
        return mClosed.get();
    }
}
