// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexSubInt extends DexFormat23x {

  public static final int OPCODE = 0x91;
  public static final String NAME = "SubInt";
  public static final String SMALI_NAME = "sub-int";

  DexSubInt(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexSubInt(int dest, int left, int right) {
    super(dest, left, right);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addSub(NumericType.INT, AA, BB, CC);
  }
}
