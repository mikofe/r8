// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexDivInt2Addr extends DexFormat12x {

  public static final int OPCODE = 0xb3;
  public static final String NAME = "DivInt2Addr";
  public static final String SMALI_NAME = "div-int/2addr";

  /*package*/ DexDivInt2Addr(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexDivInt2Addr(int left, int right) {
    super(left, right);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addDiv(NumericType.INT, A, A, B);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
