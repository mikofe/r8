// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;

@Keep
public interface RetracedMethodReference
    extends RetracedClassMemberReference, Comparable<RetracedMethodReference> {

  boolean isUnknown();

  boolean isKnown();

  KnownRetracedMethodReference asKnown();

  String getMethodName();

  boolean hasPosition();

  int getOriginalPositionOrDefault(int defaultPosition);

  @Keep
  interface KnownRetracedMethodReference extends RetracedMethodReference {

    boolean isVoid();

    TypeReference getReturnType();

    List<TypeReference> getFormalTypes();

    MethodReference getMethodReference();
  }
}
