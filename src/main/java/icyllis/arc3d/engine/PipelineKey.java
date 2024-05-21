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

import javax.annotation.Nonnull;

/**
 * This class is used to generate a generic pipeline cache key.
 * Also used to lookup pipeline state objects in cache.
 */
public final class PipelineKey extends KeyBuilder {

    private int mShaderKeyLength;

    public PipelineKey() {
    }

    public PipelineKey(PipelineKey other) {
        super(other);
        mShaderKeyLength = other.mShaderKeyLength;
    }

    /**
     * Returns the number of ints of the base key, without additional information.
     * The key in this range describes the shader module info. OpenGL has no additional
     * information, but Vulkan has.
     * <p>
     * Because Vulkan encapsulates some states into an immutable structure, we have to
     * collect additional information to form the cache key.
     */
    public int getShaderKeyLength() {
        return mShaderKeyLength;
    }

    /**
     * Builds a base pipeline descriptor, without additional information.
     *
     * @param desc the pipeline descriptor
     * @param info the pipeline information
     * @param caps the context capabilities
     */
    @Nonnull
    public static PipelineKey build(PipelineKey desc, GraphicsPipelineDesc_Old info, Caps caps) {
        desc.clear();
        genKey(desc, info, caps);
        desc.mShaderKeyLength = desc.size();
        return desc;
    }

    static void genKey(KeyBuilder b,
                       GraphicsPipelineDesc_Old info,
                       Caps caps) {
        genGPKey(info.geomProc(), b);

        //TODO more keys

        b.addBits(16, info.writeSwizzle(), "writeSwizzle");

        // Put a clean break between the "common" data written by this function, and any backend data
        // appended later. The initial key length will just be this portion (rounded to 4 bytes).
        b.flush();
    }

    /**
     * Functions which emit processor key info into the key builder.
     * For every effect, we include the effect's class ID (different for every GrProcessor subclass),
     * any information generated by the effect itself (addToKey), and some meta-information.
     * Shader code may be dependent on properties of the effect not placed in the key by the effect
     * (e.g. pixel format of textures used).
     */
    static void genGPKey(GeometryStep geomProc, KeyBuilder b) {
        // We allow 32 bits for the class id
        b.addInt32(geomProc.classID(), "gpClassID");

        geomProc.appendToKey(b);
        geomProc.appendAttributesToKey(b);

        // read swizzles are implemented as texture views, will not affect the shader code
        /*int numSamplers = geomProc.numTextureSamplers();
        b.addBits(4, numSamplers, "gpNumSamplers");
        for (int i = 0; i < numSamplers; i++) {
            b.addBits(16, geomProc.textureSamplerSwizzle(i), "swizzle");
        }*/
    }
}
