// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexAddIntLit16;
import com.android.tools.r8.dex.code.DexAddIntLit8;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexRsubInt;
import com.android.tools.r8.dex.code.DexRsubIntLit8;
import com.android.tools.r8.dex.code.DexSubDouble;
import com.android.tools.r8.dex.code.DexSubDouble2Addr;
import com.android.tools.r8.dex.code.DexSubFloat;
import com.android.tools.r8.dex.code.DexSubFloat2Addr;
import com.android.tools.r8.dex.code.DexSubInt;
import com.android.tools.r8.dex.code.DexSubInt2Addr;
import com.android.tools.r8.dex.code.DexSubLong;
import com.android.tools.r8.dex.code.DexSubLong2Addr;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.conversion.DexBuilder;

public class Sub extends ArithmeticBinop {

  public Sub(NumericType type, Value dest, Value left, Value right) {
    super(type, dest, left, right);
  }

  @Override
  public int opcode() {
    return Opcodes.SUB;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isCommutative() {
    return false;
  }

  @Override
  public DexInstruction CreateInt(int dest, int left, int right) {
    return new DexSubInt(dest, left, right);
  }

  @Override
  public DexInstruction CreateLong(int dest, int left, int right) {
    return new DexSubLong(dest, left, right);
  }

  @Override
  public DexInstruction CreateFloat(int dest, int left, int right) {
    return new DexSubFloat(dest, left, right);
  }

  @Override
  public DexInstruction CreateDouble(int dest, int left, int right) {
    return new DexSubDouble(dest, left, right);
  }

  @Override
  public DexInstruction CreateInt2Addr(int left, int right) {
    return new DexSubInt2Addr(left, right);
  }

  @Override
  public DexInstruction CreateLong2Addr(int left, int right) {
    return new DexSubLong2Addr(left, right);
  }

  @Override
  public DexInstruction CreateFloat2Addr(int left, int right) {
    return new DexSubFloat2Addr(left, right);
  }

  @Override
  public DexInstruction CreateDouble2Addr(int left, int right) {
    return new DexSubDouble2Addr(left, right);
  }

  @Override
  public DexInstruction CreateIntLit8(int dest, int left, int constant) {
    // The sub instructions with constants are rsub, and is handled below.
    throw new Unreachable("Unsupported instruction SubIntLit8");
  }

  @Override
  public DexInstruction CreateIntLit16(int dest, int left, int constant) {
    // The sub instructions with constants are rsub, and is handled below.
    throw new Unreachable("Unsupported instruction SubIntLit16");
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isSub() && other.asSub().type == type;
  }

  @Override
  int foldIntegers(int left, int right) {
    return left - right;
  }

  @Override
  long foldLongs(long left, long right) {
    return left - right;
  }

  @Override
  float foldFloat(float left, float right) {
    return left - right;
  }

  @Override
  double foldDouble(double left, double right) {
    return left - right;
  }

  boolean negativeFitsInDexInstruction(Value value) {
    return type == NumericType.INT &&
        value.isConstant() &&
        value.getConstInstruction().asConstNumber().negativeIs16Bit();
  }

  // This is overridden to give the correct value when adding the negative constant.
  @Override
  int maxInOutValueRegisterSize() {
    if (!needsValueInRegister(leftValue())) {
      assert fitsInDexInstruction(leftValue());
      ConstNumber left = leftValue().getConstInstruction().asConstNumber();
      return left.is8Bit() ? Constants.U8BIT_MAX : Constants.U4BIT_MAX;
    } else if (!needsValueInRegister(rightValue())) {
      assert negativeFitsInDexInstruction(rightValue());
      ConstNumber right = rightValue().getConstInstruction().asConstNumber();
      return right.negativeIs8Bit() ? Constants.U8BIT_MAX : Constants.U4BIT_MAX;
    }
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean needsValueInRegister(Value value) {
    if (leftValue() == rightValue()) {
      // We cannot distinguish the the two values, so both must end up in registers no matter what.
      return true;
    }
    if (value == leftValue()) {
      // If the left value fits in the dex instruction no register is needed for that (rsub
      // instruction).
      return !fitsInDexInstruction(value);
    } else {
      assert value == rightValue();
      // If the negative right value fits in the dex instruction no register is needed for that (add
      // instruction with the negative value), unless the left is taking that place.
      return !negativeFitsInDexInstruction(value) || fitsInDexInstruction(leftValue());
    }
  }

  @Override
  public void buildDex(DexBuilder builder) {
    // Handle two address and non-int case through the generic arithmetic binop.
    if (isTwoAddr(builder.getRegisterAllocator()) || type != NumericType.INT) {
      super.buildDex(builder);
      return;
    }

    DexInstruction instruction = null;
    if (!needsValueInRegister(leftValue())) {
      // Sub instructions with small left constant is emitted as rsub.
      assert fitsInDexInstruction(leftValue());
      ConstNumber left = leftValue().getConstInstruction().asConstNumber();
      int right = builder.allocatedRegister(rightValue(), getNumber());
      int dest = builder.allocatedRegister(outValue, getNumber());
      if (left.is8Bit()) {
        instruction = new DexRsubIntLit8(dest, right, left.getIntValue());
      } else {
        assert left.is16Bit();
        instruction = new DexRsubInt(dest, right, left.getIntValue());
      }
    } else if (!needsValueInRegister(rightValue())) {
      // Sub instructions with small right constant are emitted as add of the negative constant.
      assert negativeFitsInDexInstruction(rightValue());
      int dest = builder.allocatedRegister(outValue, getNumber());
      assert needsValueInRegister(leftValue());
      int left = builder.allocatedRegister(leftValue(), getNumber());
      ConstNumber right = rightValue().getConstInstruction().asConstNumber();
      if (right.negativeIs8Bit()) {
        instruction = new DexAddIntLit8(dest, left, -right.getIntValue());
      } else {
        assert right.negativeIs16Bit();
        instruction = new DexAddIntLit16(dest, left, -right.getIntValue());
      }
    } else {
      assert type == NumericType.INT;
      int left = builder.allocatedRegister(leftValue(), getNumber());
      int right = builder.allocatedRegister(rightValue(), getNumber());
      int dest = builder.allocatedRegister(outValue, getNumber());
      instruction = CreateInt(dest, left, right);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean isSub() {
    return true;
  }

  @Override
  public Sub asSub() {
    return this;
  }

  @Override
  CfArithmeticBinop.Opcode getCfOpcode() {
    return CfArithmeticBinop.Opcode.Sub;
  }
}
