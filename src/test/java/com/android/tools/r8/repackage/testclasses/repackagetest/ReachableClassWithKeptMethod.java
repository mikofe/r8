// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage.testclasses.repackagetest;

import com.android.tools.r8.NeverInline;

public class ReachableClassWithKeptMethod {

  @NeverInline
  static void packagePrivateMethod() {
    System.out.println("ReachableClassWithKeptMethod.packagePrivateMethod()");
  }

  @NeverInline
  public static void publicMethod() {
    System.out.println("ReachableClassWithKeptMethod.publicMethod()");
  }
}
