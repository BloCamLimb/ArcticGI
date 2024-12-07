/*
 * This file is part of Arc3D.
 *
 * Copyright (C) 2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.granite.geom;

import icyllis.arc3d.core.*;
import icyllis.arc3d.engine.*;
import icyllis.arc3d.engine.Engine.PrimitiveType;
import icyllis.arc3d.engine.Engine.VertexAttribType;
import icyllis.arc3d.engine.VertexInputLayout.Attribute;
import icyllis.arc3d.engine.VertexInputLayout.AttributeSet;
import icyllis.arc3d.granite.*;
import icyllis.arc3d.granite.shading.UniformHandler;
import icyllis.arc3d.granite.shading.VaryingHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Formatter;

/**
 * Draw text using a GPU glyph atlas and raster data generated by CPU.
 */
public class RasterTextStep extends GeometryStep {

    /**
     * The position in sub run's space, with creation matrix (no perspective),
     * bearing and initial origin applied.
     */
    public static final Attribute XY =
            new Attribute("XY", VertexAttribType.kFloat2, SLDataType.kFloat2);
    public static final Attribute UV =
            new Attribute("UV", VertexAttribType.kUShort2, SLDataType.kUInt2);
    public static final Attribute SIZE =
            new Attribute("Size", VertexAttribType.kUShort2, SLDataType.kUInt2);

    public static final AttributeSet INSTANCE_ATTRIBS =
            AttributeSet.makeImplicit(VertexInputLayout.INPUT_RATE_INSTANCE,
                    XY, UV, SIZE, DEPTH);

    private final int mMaskFormat;

    public RasterTextStep(int maskFormat) {
        super("RasterTextStep",
                switch (maskFormat) {
                    case Engine.MASK_FORMAT_A8 -> "gray";
                    case Engine.MASK_FORMAT_A565 -> "lcd";
                    case Engine.MASK_FORMAT_ARGB -> "color";
                    default -> throw new AssertionError();
                },
                null, INSTANCE_ATTRIBS,
                switch (maskFormat) {
                    case Engine.MASK_FORMAT_A8,
                            Engine.MASK_FORMAT_A565 -> FLAG_PERFORM_SHADING | FLAG_HAS_TEXTURES | FLAG_EMIT_COVERAGE;
                    case Engine.MASK_FORMAT_ARGB ->
                            FLAG_PERFORM_SHADING | FLAG_HAS_TEXTURES | FLAG_EMIT_PRIMITIVE_COLOR;
                    default -> throw new AssertionError();
                },
                PrimitiveType.kTriangleStrip,
                CommonDepthStencilSettings.kDirectDepthGEqualPass);
        mMaskFormat = maskFormat;
        // there's no LCD support atm, changes are needed to support
        assert maskFormat != Engine.MASK_FORMAT_A565;
        assert instanceStride() == 20;
    }

    @Override
    public void appendToKey(@NonNull KeyBuilder b) {

    }

    @NonNull
    @Override
    public ProgramImpl makeProgramImpl(ShaderCaps caps) {
        return null;
    }

    @Override
    public void emitVaryings(VaryingHandler varyingHandler, boolean usesFastSolidColor) {
        assert !usesFastSolidColor;
        varyingHandler.addVarying("f_TexCoords", SLDataType.kFloat2);
    }

    @Override
    public void emitUniforms(UniformHandler uniformHandler, boolean mayRequireLocalCoords) {
        // may have perspective
        uniformHandler.addUniform(Engine.ShaderFlags.kVertex,
                SLDataType.kFloat3x3, "u_SubRunToDevice", -1);
        if (mayRequireLocalCoords) {
            // no perspective
            uniformHandler.addUniform(Engine.ShaderFlags.kVertex,
                    SLDataType.kFloat3x3, "u_SubRunToLocal", -1);
        }
        uniformHandler.addUniform(Engine.ShaderFlags.kVertex,
                SLDataType.kFloat2, "u_InvAtlasSize", -1);
    }

    @Override
    public void emitSamplers(UniformHandler uniformHandler) {
        uniformHandler.addSampler(
                SLDataType.kSampler2D, "u_GlyphAtlas", -1);
    }

