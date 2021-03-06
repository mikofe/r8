// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.abstractmethodremoval;

import com.android.tools.r8.examples.abstractmethodremoval.a.PackageBase;
import com.android.tools.r8.examples.abstractmethodremoval.a.Public;
import com.android.tools.r8.examples.abstractmethodremoval.b.Impl1;
import com.android.tools.r8.examples.abstractmethodremoval.b.Impl2;

public class AbstractMethodRemoval {

  private static int dummy;

  public static void main(String[] args) {
    dummy = args.length;
    invokeFoo(new Impl1());
    invokeFoo(new Impl2());
    invokeFoo(new Impl2());
    PackageBase.invokeFoo(new Impl1());
    PackageBase.invokeFoo(new Impl2());
    PackageBase.invokeFoo(new Impl2());
  }

  private static void invokeFoo(Public aPublic) {
    // Enough instructions to avoid inlining.
    aPublic.foo(dummy() + dummy() + dummy() + dummy() + dummy() + dummy() + dummy() + dummy());
  }

  private static int dummy() {
    // Enough instructions to avoid inlining.
    return dummy + dummy + dummy + dummy + dummy + dummy + dummy + dummy + dummy + dummy + dummy;
  }
}
