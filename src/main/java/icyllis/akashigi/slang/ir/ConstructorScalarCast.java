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

package icyllis.akashigi.slang.ir;

import icyllis.akashigi.slang.ConstantFolder;
import icyllis.akashigi.slang.ThreadContext;

import javax.annotation.Nonnull;

/**
 * Represents the construction of a scalar cast, such as `float(intVariable)`.
 * <p>
 * These always contain exactly 1 scalar of a differing type, and are never constant.
 */
public final class ConstructorScalarCast extends AnyConstructor {

    private ConstructorScalarCast(int position, Type type, Expression... arguments) {
        super(position, ExpressionKind.kConstructorScalarCast, type, arguments);
    }

    // Casts a scalar expression. Casts that can be evaluated at compile-time will do so
    // (e.g. `int(4.1)` --> `Literal(int 4)`).
    public static Expression make(int position, Type type, Expression arg) {
        assert type.isScalar();
        assert arg.getType().isScalar();

        // No cast required when the types match.
        if (arg.getType().matches(type)) {
            return arg;
        }
        // Look up the value of constant variables. This allows constant-expressions like `int(zero)` to
        // be replaced with a literal zero.
        arg = ConstantFolder.makeConstantValueForVariable(position, arg);

        // We can cast scalar literals at compile-time when possible. (If the resulting literal would be
        // out of range for its type, we report an error and return zero to minimize error cascading.
        // This can occur when code is inlined, so we can't necessarily catch it during Convert. As
        // such, it's not safe to return null or assert.)
        if (arg.isLiteral()) {
            double value = ((Literal) arg).getValue();
            if (type.isNumeric() &&
                    (value < type.getMinValue() || value > type.getMaxValue())) {
                ThreadContext.getInstance().error(position,
                        String.format("value is out of range for type '%s': %.0f",
                                type.getName(), value));
                value = 0.0;
            }
            return Literal.make(position, value, type);
        }
        return new ConstructorScalarCast(position, type, arg);
    }

    @Nonnull
    @Override
    public Expression clone(int position) {
        return new ConstructorScalarCast(position, getType(), cloneArguments());
    }
}
