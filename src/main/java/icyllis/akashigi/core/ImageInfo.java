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

package icyllis.akashigi.core;

import org.intellij.lang.annotations.MagicConstant;

import javax.annotation.Nonnull;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Describes pixel dimensions and encoding.
 * <p>
 * ImageInfo contains dimensions, the pixel integral width and height. It encodes
 * how pixel bits describe alpha, transparency; color components red, blue, and green.
 * <p>
 * ColorInfo is used to interpret a color (GPU side): color type + alpha type.
 * The color space is always sRGB, no color space transformation is needed.
 * <p>
 * ColorInfo are implemented as ints to reduce object allocation. This class
 * is provided to pack and unpack the &lt;color type, alpha type&gt; tuple
 * into the int.
 */
@SuppressWarnings({"MagicConstant", "unused"})
public final class ImageInfo {

    /**
     * Compression types.
     * <table>
     *   <tr>
     *     <th>COMPRESSION_TYPE_*</th>
     *     <th>GL_COMPRESSED_*</th>
     *     <th>VK_FORMAT_*_BLOCK</th>
     *   </tr>
     *   <tr>
     *     <td>ETC2_RGB8_UNORM</td>
     *     <td>RGB8_ETC2</td>
     *     <td>ETC2_R8G8B8_UNORM</td>
     *   </tr>
     *   <tr>
     *     <td>BC1_RGB8_UNORM</td>
     *     <td>RGB_S3TC_DXT1_EXT</td>
     *     <td>BC1_RGB_UNORM</td>
     *   </tr>
     *   <tr>
     *     <td>BC1_RGBA8_UNORM</td>
     *     <td>RGBA_S3TC_DXT1_EXT</td>
     *     <td>BC1_RGBA_UNORM</td>
     *   </tr>
     * </table>
     */
    @MagicConstant(intValues = {
            COMPRESSION_TYPE_NONE,
            COMPRESSION_TYPE_ETC2_RGB8_UNORM,
            COMPRESSION_TYPE_BC1_RGB8_UNORM,
            COMPRESSION_TYPE_BC1_RGBA8_UNORM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CompressionType {
    }

    /**
     * Public values.
     */
    public static final int
            COMPRESSION_TYPE_NONE = 0,
            COMPRESSION_TYPE_ETC2_RGB8_UNORM = 1,
            COMPRESSION_TYPE_BC1_RGB8_UNORM = 2,
            COMPRESSION_TYPE_BC1_RGBA8_UNORM = 3,
            LAST_COMPRESSION_TYPE = COMPRESSION_TYPE_BC1_RGBA8_UNORM;

    /**
     * Describes how to interpret the alpha component of a pixel.
     */
    @MagicConstant(intValues = {
            ALPHA_TYPE_UNKNOWN,
            ALPHA_TYPE_OPAQUE,
            ALPHA_TYPE_PREMUL,
            ALPHA_TYPE_UNPREMUL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AlphaType {
    }

    /**
     * Alpha types.
     * <p>
     * Describes how to interpret the alpha component of a pixel. A pixel may
     * be opaque, or alpha, describing multiple levels of transparency.
     * <p>
     * In simple blending, alpha weights the source color and the destination
     * color to create a new color. If alpha describes a weight from zero to one:
     * <p>
     * result color = source color * alpha + destination color * (1 - alpha)
     * <p>
     * In practice alpha is encoded in two or more bits, where 1.0 equals all bits set.
     * <p>
     * RGB may have alpha included in each component value; the stored
     * value is the original RGB multiplied by alpha. Premultiplied color
     * components improve performance, but it will reduce the image quality.
     * The usual practice is to premultiply alpha in the GPU, since they were
     * converted into floating-point values.
     */
    public static final int
            ALPHA_TYPE_UNKNOWN = 0,  // uninitialized
            ALPHA_TYPE_OPAQUE = 1,   // pixel is opaque
            ALPHA_TYPE_PREMUL = 2,   // pixel components are premultiplied by alpha
            ALPHA_TYPE_UNPREMUL = 3, // pixel components are unassociated with alpha
            LAST_ALPHA_TYPE = ALPHA_TYPE_UNPREMUL;

    /**
     * Describes how pixel bits encode color.
     */
    @MagicConstant(intValues = {
            COLOR_TYPE_UNKNOWN,
            COLOR_TYPE_ALPHA_8,
            COLOR_TYPE_BGR_565,
            COLOR_TYPE_ABGR_4444,
            COLOR_TYPE_RGBA_8888,
            COLOR_TYPE_RGBA_8888_SRGB,
            COLOR_TYPE_RGB_888X,
            COLOR_TYPE_RG_88,
            COLOR_TYPE_BGRA_8888,
            COLOR_TYPE_RGBA_1010102,
            COLOR_TYPE_BGRA_1010102,
            COLOR_TYPE_GRAY_8,
            COLOR_TYPE_ALPHA_F16,
            COLOR_TYPE_RGBA_F16,
            COLOR_TYPE_RGBA_F16_CLAMPED,
            COLOR_TYPE_RGBA_F32,
            COLOR_TYPE_ALPHA_16,
            COLOR_TYPE_RG_1616,
            COLOR_TYPE_RG_F16,
            COLOR_TYPE_RGBA_16161616,
            COLOR_TYPE_R_8,
            COLOR_TYPE_RGB_565,
            COLOR_TYPE_SRGBA_8888,
            COLOR_TYPE_RGBA_F16_NORM,
            COLOR_TYPE_R8_UNORM,
            COLOR_TYPE_R8G8_UNORM,
            COLOR_TYPE_A16_FLOAT,
            COLOR_TYPE_R16G16_FLOAT,
            COLOR_TYPE_A16_UNORM,
            COLOR_TYPE_R16G16_UNORM,
            COLOR_TYPE_R16G16B16A16_UNORM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ColorType {
    }

    /**
     * Color types.
     * <p>
     * Describes a layout of pixel data in CPU memory. A pixel may be an alpha mask, a grayscale,
     * RGB, or ARGB. It specifies the channels, their type, and width. It does not refer to a texture
     * format and the mapping to texture formats may be many-to-many. It does not specify the sRGB
     * encoding of the stored values. The components are listed in order of where they appear in
     * memory. In other words the first component listed is in the low bits and the last component in
     * the high bits.
     */
    public static final int
            COLOR_TYPE_UNKNOWN = 0,          // uninitialized
            COLOR_TYPE_ALPHA_8 = 1,          // pixel with alpha in 8-bit byte
            COLOR_TYPE_BGR_565 = 2,          // pixel with 5 bits red, 6 bits green, 5 bits blue, in 16-bit word
            COLOR_TYPE_ABGR_4444 = 3,        // pixel with 4 bits for alpha, blue, red, green; in 16-bit word
            COLOR_TYPE_RGBA_8888 = 4,        // pixel with 8 bits for red, green, blue, alpha; in 32-bit word
            COLOR_TYPE_RGBA_8888_SRGB = 5,
            COLOR_TYPE_RGB_888X = 6,         // pixel with 8 bits each for red, green, blue; in 32-bit word
            COLOR_TYPE_RG_88 = 7,            // pixel with 8 bits for red and green; in 16-bit word
            COLOR_TYPE_BGRA_8888 = 8,        // pixel with 8 bits for blue, green, red, alpha; in 32-bit word
            COLOR_TYPE_RGBA_1010102 = 9,     // 10 bits for red, green, blue; 2 bits for alpha; in 32-bit word
            COLOR_TYPE_BGRA_1010102 = 10,    // 10 bits for blue, green, red; 2 bits for alpha; in 32-bit word
            COLOR_TYPE_GRAY_8 = 11,          // pixel with grayscale level in 8-bit byte
            COLOR_TYPE_ALPHA_F16 = 12,       // pixel with a half float for alpha
            COLOR_TYPE_RGBA_F16 = 13,        // pixel with half floats for red, green, blue, alpha; in 64-bit word
            COLOR_TYPE_RGBA_F16_CLAMPED = 14,// pixel with half floats [0,1] for red, green, blue, alpha; in 64-bit word
            COLOR_TYPE_RGBA_F32 = 15,        // pixel using C float for red, green, blue, alpha; in 128-bit word
            COLOR_TYPE_ALPHA_16 = 16,        // pixel with a little endian uint16_t for alpha
            COLOR_TYPE_RG_1616 = 17,         // pixel with a little endian uint16_t for red and green
            COLOR_TYPE_RG_F16 = 18,          // pixel with a half float for red and green
            COLOR_TYPE_RGBA_16161616 = 19,   // pixel with a little endian uint16_t for red, green, blue and alpha
            COLOR_TYPE_R_8 = 20;
    /**
     * Aliases.
     */
    public static final int
            COLOR_TYPE_RGB_565 = COLOR_TYPE_BGR_565,
            COLOR_TYPE_SRGBA_8888 = COLOR_TYPE_RGBA_8888_SRGB,
            COLOR_TYPE_RGBA_F16_NORM = COLOR_TYPE_RGBA_F16_CLAMPED,
            COLOR_TYPE_R8_UNORM = COLOR_TYPE_R_8;
    /**
     * The following 6 color types are just for reading from - not for rendering to.
     */
    public static final int
            COLOR_TYPE_R8G8_UNORM = COLOR_TYPE_RG_88,
            COLOR_TYPE_A16_FLOAT = COLOR_TYPE_ALPHA_F16,
            COLOR_TYPE_R16G16_FLOAT = COLOR_TYPE_RG_F16,
            COLOR_TYPE_A16_UNORM = COLOR_TYPE_ALPHA_16,
            COLOR_TYPE_R16G16_UNORM = COLOR_TYPE_RG_1616,
            COLOR_TYPE_R16G16B16A16_UNORM = COLOR_TYPE_RGBA_16161616;
    /**
     * Engine values.
     * <p>
     * Unusual types that come up after reading back in cases where we are reassigning the meaning
     * of a texture format's channels to use for a particular color format but have to read back the
     * data to a full RGBA quadruple. (e.g. using a R8 texture format as A8 color type but the API
     * only supports reading to RGBA8.)
     */
    public static final int
            COLOR_TYPE_ALPHA_8XXX = 21,
            COLOR_TYPE_ALPHA_F32XXX = 22,
            COLOR_TYPE_GRAY_8XXX = 23,
            COLOR_TYPE_R_8XXX = 24;
    /**
     * Engine values.
     * <p>
     * Types used to initialize backend textures.
     */
    public static final int
            COLOR_TYPE_RGB_888 = 25,
            COLOR_TYPE_R_16 = 26,
            COLOR_TYPE_R_F16 = 27;
    public static final int LAST_COLOR_TYPE = COLOR_TYPE_R_F16;

    /**
     * Creates a color info based on the supplied color type and alpha type.
     *
     * @param colorType the color type of the color info
     * @param alphaType the alpha type of the color info
     * @return the color info based on color type and alpha type
     */
    public static int makeColorInfo(int colorType, @AlphaType int alphaType) {
        assert (colorType >= 0 && colorType <= LAST_COLOR_TYPE);
        assert ((alphaType & ~3) == 0);
        return colorType | (alphaType << 16);
    }

    /**
     * Extracts the color type from the supplied color info.
     *
     * @param colorInfo the color info to extract the color type from
     * @return the color type defined in the supplied color info
     */
    public static int colorType(int colorInfo) {
        assert ((colorInfo & ~0x3001F) == 0);
        return colorInfo & 0xFFFF;
    }

    /**
     * Extracts the alpha type from the supplied color info.
     *
     * @param colorInfo the color info to extract the alpha type from
     * @return the alpha type defined in the supplied color info
     */
    @AlphaType
    public static int alphaType(int colorInfo) {
        assert ((colorInfo & ~0x3001F) == 0);
        return colorInfo >> 16;
    }

    /**
     * Creates new ColorInfo with same AlphaType, with ColorType set to newColorType.
     */
    public static int makeColorType(int colorInfo, int newColorType) {
        return makeColorInfo(newColorType, alphaType(colorInfo));
    }

    /**
     * Creates new ColorInfo with same ColorType, with AlphaType set to newAlphaType.
     */
    public static int makeAlphaType(int colorInfo, @AlphaType int newAlphaType) {
        return makeColorInfo(colorType(colorInfo), newAlphaType);
    }

    /**
     * Block engine-private values.
     */
    @ColorType
    public static int screenColorType(int colorType) {
        return switch (colorType) {
            case COLOR_TYPE_UNKNOWN,
                    COLOR_TYPE_ALPHA_8,
                    COLOR_TYPE_BGR_565,
                    COLOR_TYPE_ABGR_4444,
                    COLOR_TYPE_RGBA_8888,
                    COLOR_TYPE_RGBA_8888_SRGB,
                    COLOR_TYPE_RGB_888X,
                    COLOR_TYPE_RG_88,
                    COLOR_TYPE_BGRA_8888,
                    COLOR_TYPE_RGBA_1010102,
                    COLOR_TYPE_BGRA_1010102,
                    COLOR_TYPE_GRAY_8,
                    COLOR_TYPE_ALPHA_F16,
                    COLOR_TYPE_RGBA_F16,
                    COLOR_TYPE_RGBA_F16_CLAMPED,
                    COLOR_TYPE_RGBA_F32,
                    COLOR_TYPE_ALPHA_16,
                    COLOR_TYPE_RG_1616,
                    COLOR_TYPE_RG_F16,
                    COLOR_TYPE_RGBA_16161616,
                    COLOR_TYPE_R_8 -> colorType;
            case COLOR_TYPE_ALPHA_8XXX,
                    COLOR_TYPE_ALPHA_F32XXX,
                    COLOR_TYPE_GRAY_8XXX,
                    COLOR_TYPE_R_8XXX,
                    COLOR_TYPE_RGB_888,
                    COLOR_TYPE_R_16,
                    COLOR_TYPE_R_F16 -> COLOR_TYPE_UNKNOWN;
            default -> throw new IllegalArgumentException(String.valueOf(colorType));
        };
    }

    /**
     * @return bpp
     */
    public static int bytesPerPixel(int colorType) {
        return switch (colorType) {
            case COLOR_TYPE_UNKNOWN -> 0;
            case COLOR_TYPE_ALPHA_8,
                    COLOR_TYPE_R_8,
                    COLOR_TYPE_GRAY_8 -> 1;
            case COLOR_TYPE_BGR_565,
                    COLOR_TYPE_ABGR_4444,
                    COLOR_TYPE_R_F16,
                    COLOR_TYPE_R_16,
                    COLOR_TYPE_ALPHA_16,
                    COLOR_TYPE_ALPHA_F16,
                    COLOR_TYPE_RG_88 -> 2;
            case COLOR_TYPE_RGB_888 -> 3;
            case COLOR_TYPE_RGBA_8888,
                    COLOR_TYPE_RG_F16,
                    COLOR_TYPE_RG_1616,
                    COLOR_TYPE_R_8XXX,
                    COLOR_TYPE_GRAY_8XXX,
                    COLOR_TYPE_ALPHA_8XXX,
                    COLOR_TYPE_BGRA_1010102,
                    COLOR_TYPE_RGBA_1010102,
                    COLOR_TYPE_BGRA_8888,
                    COLOR_TYPE_RGB_888X,
                    COLOR_TYPE_RGBA_8888_SRGB -> 4;
            case COLOR_TYPE_RGBA_F16,
                    COLOR_TYPE_RGBA_16161616,
                    COLOR_TYPE_RGBA_F16_CLAMPED -> 8;
            case COLOR_TYPE_RGBA_F32,
                    COLOR_TYPE_ALPHA_F32XXX -> 16;
            default -> throw new IllegalArgumentException(String.valueOf(colorType));
        };
    }

    private int mWidth;
    private int mHeight;
    private final int mColorInfo;

    /**
     * Creates an empty ImageInfo with {@link #COLOR_TYPE_UNKNOWN},
     * {@link #ALPHA_TYPE_UNKNOWN}, and a width and height of zero.
     */
    public ImageInfo() {
        this(0, 0, 0);
    }

    /**
     * Creates ImageInfo from integral dimensions width and height,
     * {@link #COLOR_TYPE_UNKNOWN} and {@link #ALPHA_TYPE_UNKNOWN}.
     * <p>
     * Returned ImageInfo as part of source does not draw, and as part of destination
     * can not be drawn to.
     *
     * @param width  pixel column count; must be zero or greater
     * @param height pixel row count; must be zero or greater
     */
    public ImageInfo(int width, int height) {
        this(width, height, 0);
    }

    /**
     * Creates ImageInfo from integral dimensions width and height, colorType and
     * alphaType.
     * <p>
     * Parameters are not validated to see if their values are legal, or that the
     * combination is supported.
     *
     * @param width  pixel column count; must be zero or greater
     * @param height pixel row count; must be zero or greater
     */
    public ImageInfo(int width, int height, int colorType, @AlphaType int alphaType) {
        this(width, height, makeColorInfo(colorType, alphaType));
    }

    /**
     * Creates ImageInfo from integral dimensions and ColorInfo,
     * <p>
     * Parameters are not validated to see if their values are legal, or that the
     * combination is supported.
     *
     * @param width     pixel column count; must be zero or greater
     * @param height    pixel row count; must be zero or greater
     * @param colorInfo the pixel encoding consisting of ColorType, AlphaType
     */
    ImageInfo(int width, int height, int colorInfo) {
        mWidth = width;
        mHeight = height;
        mColorInfo = colorInfo;
    }

    /**
     * Internal resize for optimization purposes. ImageInfo should be created immutable.
     */
    void resize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * Returns pixel count in each row.
     *
     * @return pixel width
     */
    public int width() {
        return mWidth;
    }

    /**
     * Returns pixel row count.
     *
     * @return pixel height
     */
    public int height() {
        return mHeight;
    }

    /**
     * Returns color type.
     *
     * @return color type
     */
    public int colorType() {
        return colorType(mColorInfo);
    }

    /**
     * Returns alpha type.
     *
     * @return alpha type
     */
    @AlphaType
    public int alphaType() {
        return alphaType(mColorInfo);
    }

    /**
     * Returns the dimensionless ColorInfo that represents the same color type,
     * alpha type as this ImageInfo.
     */
    public int colorInfo() {
        return mColorInfo;
    }

    /**
     * Returns number of bytes per pixel required by ColorType.
     * Returns zero if colorType is {@link #COLOR_TYPE_UNKNOWN}.
     *
     * @return bytes in pixel, bpp
     */
    public int bytesPerPixel() {
        return bytesPerPixel(colorType());
    }

    /**
     * Returns minimum bytes per row, computed from pixel width() and ColorType, which
     * specifies bytesPerPixel().
     *
     * @return width() times bytesPerPixel() as integer
     */
    public int minRowBytes() {
        return mWidth * bytesPerPixel();
    }

    /**
     * Returns if ImageInfo describes an empty area of pixels by checking if either
     * width or height is zero or smaller.
     *
     * @return true if either dimension is zero or smaller
     */
    public boolean isEmpty() {
        return mWidth <= 0 && mHeight <= 0;
    }

    /**
     * Returns if ImageInfo describes an empty area of pixels by checking if
     * width and height is greater than zero, and ColorInfo is valid.
     *
     * @return true if both dimension and ColorInfo is valid
     */
    public boolean isValid() {
        return mWidth > 0 && mHeight > 0 &&
                colorType(mColorInfo) != COLOR_TYPE_UNKNOWN &&
                alphaType(mColorInfo) != ALPHA_TYPE_UNKNOWN;
    }

    /**
     * Creates ImageInfo with the same ColorType and AlphaType,
     * with dimensions set to width and height.
     *
     * @param newWidth  pixel column count; must be zero or greater
     * @param newHeight pixel row count; must be zero or greater
     * @return created ImageInfo
     */
    @Nonnull
    public ImageInfo makeWH(int newWidth, int newHeight) {
        return new ImageInfo(newWidth, newHeight, mColorInfo);
    }

    /**
     * Creates ImageInfo with same AlphaType, width, and height, with ColorType set to newColorType.
     *
     * @return created ImageInfo
     */
    @Nonnull
    public ImageInfo makeColorType(int newColorType) {
        return new ImageInfo(mWidth, mHeight, makeColorType(mColorInfo, newColorType));
    }

    /**
     * Creates ImageInfo with same ColorType, width, and height, with AlphaType set to newAlphaType.
     *
     * @return created ImageInfo
     */
    @Nonnull
    public ImageInfo makeAlphaType(@AlphaType int newAlphaType) {
        return new ImageInfo(mWidth, mHeight, makeAlphaType(mColorInfo, newAlphaType));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImageInfo imageInfo = (ImageInfo) o;

        if (mWidth != imageInfo.mWidth) return false;
        if (mHeight != imageInfo.mHeight) return false;
        return mColorInfo == imageInfo.mColorInfo;
    }

    @Override
    public int hashCode() {
        int result = mWidth;
        result = 31 * result + mHeight;
        result = 31 * result + mColorInfo;
        return result;
    }
}