    @Override
    public void emitVertexGeomCode(Formatter vs,
                                   @NonNull String worldPosVar,
                                   @Nullable String localPosVar,
                                   boolean usesFastSolidColor) {
        assert !usesFastSolidColor;
        // {(0,0), (0,1), (1,0), (1,1)}
        // corner selector, CCW
        vs.format("vec2 position = vec2(gl_VertexID >> 1, gl_VertexID & 1) * vec2(%s);\n",
                SIZE.name());

        vs.format("""
                %s = (position + vec2(%s)) * %s;
                """, "f_TexCoords", UV.name(), "u_InvAtlasSize");

        // setup sub run position
        vs.format("""
                vec3 pos = vec3(position + %s, 1.0);
                """, XY.name());
        vs.format("vec3 devicePos = %s * pos;\n",
                "u_SubRunToDevice");
        vs.format("vec4 %s = vec4(devicePos.xy, %s, devicePos.z);\n",
                worldPosVar,
                DEPTH.name());
        if (localPosVar != null) {
            // no perspective
            vs.format("""
                    %s = (%s * pos).xy;
                    """, localPosVar, "u_SubRunToLocal");
        }
    }

    @Override
    public void emitFragmentColorCode(Formatter fs, String outputColor) {
        // ARGB only
        fs.format("%s = texture(%s, %s);\n", outputColor, "u_GlyphAtlas", "f_TexCoords");
    }

    /**
     * Remember that for DrawAtlas, we do not use texture swizzle, then the
     * corresponding geometry step must handle the swizzle in shader code.
     *
     * @see icyllis.arc3d.granite.DrawAtlas
     */
    @Override
    public void emitFragmentCoverageCode(Formatter fs, String outputCoverage) {
        // A8 and LCD only
        if (mMaskFormat == Engine.MASK_FORMAT_A8) {
            // A8 is always backed by R8 texture, colors are premultiplied
            fs.format("%s = texture(%s, %s).rrrr;\n", outputCoverage, "u_GlyphAtlas", "f_TexCoords");
        } else {
            fs.format("%s = texture(%s, %s);\n", outputCoverage, "u_GlyphAtlas", "f_TexCoords");
        }
    }

    @Override
    public void writeMesh(MeshDrawWriter writer, Draw draw,
            float @Nullable[] solidColor,
                          boolean mayRequireLocalCoords) {
        assert solidColor == null;
        var subRunData = (SubRunData) draw.mGeometry;
        // SubRunToDevice
        if (!mayRequireLocalCoords && draw.mTransform.isTranslate()) {
            // if local coordinates are not required and SubRunToDevice is translation only,
            // we can extract translation to XY and reduce uniform binding changes
            subRunData.getSubRun().fillInstanceData(
                    writer,
                    subRunData.getStartGlyphIndex(),
                    subRunData.getGlyphCount(),
                    draw.mTransform.getTranslateX(),
                    draw.mTransform.getTranslateY(),
                    draw.getDepthAsFloat()
            );
        } else {
            subRunData.getSubRun().fillInstanceData(
                    writer,
                    subRunData.getStartGlyphIndex(),
                    subRunData.getGlyphCount(),
                    draw.getDepthAsFloat()
            );
        }
    }

    @Override
    public void writeUniformsAndTextures(RecordingContext context, Draw draw,
                                         UniformDataGatherer uniformDataGatherer,
                                         TextureDataGatherer textureDataGatherer,
                                         boolean mayRequireLocalCoords) {
        var subRunData = (SubRunData) draw.mGeometry;
        @RawPtr
        var texture = context.getAtlasProvider().getGlyphAtlasManager().getCurrentTexture(
                subRunData.getSubRun().getMaskFormat()
        );
        assert texture != null;

        // SubRunToDevice
        if (!mayRequireLocalCoords && draw.mTransform.isTranslate()) {
            // if local coordinates are not required and SubRunToDevice is translation only,
            // we can extract translation to XY and reduce uniform binding changes
            uniformDataGatherer.writeMatrix3f(Matrix.identity());
        } else {
            uniformDataGatherer.writeMatrix3f(draw.mTransform);
        }
        if (mayRequireLocalCoords) {
            uniformDataGatherer.writeMatrix3f(subRunData.getSubRunToLocal());
        }
        uniformDataGatherer.write2f(
                1.f / texture.getWidth(),
                1.f / texture.getHeight()
        );

        textureDataGatherer.add(RefCnt.create(texture), SamplerDesc.make(subRunData.getFilter()));
    }
}
