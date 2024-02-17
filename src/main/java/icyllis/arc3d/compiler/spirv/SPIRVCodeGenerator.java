/*
 * This file is part of Arc 3D.
 *
 * Copyright (C) 2022-2024 BloCamLimb <pocamelards@gmail.com>
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

package icyllis.arc3d.compiler.spirv;

import icyllis.arc3d.compiler.*;
import icyllis.arc3d.compiler.tree.*;
import icyllis.arc3d.core.MathUtil;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lwjgl.BufferUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static org.lwjgl.util.spvc.Spv.*;

/**
 * SPIR-V code generator for OpenGL 4.6 and Vulkan 1.1 or above.
 * <p>
 * A SPIR-V module is a stream of uint32 words, the generated code is in host endianness,
 * which can be little-endian or big-endian.
 */
public final class SPIRVCodeGenerator extends CodeGenerator {

    // Arc 3D is not registered, so higher 16 bits are zero
    // We use 0x32D2 and 0x6D5C for the lower 16 bits (in the future)
    public static final int GENERATOR_MAGIC_NUMBER = 0x00000000;
    // We reserve the max SpvId as a sentinel (meaning not available)
    // SpvId 0 is reserved by SPIR-V and also represents absence in map
    public static final int NONE_ID = 0xFFFFFFFF;

    public final SPIRVTarget mOutputTarget;
    public final SPIRVVersion mOutputVersion;

    private final WordBuffer mNameBuffer = new WordBuffer();
    private final WordBuffer mConstantBuffer = new WordBuffer();
    private final WordBuffer mDecorationBuffer = new WordBuffer();

