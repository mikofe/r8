// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package naming044;

public class B {
  public static int m() {
    return A.f;
  }
  public int f(A a) {
    return a.f;
  }
}
