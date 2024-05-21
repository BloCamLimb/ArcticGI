/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

import icyllis.arc3d.core.ColorInfo;

/**
 * Shared constants, enums and utilities for Arc3D Engine.
 */
public interface Engine {

    /**
     * Block engine-private values.
     */
    @ColorInfo.ColorType
    static int colorTypeToPublic(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                    ColorInfo.CT_ALPHA_8,
                    ColorInfo.CT_RGB_565,
                    ColorInfo.CT_RGBA_8888,
                    ColorInfo.CT_RGBA_8888_SRGB,
                    ColorInfo.CT_RGB_888x,
                    ColorInfo.CT_RG_88,
                    ColorInfo.CT_BGRA_8888,
                    ColorInfo.CT_RGBA_1010102,
                    ColorInfo.CT_BGRA_1010102,
                    ColorInfo.CT_GRAY_8,
                    ColorInfo.CT_ALPHA_F16,
                    ColorInfo.CT_RGBA_F16,
                    ColorInfo.CT_RGBA_F16_CLAMPED,
                    ColorInfo.CT_RGBA_F32,
                    ColorInfo.CT_ALPHA_16,
                    ColorInfo.CT_RG_1616,
                    ColorInfo.CT_RG_F16,
                    ColorInfo.CT_RGBA_16161616,
                    ColorInfo.CT_R_8 -> ct;
            case ColorInfo.CT_ALPHA_8xxx,
                    ColorInfo.CT_ALPHA_F32xxx,
                    ColorInfo.CT_GRAY_8xxx,
                    ColorInfo.CT_R_8xxx,
                    ColorInfo.CT_RGB_888,
                    ColorInfo.CT_R_16,
                    ColorInfo.CT_R_F16,
                    ColorInfo.CT_GRAY_ALPHA_88 -> ColorInfo.CT_UNKNOWN;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Possible 3D APIs that may be used by Arc3D Engine.
     */
    interface BackendApi {

        /**
         * OpenGL 3.3 to 4.5 core profile (desktop)
         */
        int kOpenGL = 0;
        /**
         * Vulkan 1.1 (desktop and mobile)
         */
        int kVulkan = 1;
        /**
         * Mock draws nothing. It is used for unit tests and to measure CPU overhead.
         */
        int kMock = 2;

        static String toString(int value) {
            return switch (value) {
                case kOpenGL -> "OpenGL";
                case kVulkan -> "Vulkan";
                case kMock -> "Mock";
                default -> throw new AssertionError(value);
            };
        }
    }

    /**
     * Image and Surfaces can be stored such that (0, 0) in texture space may correspond to
     * either the upper-left or lower-left content pixel.
     */
    interface SurfaceOrigin {

        int kUpperLeft = 0; // top left, Vulkan
        int kLowerLeft = 1; // bottom left, OpenGL
    }

    /**
     * A Context's cache of backend context state can be partially invalidated.
     * These enums are specific to the GL backend.
     *
     * @see ImmediateContext#resetContext(int)
     */
    interface GLBackendState {

        int kRenderTarget = 1;
        int kPixelStore = 1 << 1;
        /**
         * Shader stages, vertex array and input buffers
         */
        int kPipeline = 1 << 2;
        /**
         * Also includes samplers bound to texture units
         */
        int kTexture = 1 << 3;
        int kStencil = 1 << 4;
        /**
         * Antialiasing and conservative raster
         */
        int kRaster = 1 << 5;
        int kBlend = 1 << 6;
        /**
         * View state stands for scissor and viewport
         */
        int kView = 1 << 7;
        int kMisc = 1 << 8;
    }

    /**
     * Describes image view type.
     */
    interface ImageType {
        /**
         * None may represent OpenGL renderbuffers.
         */
        byte kNone = 0;
        /**
         * GL_TEXTURE_2D, GL_TEXTURE_2D_MULTISAMPLE;
         * VK_IMAGE_TYPE_2D, layers=1; VK_IMAGE_VIEW_TYPE_2D;
         */
        byte k2D = 1;
        /**
         * GL_TEXTURE_2D_ARRAY, GL_TEXTURE_2D_MULTISAMPLE_ARRAY;
         * VK_IMAGE_TYPE_2D, layers=N; VK_IMAGE_VIEW_TYPE_2D_ARRAY;
         */
        byte k2DArray = 2;
        /**
         * GL_TEXTURE_CUBE_MAP;
         * VK_IMAGE_TYPE_2D, layers=6; VK_IMAGE_VIEW_TYPE_CUBE;
         */
        byte kCube = 3;
        /**
         * GL_TEXTURE_CUBE_MAP_ARRAY;
         * VK_IMAGE_TYPE_2D, layers=6N; VK_IMAGE_VIEW_TYPE_CUBE_ARRAY;
         */
        byte kCubeArray = 4;
        /**
         * GL_TEXTURE_3D;
         * VK_IMAGE_TYPE_3D, layers=1; VK_IMAGE_VIEW_TYPE_3D;
         */
        byte k3D = 5;
    }

    interface ImageCreateFlags {

    }

    /**
     * Indicates the type of pending IO operations that can be recorded for GPU resources.
     */
    interface IOType {
        int kRead = 0;
        int kWrite = 1;
        int kRW = 2;
    }

    /**
     * Describes the intended usage (type + access) a GPU buffer.
     * This will affect memory allocation and pipeline commands.
     */
    interface BufferUsageFlags {
        // DO NOT CHANGE THE ORDER OR THE BIT VALUE
        /**
         * Vertex buffer (also includes instance buffer).
         */
        int kVertex = 1;
        /**
         * Index buffer, also known as element buffer.
         */
        int kIndex = 1 << 1;

        // Note: vertex buffers and index buffers are mesh buffers.

        /**
         * Staging buffer. Src meaning CPU to device, Dst meaning device to CPU.
         * In OpenGL, this means only pixel transfer buffer.
         */
        int kTransferSrc = 1 << 2; // transfer src and host coherent
        int kTransferDst = 1 << 3; // transfer dst and host cached

        // Note: transfer buffers must be created with HostVisible flag.

        /**
         * Uniform buffer, also known as constant buffer.
         * This will be created as ring buffers.
         */
        int kUniform = 1 << 4;
        /**
         * Indirect buffer, also known as argument buffer.
         * Not always available, check caps first.
         */
        int kDrawIndirect = 1 << 5;

        // Note: draw indirect buffers are currently not supported.

        /**
         * Data store will be written to once by CPU.
         * Use device-local only memory.
         * A staging buffer is required to update it contents.
         * For static or dynamic VBO, static UBO.
         */
        int kDeviceLocal = 1 << 6;
        /**
         * Data store will be written to occasionally, CPU writes, GPU reads.
         * Use host-visible host-coherent memory and persistent mapping, and possibly device-local.
         * If write-combined memory is used, then write directly and requires sync.
         * For streaming VBO, dynamic or streaming UBO.
         */
        int kHostVisible = 1 << 7;

    }

    /**
     * Shader flags.
     */
    interface ShaderFlags {

        int kVertex = 1;
        int kGeometry = 1 << 1;
        int kFragment = 1 << 2;
        int kCompute = 1 << 3;
    }

    /**
     * Describes the encoding of channel data in a ColorType.
     *
     * @see #colorTypeEncoding(int)
     */
    int
            COLOR_ENCODING_UNORM = 0,
            COLOR_ENCODING_UNORM_PACK16 = 1,
            COLOR_ENCODING_UNORM_PACK32 = 2,
            COLOR_ENCODING_UNORM_SRGB = 3,
            COLOR_ENCODING_FLOAT = 4;

    static int colorTypeEncoding(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                    ColorInfo.CT_ALPHA_8,
                    ColorInfo.CT_RGBA_8888,
                    ColorInfo.CT_RGB_888x,
                    ColorInfo.CT_RG_88,
                    ColorInfo.CT_BGRA_8888,
                    ColorInfo.CT_GRAY_8,
                    ColorInfo.CT_ALPHA_8xxx,
                    ColorInfo.CT_GRAY_8xxx,
                    ColorInfo.CT_R_8xxx,
                    ColorInfo.CT_ALPHA_16,
                    ColorInfo.CT_RG_1616,
                    ColorInfo.CT_RGBA_16161616,
                    ColorInfo.CT_RGB_888,
                    ColorInfo.CT_R_8,
                    ColorInfo.CT_R_16,
                    ColorInfo.CT_GRAY_ALPHA_88 -> COLOR_ENCODING_UNORM;
            case ColorInfo.CT_RGB_565 -> COLOR_ENCODING_UNORM_PACK16;
            case ColorInfo.CT_RGBA_1010102,
                    ColorInfo.CT_BGRA_1010102 -> COLOR_ENCODING_UNORM_PACK32;
            case ColorInfo.CT_RGBA_8888_SRGB -> COLOR_ENCODING_UNORM_SRGB;
            case ColorInfo.CT_ALPHA_F16,
                    ColorInfo.CT_RGBA_F16,
                    ColorInfo.CT_RGBA_F16_CLAMPED,
                    ColorInfo.CT_RGBA_F32,
                    ColorInfo.CT_ALPHA_F32xxx,
                    ColorInfo.CT_RG_F16,
                    ColorInfo.CT_R_F16 -> COLOR_ENCODING_FLOAT;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Some pixel configs are inherently clamped to [0,1], some are allowed to go outside that range,
     * and some are FP but manually clamped in the XP.
     *
     * @see #colorTypeClampType(int)
     */
    int
            CLAMP_TYPE_AUTO = 0,    // Normalized, fixed-point configs
            CLAMP_TYPE_MANUAL = 1,  // Clamped FP configs
            CLAMP_TYPE_NONE = 2;    // Normal (un-clamped) FP configs

    static int colorTypeClampType(int ct) {
        return switch (ct) {
            case ColorInfo.CT_UNKNOWN,
                    ColorInfo.CT_ALPHA_8,
                    ColorInfo.CT_RGB_565,
                    ColorInfo.CT_RGBA_8888,
                    ColorInfo.CT_RGBA_8888_SRGB,
                    ColorInfo.CT_RGB_888x,
                    ColorInfo.CT_RG_88,
                    ColorInfo.CT_BGRA_8888,
                    ColorInfo.CT_RGBA_1010102,
                    ColorInfo.CT_BGRA_1010102,
                    ColorInfo.CT_GRAY_8,
                    ColorInfo.CT_ALPHA_8xxx,
                    ColorInfo.CT_GRAY_8xxx,
                    ColorInfo.CT_R_8xxx,
                    ColorInfo.CT_ALPHA_16,
                    ColorInfo.CT_RG_1616,
                    ColorInfo.CT_RGBA_16161616,
                    ColorInfo.CT_RGB_888,
                    ColorInfo.CT_R_8,
                    ColorInfo.CT_R_16,
                    ColorInfo.CT_GRAY_ALPHA_88 -> CLAMP_TYPE_AUTO;
            case ColorInfo.CT_RGBA_F16_CLAMPED -> CLAMP_TYPE_MANUAL;
            case ColorInfo.CT_ALPHA_F16,
                    ColorInfo.CT_RGBA_F16,
                    ColorInfo.CT_RGBA_F32,
                    ColorInfo.CT_ALPHA_F32xxx,
                    ColorInfo.CT_RG_F16,
                    ColorInfo.CT_R_F16 -> CLAMP_TYPE_NONE;
            default -> throw new AssertionError(ct);
        };
    }

    /**
     * Geometric primitives used for drawing.
     * <p>
     * We can't simply use point or line, because both OpenGL and Vulkan can only guarantee
     * the rasterization of one pixel in screen coordinates, may or may not anti-aliased.
     */
    interface PrimitiveType {
        byte PointList = 0; // 1 px only
        byte LineList = 1; // 1 px wide only
        byte LineStrip = 2; // 1 px wide only
        byte TriangleList = 3; // separate triangle
        byte TriangleStrip = 4; // connected triangle
    }

    /**
     * Mask formats. Used by the font atlas. Important that these are 0-based.
     * <p>
     * Using L-shift to get the number of bytes-per-pixel for the specified mask format.
     */
    int
            MASK_FORMAT_A8 = 0,     // 1-byte per pixel
            MASK_FORMAT_A565 = 1,   // 2-bytes per pixel, RGB represent 3-channel LCD coverage
            MASK_FORMAT_ARGB = 2;   // 4-bytes per pixel, color format

    /**
     * Budget types. Used with resources that have a large memory allocation.
     *
     * @see Resource
     */
    // @formatter:off
    interface BudgetType {
        /**
         * The resource is budgeted and is subject to cleaning up under budget pressure.
         */
        byte Budgeted       = 0;
        /**
         * The resource is not budgeted and is cleaned up as soon as it has no refs regardless
         * of whether it has a unique or scratch key.
         */
        byte NotBudgeted    = 1;
        /**
         * The resource is not budgeted and is allowed to remain in the cache with no refs
         * if it has a unique key. Scratch keys are ignored.
         */
        byte WrapCacheable  = 2;
    }

    /**
     * Load ops. Used to specify the load operation to be used when an OpsTask/OpsRenderPass
     * begins execution.
     */
    interface LoadOp {
        byte Load       = 0;
        byte Clear      = 1;
        byte DontCare   = 2;
        byte Count      = 3;
    }

    /**
     * Store ops. Used to specify the store operation to be used when an OpsTask/OpsRenderPass
     * ends execution.
     */
    interface StoreOp {
        byte Store      = 0;
        byte DontCare   = 1;
        byte Count      = 2;
    }

    /**
     * Combination of load ops and store ops.
     */
    interface LoadStoreOps {

        byte StoreOpShift = Byte.SIZE / 2;

        /**
         * Combination of load ops and store ops.
         * 0-4 bits: LoadOp;
         * 4-8 bits: StoreOp
         */
        byte
                Load_Store          = LoadOp.Load     | (StoreOp.Store    << StoreOpShift),
                Clear_Store         = LoadOp.Clear    | (StoreOp.Store    << StoreOpShift),
                DontLoad_Store      = LoadOp.DontCare | (StoreOp.Store    << StoreOpShift),
                Load_DontStore      = LoadOp.Load     | (StoreOp.DontCare << StoreOpShift),
                Clear_DontStore     = LoadOp.Clear    | (StoreOp.DontCare << StoreOpShift),
                DontLoad_DontStore  = LoadOp.DontCare | (StoreOp.DontCare << StoreOpShift);

        static byte make(byte load, byte store) {
            assert (load  < (1 << StoreOpShift));
            assert (store < (1 << StoreOpShift));
            return (byte) (load | (store << StoreOpShift));
        }

        /**
         * @see LoadOp
         */
        static byte loadOp(byte ops) {
            return (byte) (ops & ((1 << StoreOpShift) - 1));
        }

        /**
         * @see StoreOp
         */
        static byte storeOp(byte ops) {
            return (byte) (ops >>> StoreOpShift);
        }
    }
    // @formatter:on

    /**
     * Specifies if the holder owns the backend, OpenGL or Vulkan, object.
     */
    boolean
            Ownership_Borrowed = false, // Holder does not destroy the backend object.
            Ownership_Owned = true;     // Holder destroys the backend object.

    /**
     * Types used to describe format of indices in arrays.
     */
    interface IndexType {
        // DO NOT CHANGE THE ORDER OR THE ENUM VALUE
        int kUByte = 0,         // 8-bit
                kUShort = 1,    // 16-bit
                kUInt = 2;      // 32-bit

        /**
         * @return size in bytes
         */
        static int size(int type) {
            assert type == kUByte || type == kUShort || type == kUInt;
            return 1 << type;
        }
    }

    /**
     * Types used to describe format of vertices in arrays.
     */
    interface VertexAttribType {

        byte
                kFloat = 0,
                kFloat2 = 1,
                kFloat3 = 2,
                kFloat4 = 3,
                kHalf = 4,
                kHalf2 = 5,
                kHalf4 = 6;
        byte
                kInt2 = 7,   // vector of 2 32-bit ints
                kInt3 = 8,   // vector of 3 32-bit ints
                kInt4 = 9;   // vector of 4 32-bit ints
        byte
                kByte = 10,   // signed byte
                kByte2 = 11,  // vector of 2 8-bit signed bytes
                kByte4 = 12,  // vector of 4 8-bit signed bytes
                kUByte = 13,  // unsigned byte
                kUByte2 = 14, // vector of 2 8-bit unsigned bytes
                kUByte4 = 15; // vector of 4 8-bit unsigned bytes
        byte
                kUByte_norm = 16,  // unsigned byte, e.g. coverage, 0 -> 0.0f, 255 -> 1.0f.
                kUByte4_norm = 17; // vector of 4 unsigned bytes, e.g. colors, 0 -> 0.0f, 255 -> 1.0f.
        byte
                kShort2 = 18,       // vector of 2 16-bit shorts.
                kShort4 = 19;       // vector of 4 16-bit shorts.
        byte
                kUShort2 = 20,      // vector of 2 unsigned shorts. 0 -> 0, 65535 -> 65535.
                kUShort2_norm = 21; // vector of 2 unsigned shorts. 0 -> 0.0f, 65535 -> 1.0f.
        byte
                kInt = 22,
                kUInt = 23;
        byte
                kUShort_norm = 24;
        byte
                kUShort4_norm = 25; // vector of 4 unsigned shorts. 0 -> 0.0f, 65535 -> 1.0f.
        byte
                kLast = kUShort4_norm;

        /**
         * @return size in bytes
         */
        static int size(byte type) {
            switch (type) {
                case kFloat:
                    return Float.BYTES;
                case kFloat2:
                    return 2 * Float.BYTES;
                case kFloat3:
                    return 3 * Float.BYTES;
                case kFloat4:
                    return 4 * Float.BYTES;
                case kHalf:
                case kUShort_norm:
                    return Short.BYTES;
                case kHalf2:
                case kShort2:
                case kUShort2:
                case kUShort2_norm:
                    return 2 * Short.BYTES;
                case kHalf4:
                case kShort4:
                case kUShort4_norm:
                    return 4 * Short.BYTES;
                case kInt2:
                    return 2 * Integer.BYTES;
                case kInt3:
                    return 3 * Integer.BYTES;
                case kInt4:
                    return 4 * Integer.BYTES;
                case kByte:
                case kUByte:
                case kUByte_norm:
                    return Byte.BYTES;
                case kByte2:
                case kUByte2:
                    return 2 * Byte.BYTES;
                case kByte4:
                case kUByte4:
                case kUByte4_norm:
                    return 4 * Byte.BYTES;
                case kInt:
                case kUInt:
                    return Integer.BYTES;
            }
            throw new AssertionError(type);
        }
    }

    /**
     * ResourceHandle is an opaque handle to a resource, actually a table index.
     */
    int INVALID_RESOURCE_HANDLE = -1;
}
