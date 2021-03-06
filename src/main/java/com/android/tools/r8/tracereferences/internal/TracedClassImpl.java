// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.ClassAccessFlags;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;

public class TracedClassImpl extends TracedReferenceBase<ClassReference, ClassAccessFlags>
    implements TracedClass {
  public TracedClassImpl(DexType type, DefinitionContext referencedFrom) {
    this(type.asClassReference(), referencedFrom, null);
  }

  public TracedClassImpl(DexClass clazz, DefinitionContext referencedFrom) {
    this(
        clazz.getClassReference(),
        referencedFrom,
        new ClassAccessFlagsImpl(clazz.getAccessFlags()));
  }

  public TracedClassImpl(
      ClassReference classReference,
      DefinitionContext referencedFrom,
      ClassAccessFlags accessFlags) {
    super(classReference, referencedFrom, accessFlags, accessFlags == null);
  }

  @Override
  public String toString() {
    return getReference().getTypeName();
  }
}
