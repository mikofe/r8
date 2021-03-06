// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package basic;

public class MainUsingProvides extends MainBase {
  public static void main(String[] args) {
    new MainUsingProvides().test(DaggerMainComponentUsingProvides.create());
  }
}
