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

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.Scanner;

// A-A∩B
public class LevP1330 {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        PrintWriter pw = new PrintWriter(System.out);
        BitSet A = new BitSet(1001), B = new BitSet(1001);
        while (sc.hasNext()) {
            A.clear();
            B.clear();
            for (int i = sc.nextInt(); i > 0; i--)
                A.set(sc.nextInt());
            for (int i = sc.nextInt(); i > 0; i--)
                B.set(sc.nextInt());
            B.and(A);
            A.andNot(B);
            for (int i = 0; i < 1001; i++)
                if (A.get(i)) {
                    pw.print(i);
                    pw.print(' ');
                }
            pw.println();
        }
        pw.flush();
    }
}
