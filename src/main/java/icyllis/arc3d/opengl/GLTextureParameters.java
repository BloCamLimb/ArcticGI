/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2023 BloCamLimb <pocamelards@gmail.com>
 *
 * Arc 3D is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Arc 3D is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Arc 3D. If not, see <https://www.gnu.org/licenses/>.
 */

package icyllis.arc3d.opengl;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;

public final class GLTextureParameters {

    // Texture parameter state that is not overridden by a bound sampler object.
    public int baseMipmapLevel;
    public int maxMipmapLevel;
    // The read swizzle, identity by default.
    public final int[] swizzle = {GL_RED, GL_GREEN, GL_BLUE, GL_ALPHA};

    public GLTextureParameters() {
        // These are the OpenGL defaults.
        baseMipmapLevel = 0;
        maxMipmapLevel = 1000;
    }

    /**
     * Makes parameters invalid, forces GLContext to refresh.
     */
    public void invalidate() {
        baseMipmapLevel = ~0;
        maxMipmapLevel = ~0;
        Arrays.fill(swizzle, 0);
    }

    @Override
    public String toString() {
        return '{' +
                "baseMipmapLevel=" + baseMipmapLevel +
                ", maxMipmapLevel=" + maxMipmapLevel +
                ", swizzle=" + Arrays.toString(swizzle) +
                '}';
    }
}
