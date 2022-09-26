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

import javax.annotation.Nonnull;

/**
 * Common interface between {@link Texture} and {@link RenderTarget}.
 */
public interface Surface {

    /**
     * @return the width of the surface
     */
    int getWidth();

    /**
     * @return the height of the surface
     */
    int getHeight();

    /**
     * @return the backend format of this surface
     */
    @Nonnull
    BackendFormat getBackendFormat();

    /**
     * @return true if we are working with protected content
     */
    boolean isProtected();

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
    int getFlags();
}
