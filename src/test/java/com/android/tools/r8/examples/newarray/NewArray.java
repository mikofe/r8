// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.newarray;

class NewArray {

  static class A {
    int v0;
    int v1;
    int v2;
    int v3;
    int v4;
    int v5;
    int v6;
  }

  public static void printArray(int[] array) {
    System.out.print("[");
    if (array.length > 0) {
      System.out.print(array[0]);
      for (int i = 1; i < array.length; i++) {
        System.out.print(",");
        System.out.print(i);
      }
    }
    System.out.println("]");
  }

  static void printIntermediate(boolean b) {
    System.out.print(b);
    System.out.print(",");
  }

  static void printIntermediate(double d) {
    System.out.print(d);
    System.out.print(",");
  }

  static void printIntermediate(float f) {
    System.out.print(f);
    System.out.print(",");
  }

  static void printIntermediate(int i) {
    System.out.print(i);
    System.out.print(",");
  }

  static void printIntermediate(long l) {
    System.out.print(l);
    System.out.print(",");
  }

  public static void test() {
    int x0[] = new int[]{};
    int x1[] = new int[]{0};
    int x2[] = new int[]{0, 1};
    int x3[] = new int[]{0, 1, 2};
    int x4[] = new int[]{0, 1, 2, 3};
    int x5[] = new int[]{0, 1, 2, 3, 4};
    int x6[] = new int[]{0, 1, 2, 3, 4, 5};
    int x7[] = new int[]{0, 1, 2, 3, 4, 5, 6};
    int x8[] = new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    int x9[] = new int[]{0, 1, 0, 3, 4, 0, 6, 7};
    printArray(x0);
    printArray(x1);
    printArray(x2);
    printArray(x3);
    printArray(x4);
    printArray(x5);
    printArray(x6);
    printArray(x7);
    printArray(x8);
    printArray(x9);
  }

  public static void testIntArgs(int v0, int v1, int v2, int v3, int v4, int v5) {
    int x0[] = new int[]{};
    int x1[] = new int[]{v0};
    int x2[] = new int[]{v0, v1};
    int x3[] = new int[]{v0, v1, v2};
    int x4[] = new int[]{v0, v1, v2, v3};
    int x5[] = new int[]{v0, v1, v2, v3, v4};
    int x6[] = new int[]{v0, v1, v2, v3, v4, v5};
    int x7[] = new int[]{v0, v1, v2, v3, v4, v5, v0, v1, v0, v4, v0};
    printArray(x0);
    printArray(x1);
    printArray(x2);
    printArray(x3);
    printArray(x4);
    printArray(x5);
    printArray(x6);
    printArray(x7);
  }

  public static void testObjectArg(A a) {
    int x0[] = new int[]{};
    int x1[] = new int[]{a.v0};
    int x2[] = new int[]{a.v0, a.v1};
    int x3[] = new int[]{a.v0, a.v1, a.v2};
    int x4[] = new int[]{a.v0, a.v1, a.v2, a.v3};
    int x5[] = new int[]{a.v0, a.v1, a.v2, a.v3, a.v4};
    int x6[] = new int[]{a.v0, a.v1, a.v2, a.v3, a.v4, a.v5};
    int x7[] = new int[]{a.v0, a.v1, a.v2, a.v3, a.v4, a.v5, a.v6};
    int x8[] = new int[]{a.v0, a.v1, a.v2, a.v0, a.v3, a.v4, a.v5, a.v6};
    printArray(x0);
    printArray(x1);
    printArray(x2);
    printArray(x3);
    printArray(x4);
    printArray(x5);
    printArray(x6);
    printArray(x7);
    printArray(x8);
  }

  public static void newMultiDimensionalArrays(int n) {
    int[][] i2 = new int[n][n];
    int[][][] i3 = new int[n][n][n];
    int[][][][] i4 = new int[n][n][n][n];
    int[][][][][] i5 = new int[n][n][n][n][n];
    int[][][][][][] i6 = new int[n][n][n][n][n][n];
    printIntermediate(i2.length);
    printIntermediate(i3.length);
    printIntermediate(i4.length);
    printIntermediate(i5.length);
    System.out.println(i6.length);
  }

  public static void newMultiDimensionalArrays2(int n1, int n2, int n3, int n4, int n5, int n6) {
    int[][] i2 = new int[n1][n2];
    printIntermediate(i2.length);
    int[][][] i3 = new int[n1][n2][n3];
    printIntermediate(i3.length);
    int[][][][] i4 = new int[n1][n2][n3][n4];
    printIntermediate(i4.length);
    int[][][][][] i5 = new int[n1][n2][n3][n4][n5];
    printIntermediate(i5.length);
    int[][][][][][] i6 = new int[n1][n2][n3][n4][n5][n6];
    printIntermediate(i6.length);
    int[][][][][][] i7 = new int[n1][n2][n1][n4][n5][n1];
    System.out.println(i7.length);
  }

  public static void newMultiDimensionalArrays3(int n) {
    int[][][] i3 = new int[n][n][];
    int[][][][] i4 = new int[n][n][][];
    int[][][][][][][] i7 = new int[n][n][n][n][n][n][];
    int[][][][][][][][] i8 = new int[n][n][n][n][n][n][][];
    printIntermediate(i3.length);
    printIntermediate(i4.length);
    printIntermediate(i7.length);
    System.out.println(i8.length);
  }

  public static void newMultiDimensionalArrays4() {
    boolean[][] a1 = new boolean[1][2];
    byte[][] a2 = new byte[3][4];
    char[][] a3 = new char[5][6];
    short[][] a4 = new short[7][8];
    long[][] a5 = new long[9][10];
    float[][] a6 = new float[11][12];
    double[][] a7 = new double[13][14];
    A[][] a8 = new A[15][16];
    printIntermediate(a1[0].length);
    printIntermediate(a2[0].length);
    printIntermediate(a3[0].length);
    printIntermediate(a4[0].length);
    printIntermediate(a5[0].length);
    printIntermediate(a6[0].length);
    printIntermediate(a7[0].length);
    printIntermediate(a8[0].length);
    printIntermediate(a1[0][0]);
    printIntermediate(a2[0][0]);
    printIntermediate(a3[0][0]);
    printIntermediate(a4[0][0]);
    printIntermediate(a5[0][0]);
    printIntermediate(a6[0][0]);
    printIntermediate(a7[0][0]);
    System.out.println(a8[0][0]);
  }

  public static void main(String[] args) {
    test();
    testIntArgs(0, 1, 2, 3, 4, 5);
    A a = new A();
    a.v0 = 0;
    a.v1 = 1;
    a.v2 = 2;
    a.v3 = 3;
    a.v4 = 4;
    a.v5 = 5;
    a.v6 = 6;
    testObjectArg(a);
    newMultiDimensionalArrays(6);
    newMultiDimensionalArrays2(1, 2, 3, 4, 5, 6);
    newMultiDimensionalArrays3(8);
    newMultiDimensionalArrays4();
  }
}