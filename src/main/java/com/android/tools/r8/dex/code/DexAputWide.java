// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexAputWide extends DexFormat23x {

  public static final int OPCODE = 0x4c;
  public static final String NAME = "AputWide";
  public static final String SMALI_NAME = "aput-wide";

  /*package*/ DexAputWide(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexAputWide(int AA, int BB, int CC) {
    super(AA, BB, CC);
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
    builder.addArrayPut(MemberType.LONG_OR_DOUBLE, AA, BB, CC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
