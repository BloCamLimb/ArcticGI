/*
 * Arc 3D.
 * Copyright (C) 2022-2023 BloCamLimb. All rights reserved.
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

import java.util.Arrays;
import java.util.Scanner;

public class LevP1231 {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int[] counts = new int[26];
        while (scanner.hasNext()) {
            String s = scanner.next();
            Arrays.fill(counts, 0);
            for (int i = 0, len = s.length(); i < len; i++)
                counts[s.charAt(i) - 'a']++;
            for (int i = 0; i < 26; i++)
                if (counts[i] == 1) System.out.print((char) ('a' + i));
            System.out.println();
        }
    }
}