    private final Output mMainOutput = new Output() {
        @Override
        public void writeWord(int word) {
            grow(mBuffer.limit() + 4)
                    .putInt(word);
        }

        @Override
        public void writeWords(int[] words, int size) {
            // int array is in host endianness (native byte order)
            grow(mBuffer.limit() + (size << 2))
                    .asIntBuffer()
                    .put(words, 0, size);
        }

        @Override
        public void writeString8(String s) {
            int len = s.length();
            ByteBuffer buffer = grow(mBuffer.limit() +
                    (len + 4 & -4)); // +1 null-terminator
            int word = 0;
            int shift = 0;
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                if (c == 0 || c >= 0x80) {
                    throw new AssertionError(c);
                }
                word |= c << shift;
                shift += 8;
                if (shift == 32) {
                    buffer.putInt(word);
                    word = 0;
                    shift = 0;
                }
            }
            // null-terminator and padding
            buffer.putInt(word);
        }
    };

    // key is a pointer to symbol table, hash is based on address (reference equality)
    // struct type to SpvId[MemoryLayout.ordinal + 1], no memory layout is at [0]
    private final HashMap<Type, int[]> mStructTable = new HashMap<>();
    private final Object2IntOpenHashMap<FunctionDecl> mFunctionTable = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<Variable> mVariableTable = new Object2IntOpenHashMap<>();

    // a stack of instruction builders
    // used to write nested types or structures
    private final InstructionBuilder[] mInstBuilderPool = new InstructionBuilder[Type.kMaxNestingDepth + 2];
    private int mInstBuilderPoolSize = 0;

    // A map of instruction -> SpvId:
    private final Object2IntOpenHashMap<Instruction> mOpCache = new Object2IntOpenHashMap<>();
    // A map of SpvId -> instruction:
    private final Int2ObjectOpenHashMap<Instruction> mSpvIdCache = new Int2ObjectOpenHashMap<>();

    private final IntSet mCapabilities = new IntOpenHashSet(16, 0.5f);

    // SpvId 0 is reserved
    private int mIdCount = 1;
    private int mGLSLExtendedInstructions;

    private boolean mEmitNames = false;

    public SPIRVCodeGenerator(Context context,
                              TranslationUnit translationUnit,
                              SPIRVTarget outputTarget,
                              SPIRVVersion outputVersion) {
        super(context, translationUnit);
        mOutputTarget = outputTarget;
        mOutputVersion = outputVersion;
    }

    /**
     * Emit name strings for variables, functions, user-defined types, and members.
     * This has no semantic impact (debug only). The default is false.
     */
    public void setEmitNames(boolean emitNames) {
        mEmitNames = emitNames;
    }

    @Nonnull
    @Override
    public ByteBuffer generateCode() {
        assert mContext.getErrorHandler().errorCount() == 0;
        // Header
        // 0 - magic number
        // 1 - version number
        // 2 - generator magic
        // 3 - bound (set later)
        // 4 - schema (reserved)
        mBuffer = BufferUtils.createByteBuffer(1024)
                .putInt(SpvMagicNumber)
                .putInt(mOutputVersion.mVersionNumber)
                .putInt(GENERATOR_MAGIC_NUMBER)
                .putInt(0)
                .putInt(0);

        buildInstructions();

        writeCapabilities(mMainOutput);
        writeInstruction(SpvOpExtInstImport, mGLSLExtendedInstructions, "GLSL.std.450", mMainOutput);
        writeInstruction(SpvOpMemoryModel, SpvAddressingModelLogical, SpvMemoryModelGLSL450, mMainOutput);


        ByteBuffer buffer = mBuffer.putInt(12, mIdCount); // set bound
        mBuffer = null;
        return buffer.flip();
    }

    private void writeCapabilities(Output output) {
        // always enable Shader capability, this implicitly declares Matrix capability
        writeInstruction(SpvOpCapability, SpvCapabilityShader, output);
        for (var it = mCapabilities.iterator(); it.hasNext(); ) {
            writeInstruction(SpvOpCapability, it.nextInt(), output);
        }
    }

    private int getUniqueId(@Nonnull Type type) {
        return getUniqueId(type.isRelaxedPrecision());
    }

    private int getUniqueId(boolean relaxedPrecision) {
        int id = getUniqueId();
        if (relaxedPrecision) {
            writeInstruction(SpvOpDecorate, id, SpvDecorationRelaxedPrecision,
                    mDecorationBuffer);
        }
        return id;
    }

    private int getUniqueId() {
        return mIdCount++;
    }

    /**
     * For {@link #writeInstructionWithCache(InstructionBuilder, Output)}.
     */
    private InstructionBuilder getInstBuilder(int opcode) {
        if (mInstBuilderPoolSize == 0) {
            return new InstructionBuilder(opcode);
        }
        return mInstBuilderPool[--mInstBuilderPoolSize]
                .reset(opcode);
    }

    /**
     * Make type with cache, for types other than opaque types and interface blocks.
     */
    private int writeType(@Nonnull Type type) {
        return writeType(type, null, null);
    }

    /**
     * Make type with cache.
     *
     * @param modifiers    non-null for opaque types, otherwise null
     * @param memoryLayout non-null for uniform and shader storage blocks, and their members, otherwise null
     */
    private int writeType(@Nonnull Type type,
                          @Nullable Modifiers modifiers,
                          @Nullable MemoryLayout memoryLayout) {
        return switch (type.getTypeKind()) {
            case Type.kVoid_TypeKind -> writeInstructionWithCache(
                    getInstBuilder(SpvOpTypeVoid)
                            .addResult(),
                    mConstantBuffer
            );
            case Type.kScalar_TypeKind -> {
                if (type.isBoolean()) {
                    yield writeInstructionWithCache(
                            getInstBuilder(SpvOpTypeBool)
                                    .addResult(),
                            mConstantBuffer
                    );
                } else if (type.isInteger()) {
                    assert type.getWidth() == 32;
                    yield writeInstructionWithCache(
                            getInstBuilder(SpvOpTypeInt)
                                    .addResult()
                                    .addWord(type.getWidth())
                                    .addWord(type.isSigned() ? 1 : 0),
                            mConstantBuffer
                    );
                } else if (type.isFloat()) {
                    assert type.getWidth() == 32;
                    yield writeInstructionWithCache(
                            getInstBuilder(SpvOpTypeFloat)
                                    .addResult()
                                    .addWord(type.getWidth()),
                            mConstantBuffer
                    );
                } else {
                    throw new AssertionError();
                }
            }
            case Type.kVector_TypeKind -> {
                int scalarTypeId = writeType(type.getElementType(), modifiers, memoryLayout);
                yield writeInstructionWithCache(
                        getInstBuilder(SpvOpTypeVector)
                                .addResult()
                                .addWord(scalarTypeId)
                                .addWord(type.getRows()),
                        mConstantBuffer
                );
            }
            case Type.kMatrix_TypeKind -> {
                int vectorTypeId = writeType(type.getElementType(), modifiers, memoryLayout);
                yield writeInstructionWithCache(
                        getInstBuilder(SpvOpTypeMatrix)
                                .addResult()
                                .addWord(vectorTypeId)
                                .addWord(type.getCols()),
                        mConstantBuffer
                );
            }
            case Type.kArray_TypeKind -> {
                final int stride;
                if (memoryLayout != null) {
                    if (!memoryLayout.isSupported(type)) {
                        mContext.error(type.mPosition, "type '" + type +
                                "' is not permitted here");
                        yield NONE_ID;
                    }
                    stride = memoryLayout.stride(type);
                    assert stride > 0;
                } else {
                    stride = 0;
                }
                int elementTypeId = writeType(type.getElementType(), modifiers, memoryLayout);
                final int resultId;
                if (type.isUnsizedArray()) {
                    resultId = writeInstructionWithCache(
                            getInstBuilder(SpvOpTypeRuntimeArray)
                                    .addKeyedResult(stride)
                                    .addWord(elementTypeId),
                            mConstantBuffer
                    );
                } else {
                    int arraySize = type.getArraySize();
                    if (arraySize <= 0) {
                        mContext.error(type.mPosition, "array size must be positive");
                        yield NONE_ID;
                    }
                    int arraySizeId = writeScalarConstant(arraySize, mContext.getTypes().mInt);
                    resultId = writeInstructionWithCache(
                            getInstBuilder(SpvOpTypeArray)
                                    .addKeyedResult(stride)
                                    .addWord(elementTypeId)
                                    .addWord(arraySizeId),
                            mConstantBuffer
                    );
                }
                if (stride > 0) {
                    writeInstructionWithCache(
                            getInstBuilder(SpvOpDecorate)
                                    .addWord(resultId)
                                    .addWord(SpvDecorationArrayStride)
                                    .addWord(stride),
                            mDecorationBuffer
                    );
                }
                yield resultId;
            }
            case Type.kStruct_TypeKind -> writeStruct(type, memoryLayout);
            default -> {
                // compiler defines some intermediate types
                mContext.error(type.mPosition, "type '" + type +
                        "' is not permitted here");
                yield NONE_ID;
            }
        };
    }

    /**
     * Make struct-like type with fast cache, nested structures are allowed.
     * <p>
     * This method ensures that a structure can have different layouts, with deduplication.
     * Structures that do not require layout can reuse structure types with any layout.
     *
     * @param memoryLayout non-null for uniform and shader storage blocks, null for others
     * @return structure type id
     */
    private int writeStruct(@Nonnull Type type,
                            @Nullable MemoryLayout memoryLayout) {
        assert type.isStruct();
        int[] cached = mStructTable.computeIfAbsent(type,
                // no memory layout at [0]
                // others at [ordinal + 1]
                // total number = max ordinal + 2
                __ -> new int[MemoryLayout.Scalar.ordinal() + 2]);
        int slot = memoryLayout == null
                ? 0
                : memoryLayout.ordinal() + 1;
        int resultId = cached[slot];
        if (resultId != 0) {
            return resultId;
        }
        //TODO may not be correct for structs in blocks
        if (slot == 0) {
            // a structure that does not require layout and can match any structure with layout
            for (int i = 1; i < cached.length; i++) {
                resultId = cached[i];
                if (resultId != 0) {
                    return resultId;
                }
            }
        } else {
            // requires layout, try to reuse the one without layout
            resultId = cached[0];
            if (resultId != 0) {
                // a structure can have different layouts, so clear this default
                // since we are going to decorate it
                cached[0] = 0;
            }
        }

        var fields = type.getFields();
        final boolean unique;
        if (resultId == 0) {
            // cannot reuse
            unique = true;
            var builder = getInstBuilder(SpvOpTypeStruct)
                    .addUniqueResult();
            for (var f : fields) {
                // use builder stack
                int typeId = writeType(f.type(), f.modifiers(), memoryLayout);
                builder.addWord(typeId);
            }
            resultId = writeInstructionWithCache(builder, mConstantBuffer);
            if (mEmitNames) {
                writeInstruction(SpvOpName, resultId, type.getName(), mNameBuffer);
            }
        } else {
            unique = false;
        }
        // cache it
        cached[slot] = resultId;

        int offset = 0;
        int[] size = new int[3]; // size, matrix stride and array stride
        for (int i = 0; i < fields.length; i++) {
            var field = fields[i];
            if (unique) {
                if (mEmitNames) {
                    writeInstruction(SpvOpMemberName, resultId, i, field.name(), mNameBuffer);
                }
                if (field.type().isRelaxedPrecision()) {
                    writeInstruction(SpvOpMemberDecorate, resultId, i,
                            SpvDecorationRelaxedPrecision, mDecorationBuffer);
                }
                writeFieldModifiers(field.modifiers(), resultId, i);
            }

            // layout will never be unique
            if (memoryLayout != null) {
                if (!memoryLayout.isSupported(field.type())) {
                    mContext.error(field.position(), "type '" + field.type() +
                            "' is not permitted here");
                    return resultId;
                }

                int alignment = memoryLayout.alignment(field.type(), size);

                int fieldOffset = field.modifiers().layoutOffset();
                if (fieldOffset >= 0) {
                    // must be in declaration order
                    if (fieldOffset < offset) {
                        mContext.error(field.position(), "offset of field '" +
                                field.name() + "' must be at least " + offset);
                    }
                    if ((fieldOffset & alignment - 1) != 0) {
                        mContext.error(field.position(), "offset of field '" +
                                field.name() + "' must round up to " + alignment);
                    }
                    offset = fieldOffset;
                } else {
                    offset = MathUtil.alignTo(offset, alignment);
                }

                if (field.modifiers().layoutBuiltin() >= 0) {
                    mContext.error(field.position(), "builtin field '" + field.name() +
                            "' cannot be explicitly laid out.");
                } else {
                    writeInstruction(SpvOpMemberDecorate, resultId, i,
                            SpvDecorationOffset, offset, mDecorationBuffer);
                }

                int matrixStride = size[1];
                if (matrixStride > 0) {
                    // matrix or array-of-matrices
                    assert field.type().getElementType().isMatrix();
                    writeInstruction(SpvOpMemberDecorate, resultId, i,
                            SpvDecorationColMajor, mDecorationBuffer);
                    writeInstruction(SpvOpMemberDecorate, resultId, i,
                            SpvDecorationMatrixStride, matrixStride, mDecorationBuffer);
                }

                // end padding of matrix, array and struct is already included
                offset += size[0];
            }
        }

        return resultId;
    }

    private int writeFunctionType(@Nonnull FunctionDecl function) {
        int returnTypeId = writeType(function.getReturnType());
        var builder = getInstBuilder(SpvOpTypeFunction)
                .addResult()
                .addWord(returnTypeId);
        for (var parameter : function.getParameters()) {
            // use builder stack
            int typeId = writeFunctionParameterType(parameter.getType(),
                    parameter.getModifiers());
            builder.addWord(typeId);
        }
        return writeInstructionWithCache(builder, mConstantBuffer);
    }

    private int writeFunctionParameterType(@Nonnull Type paramType,
                                           @Nullable Modifiers paramMods) {
        //TODO rvalue, block pointer
        int storageClass;
        if (paramType.isOpaque()) {
            storageClass = SpvStorageClassUniformConstant;
        } else {
            storageClass = SpvStorageClassFunction;
        }
        return writePointerType(paramType,
                paramMods,
                null,
                storageClass);
    }

    private int writePointerType(@Nonnull Type type,
                                 int storageClass) {
        return writePointerType(type, null, null, storageClass);
    }

    private int writePointerType(@Nonnull Type type,
                                 @Nullable Modifiers modifiers,
                                 @Nullable MemoryLayout memoryLayout,
                                 int storageClass) {
        int typeId = writeType(type, modifiers, memoryLayout);
        return writeInstructionWithCache(
                getInstBuilder(SpvOpTypePointer)
                        .addResult()
                        .addWord(storageClass)
                        .addWord(typeId),
                mConstantBuffer
        );
    }

    private int writeOpConstantTrue(@Nonnull Type type) {
        assert type.isBoolean();
        int typeId = writeType(type);
        return writeInstructionWithCache(
                getInstBuilder(SpvOpConstantTrue)
                        .addWord(typeId)
                        .addResult(),
                mConstantBuffer
        );
    }

    private int writeOpConstantFalse(@Nonnull Type type) {
        assert type.isBoolean();
        int typeId = writeType(type);
        return writeInstructionWithCache(
                getInstBuilder(SpvOpConstantFalse)
                        .addWord(typeId)
                        .addResult(),
                mConstantBuffer
        );
    }

    private int writeOpConstant(@Nonnull Type type, int valueBits) {
        assert type.isInteger() || type.isFloat();
        assert type.getWidth() == 32;
        int typeId = writeType(type);
        return writeInstructionWithCache(
                getInstBuilder(SpvOpConstant)
                        .addWord(typeId)
                        .addResult()
                        .addWord(valueBits),
                mConstantBuffer
        );
    }

    /**
     * Make scalar constant with cache, not a literal.
     */
    private int writeScalarConstant(double value, Type type) {
        final int valueBits;
        switch (type.getScalarKind()) {
            case Type.kBoolean_ScalarKind -> {
                return value != 0
                        ? writeOpConstantTrue(type)
                        : writeOpConstantFalse(type);
            }
            case Type.kFloat_ScalarKind -> {
                valueBits = Float.floatToRawIntBits((float) value);
            }
            case Type.kSigned_ScalarKind -> {
                valueBits = (int) value;
            }
            case Type.kUnsigned_ScalarKind -> {
                // bit pattern is two's complement
                valueBits = (int) (long) value;
            }
            default -> throw new AssertionError();
        }
        assert type.getWidth() == 32;
        return writeOpConstant(type, valueBits);
    }

    /**
     * Write decorations.
     */
    private void writeModifiers(@Nonnull Modifiers modifiers, int targetId) {
        Layout layout = modifiers.layout();
        if (layout != null) {
            boolean isPushConstant = (layout.layoutFlags() & Layout.kPushConstant_LayoutFlag) != 0;
            if (layout.mLocation >= 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationLocation,
                        layout.mLocation, mDecorationBuffer);
            }
            if (layout.mComponent >= 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationComponent,
                        layout.mComponent, mDecorationBuffer);
            }
            if (layout.mIndex >= 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationIndex,
                        layout.mIndex, mDecorationBuffer);
            }
            if (layout.mBinding >= 0) {
                if (isPushConstant) {
                    mContext.error(modifiers.mPosition, "cannot combine 'binding' with 'push_constants'");
                } else {
                    writeInstruction(SpvOpDecorate, targetId, SpvDecorationBinding,
                            layout.mBinding, mDecorationBuffer);
                }
            }
            if (layout.mSet >= 0) {
                if (isPushConstant) {
                    mContext.error(modifiers.mPosition, "cannot combine 'set' with 'push_constants'");
                } else {
                    writeInstruction(SpvOpDecorate, targetId, SpvDecorationDescriptorSet,
                            layout.mSet, mDecorationBuffer);
                }
            }
            if (layout.mInputAttachmentIndex >= 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationInputAttachmentIndex,
                        layout.mInputAttachmentIndex, mDecorationBuffer);
                mCapabilities.add(SpvCapabilityInputAttachment);

            }
            if (layout.mBuiltin >= 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationBuiltIn,
                        layout.mBuiltin, mDecorationBuffer);
            }
        }

        // interpolation qualifiers
        if ((modifiers.flags() & Modifiers.kSmooth_Flag) == 0) {
            // no smooth
            if ((modifiers.flags() & Modifiers.kNoPerspective_Flag) != 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationNoPerspective,
                        mDecorationBuffer);
            } else if ((modifiers.flags() & Modifiers.kFlat_Flag) != 0) {
                writeInstruction(SpvOpDecorate, targetId, SpvDecorationFlat,
                        mDecorationBuffer);
            }
        }

        // memory qualifiers
        if ((modifiers.flags() & Modifiers.kVolatile_Flag) != 0) {
            writeInstruction(SpvOpDecorate, targetId, SpvDecorationVolatile,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & (Modifiers.kCoherent_Flag | Modifiers.kVolatile_Flag)) != 0) {
            // volatile is always coherent
            writeInstruction(SpvOpDecorate, targetId, SpvDecorationCoherent,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kRestrict_Flag) != 0) {
            writeInstruction(SpvOpDecorate, targetId, SpvDecorationRestrict,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kReadOnly_Flag) != 0) {
            writeInstruction(SpvOpDecorate, targetId, SpvDecorationNonWritable,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kWriteOnly_Flag) != 0) {
            writeInstruction(SpvOpDecorate, targetId, SpvDecorationNonReadable,
                    mDecorationBuffer);
        }
    }

    /**
     * Write member decorations, excluding offset.
     */
    private void writeFieldModifiers(@Nonnull Modifiers modifiers, int targetId, int member) {
        Layout layout = modifiers.layout();
        if (layout != null) {
            assert (layout.mIndex == -1);
            assert (layout.mBinding == -1);
            assert (layout.mSet == -1);
            assert (layout.mInputAttachmentIndex == -1);
            if (layout.mLocation >= 0) {
                writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationLocation,
                        layout.mLocation, mDecorationBuffer);
            }
            if (layout.mComponent >= 0) {
                writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationComponent,
                        layout.mComponent, mDecorationBuffer);
            }
            if (layout.mBuiltin >= 0) {
                writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationBuiltIn,
                        layout.mBuiltin, mDecorationBuffer);
            }
        }

        // interpolation qualifiers
        if ((modifiers.flags() & Modifiers.kSmooth_Flag) == 0) {
            // no smooth
            if ((modifiers.flags() & Modifiers.kNoPerspective_Flag) != 0) {
                writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationNoPerspective,
                        mDecorationBuffer);
            } else if ((modifiers.flags() & Modifiers.kFlat_Flag) != 0) {
                writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationFlat,
                        mDecorationBuffer);
            }
        }

        // memory qualifiers
        if ((modifiers.flags() & Modifiers.kVolatile_Flag) != 0) {
            writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationVolatile,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & (Modifiers.kCoherent_Flag | Modifiers.kVolatile_Flag)) != 0) {
            // volatile is always coherent
            writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationCoherent,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kRestrict_Flag) != 0) {
            writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationRestrict,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kReadOnly_Flag) != 0) {
            writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationNonWritable,
                    mDecorationBuffer);
        }
        if ((modifiers.flags() & Modifiers.kWriteOnly_Flag) != 0) {
            writeInstruction(SpvOpMemberDecorate, targetId, member, SpvDecorationNonReadable,
                    mDecorationBuffer);
        }
    }

    private void buildInstructions() {
        mGLSLExtendedInstructions = getUniqueId();
    }

    private void writeOpcode(int opcode, int count, Output output) {
        if ((count & 0xFFFF0000) != 0) {
            mContext.error(Position.NO_POS, "too many words");
        }
        output.writeWord((count << 16) | opcode);
    }

    private void writeInstruction(int opcode, Output output) {
        writeOpcode(opcode, 1, output);
    }

    private void writeInstruction(int opcode, int word1, Output output) {
        writeOpcode(opcode, 2, output);
        output.writeWord(word1);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  Output output) {
        writeOpcode(opcode, 3, output);
        output.writeWord(word1);
        output.writeWord(word2);
    }

    private void writeInstruction(int opcode, int word1, String string,
                                  Output output) {
        writeOpcode(opcode, 2 + (string.length() + 4 >> 2), output);
        output.writeWord(word1);
        output.writeString8(string);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, Output output) {
        writeOpcode(opcode, 4, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  String string, Output output) {
        writeOpcode(opcode, 3 + (string.length() + 4 >> 2), output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeString8(string);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, Output output) {
        writeOpcode(opcode, 5, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  Output output) {
        writeOpcode(opcode, 6, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, Output output) {
        writeOpcode(opcode, 7, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, int word7, Output output) {
        writeOpcode(opcode, 8, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
        output.writeWord(word7);
    }

    private void writeInstruction(int opcode, int word1, int word2,
                                  int word3, int word4, int word5,
                                  int word6, int word7, int word8,
                                  Output output) {
        writeOpcode(opcode, 9, output);
        output.writeWord(word1);
        output.writeWord(word2);
        output.writeWord(word3);
        output.writeWord(word4);
        output.writeWord(word5);
        output.writeWord(word6);
        output.writeWord(word7);
        output.writeWord(word8);
    }

    /**
     * With bidirectional map.
     */
    private int writeInstructionWithCache(@Nonnull InstructionBuilder key,
                                          @Nonnull Output output) {
        assert (key.mOpcode != SpvOpLoad);
        assert (key.mOpcode != SpvOpStore);
        int cachedId = mOpCache.getInt(key);
        if (cachedId != 0) {
            releaseInstBuilder(key);
            return cachedId;
        }
        Instruction instruction = key.copy();

        int resultId = NONE_ID;
        boolean relaxedPrecision = false;

        switch (key.mResultKind) {
            case Instruction.kUniqueResult:
                resultId = getUniqueId();
                mSpvIdCache.put(resultId, instruction);
                break;
            case Instruction.kNoResult:
                mOpCache.put(instruction, resultId);
                break;

            case Instruction.kRelaxedPrecisionResult:
                relaxedPrecision = true;
                // fallthrough
            case Instruction.kKeyedResult:
                // fallthrough
            case Instruction.kDefaultPrecisionResult:
                resultId = getUniqueId(relaxedPrecision);
                mOpCache.put(instruction, resultId);
                mSpvIdCache.put(resultId, instruction);
                break;

            default:
                throw new AssertionError();
        }

        // Write the requested instruction.
        int[] values = key.mValues.elements();
        int[] kinds = key.mKinds.elements();
        int s = key.mValues.size();
        writeOpcode(key.mOpcode, s + 1, output);
        for (int i = 0; i < s; i++) {
            if (Instruction.isResult(kinds[i])) {
                assert resultId != NONE_ID;
                output.writeWord(resultId);
            } else {
                output.writeWord(values[i]);
            }
        }

        releaseInstBuilder(key);
        // Return the result.
        return resultId;
    }

    private void releaseInstBuilder(InstructionBuilder key) {
        if (mInstBuilderPoolSize == mInstBuilderPool.length) {
            return;
        }
        mInstBuilderPool[mInstBuilderPoolSize++] = key;
    }
}
