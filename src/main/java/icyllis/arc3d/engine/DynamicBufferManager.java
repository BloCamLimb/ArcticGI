/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024-2024 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.engine;

import icyllis.arc3d.core.*;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Manages dynamic and streaming GPU buffers.
 * <p>
 * This prefers to create a large ring buffer that is host visible and device visible.
 * If not available, creates a large staging buffer and a device local buffer.
 * <p>
 * For streaming buffers: use persistent mapped host-visible buffers for Vulkan and
 * OpenGL 4.4; use mutable buffer and CPU staging buffer for OpenGL 4.3 and below.
 */
//TODO
public class DynamicBufferManager {

    /**
     * We expect buffers for meshes to be at least 128KB.
     */
    public static final int VERTEX_BUFFER_SIZE = 1 << 17;

    private static class BlockBuffer {

        final int mUsage;
        final int mOffsetAlignment; // must be power of two
        final int mBlockSize; // must be power of two
        @SharedPtr Buffer mBuffer;
        int mOffset;

        ByteBuffer mCachedWriter;

        BlockBuffer(Caps caps, int usage, int blockSize) {
            mUsage = usage;
            mBlockSize = blockSize;
            if ((usage & Engine.BufferUsageFlags.kUniform) != 0) {
                mOffsetAlignment = caps.minUniformBufferOffsetAlignment();
            } else {
                mOffsetAlignment = VertexInputLayout.Attribute.OFFSET_ALIGNMENT;
            }
        }
    }

    // @formatter:off
    static final int kVertexBufferIndex     = 0;
    static final int kIndexBufferIndex      = 1;
    static final int kUniformBufferIndex    = 2;
    final BlockBuffer[] mCurrentBuffers = new BlockBuffer[3];
    // @formatter:on

    final ArrayList<@SharedPtr Buffer> mUsedBuffers = new ArrayList<>();

    private final ResourceProvider mResourceProvider;

    // If mapping failed on Buffers created/managed by this DrawBufferManager or by the mapped
    // transfer buffers from the UploadManager, remember so that the next Recording will fail.
    private boolean mMappingFailed = false;

    public DynamicBufferManager(Caps caps, ResourceProvider resourceProvider) {
        mResourceProvider = resourceProvider;
        mCurrentBuffers[kVertexBufferIndex] = new BlockBuffer(
                caps,
                Engine.BufferUsageFlags.kVertex | Engine.BufferUsageFlags.kHostVisible,
                VERTEX_BUFFER_SIZE
        );
    }

    @Nullable
    public ByteBuffer getVertexWriter(int requiredBytes, BufferViewInfo outInfo) {
        return prepareMappedWriter(
                mCurrentBuffers[kVertexBufferIndex],
                requiredBytes,
                outInfo,
                "DirectVertexBuffer"
        );
    }

    public void putBackVertexBytes(int unusedBytes) {

    }

    @Nonnull
    private static ByteBuffer getMappedBuffer(@Nullable ByteBuffer buffer, long address, int capacity) {
        if (buffer != null && MemoryUtil.memAddress0(buffer) == address && buffer.capacity() == capacity) {
            return buffer;
        }
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    private ByteBuffer prepareMappedWriter(BlockBuffer target,
                                           int requiredBytes,
                                           BufferViewInfo outInfo,
                                           String label) {
        assert (target.mUsage & Engine.BufferUsageFlags.kHostVisible) != 0;
        prepareBuffer(target, requiredBytes, outInfo, label);
        if (outInfo.mBuffer == null) {
            assert mMappingFailed;
            return null;
        }
        assert (outInfo.mBuffer == target.mBuffer);
        assert (outInfo.mBuffer.isLocked());

        ByteBuffer writer = getMappedBuffer(target.mCachedWriter,
                outInfo.mBuffer.getLockedBuffer(), (int) outInfo.mBuffer.getSize())
                .position((int) outInfo.mOffset)
                .limit((int) (outInfo.mOffset + outInfo.mSize));
        target.mCachedWriter = writer;
        return writer;
    }

    private void prepareBuffer(BlockBuffer target,
                               int requiredBytes,
                               BufferViewInfo outInfo,
                               String label) {
        assert requiredBytes > 0;

        if (mMappingFailed) {
            outInfo.set(null);
            return;
        }

        int startOffset = -1;
        if (target.mBuffer != null) {
            startOffset = MathUtil.alignTo(target.mOffset, target.mOffsetAlignment);
            // capture overflow
            if (startOffset < 0 ||
                    startOffset > target.mBuffer.getSize() - requiredBytes) {
                mUsedBuffers.add(target.mBuffer);
                target.mBuffer = null;
                startOffset = -1;
            }
        }

        if (target.mBuffer == null) {
            long bufferSize = Math.min(
                    MathUtil.alignTo(requiredBytes, target.mBlockSize),
                    Integer.MAX_VALUE
            );
            target.mBuffer = mResourceProvider.createBuffer(bufferSize,
                    target.mUsage);
            target.mOffset = 0;
            if (target.mBuffer == null) {
                onAllocationFailed();
                outInfo.set(null);
                return;
            }
            if ((target.mUsage & Engine.BufferUsageFlags.kHostVisible) != 0) {
                long lockedPtr = target.mBuffer.lock();
                assert lockedPtr != NULL;
            }
        }

        if (startOffset == -1) {
            startOffset = MathUtil.alignTo(target.mOffset, target.mOffsetAlignment);
        }
        outInfo.mBuffer = target.mBuffer;
        outInfo.mOffset = startOffset;
        outInfo.mSize = requiredBytes;
        target.mOffset = startOffset + requiredBytes;
    }

    private void onAllocationFailed() {
        mMappingFailed = true;

        for (var buffer : mUsedBuffers) {
            if (buffer.isLocked()) {
                // no need to flush any data
                buffer.unlock(0, 0);
            }
            buffer.unref();
        }
        mUsedBuffers.clear();

        for (var target : mCurrentBuffers) {
            if (target.mBuffer != null && target.mBuffer.isLocked()) {
                // no need to flush any data
                target.mBuffer.unlock(0, 0);
            }
            target.mBuffer = RefCnt.move(target.mBuffer);
            target.mOffset = 0;
        }
    }
}
