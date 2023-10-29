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

package icyllis.arc3d.engine;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Implementation of {@code Multimap} that uses an {@code ArrayDeque} to store the values for a given
 * key. A {@link HashMap} associates each key with an {@link ArrayDeque} of values.
 *
 * @see HashMap
 * @see ArrayDeque
 */
public class ArrayDequeMultimap<K, V> extends HashMap<K, ArrayDeque<V>> {

    private V mTmpValue;

    private final BiFunction<K, ArrayDeque<V>, ArrayDeque<V>> mRemoveFirstEntry =
            (__, queue) -> queue.removeFirstOccurrence(mTmpValue) && queue.isEmpty() ? null : queue;
    private final BiFunction<K, ArrayDeque<V>, ArrayDeque<V>> mRemoveLastEntry =
            (__, queue) -> queue.removeLastOccurrence(mTmpValue) && queue.isEmpty() ? null : queue;

    public ArrayDequeMultimap() {
    }

    public ArrayDequeMultimap(Map<? extends K, ? extends ArrayDeque<V>> other) {
        super(other);
    }

    public void addFirstEntry(K k, V v) {
        computeIfAbsent(k, __ -> new ArrayDeque<>())
                .addFirst(v);
    }

    public void addLastEntry(K k, V v) {
        computeIfAbsent(k, __ -> new ArrayDeque<>())
                .addLast(v);
    }

    public V pollFirstEntry(K k) {
        ArrayDeque<V> queue = get(k);
        if (queue != null) {
            return queue.pollFirst();
        }
        return null;
    }

    public V pollLastEntry(K k) {
        ArrayDeque<V> queue = get(k);
        if (queue != null) {
            return queue.pollLast();
        }
        return null;
    }

    // this method will release the backing buffer if queue is empty, whereas poll() won't
    public void removeFirstEntry(K k, V v) {
        assert (mTmpValue == null);
        mTmpValue = v;
        computeIfPresent(k, mRemoveFirstEntry);
        assert (mTmpValue == v);
        mTmpValue = null;
    }

    // this method will release the backing buffer if queue is empty, whereas poll() won't
    public void removeLastEntry(K k, V v) {
        assert (mTmpValue == null);
        mTmpValue = v;
        computeIfPresent(k, mRemoveLastEntry);
        assert (mTmpValue == v);
        mTmpValue = null;
    }
}
