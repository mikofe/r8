// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;

public class DefaultFieldOptimizationInfo extends FieldOptimizationInfo {

  private static final DefaultFieldOptimizationInfo INSTANCE = new DefaultFieldOptimizationInfo();

  protected DefaultFieldOptimizationInfo() {}

  public static DefaultFieldOptimizationInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean cannotBeKept() {
    return false;
  }

  @Override
  public AbstractValue getAbstractValue() {
    return UnknownValue.getInstance();
  }

  @Override
  public int getReadBits() {
    return BitAccessInfo.getNoBitsReadValue();
  }

  @Override
  public DynamicType getDynamicType() {
    return DynamicType.unknown();
  }

  @Override
  public boolean isDead() {
    return false;
  }

  @Override
  public boolean valueHasBeenPropagated() {
    return false;
  }

  @Override
  public MutableFieldOptimizationInfo toMutableOptimizationInfo() {
    return new MutableFieldOptimizationInfo();
  }
}
