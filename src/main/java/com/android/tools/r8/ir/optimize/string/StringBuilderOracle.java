// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringBuilderHelper.extractConstantArgument;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import java.util.List;

/**
 * The {@link StringBuilderOracle} can answer if an instruction is of particular interest to the
 * StringBuilderOptimization.
 */
interface StringBuilderOracle {

  boolean isModeledStringBuilderInstruction(Instruction instruction);

  boolean hasStringBuilderType(Value value);

  boolean isStringBuilderType(DexType type);

  boolean isToString(Instruction instruction);

  String getConstantArgument(Instruction instruction);

  boolean isInspecting(Instruction instruction);

  boolean isAppend(Instruction instruction);

  boolean canObserveStringBuilderCall(Instruction instruction);

  boolean isInit(Instruction instruction);

  class DefaultStringBuilderOracle implements StringBuilderOracle {

    private final DexItemFactory factory;

    DefaultStringBuilderOracle(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    public boolean isModeledStringBuilderInstruction(Instruction instruction) {
      if (instruction.isNewInstance()) {
        return isStringBuilderType(instruction.asNewInstance().getType());
      } else if (instruction.isInvokeMethod()) {
        DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
        return isStringBuildingMethod(factory.stringBuilderMethods, invokedMethod)
            || isStringBuildingMethod(factory.stringBufferMethods, invokedMethod);
      }
      return false;
    }

    private boolean isStringBuildingMethod(StringBuildingMethods methods, DexMethod method) {
      return methods.isAppendMethod(method)
          || methods.isConstructorMethod(method)
          || method == methods.toString
          || method == methods.capacity;
    }

    @Override
    public boolean hasStringBuilderType(Value value) {
      return value.getType().isClassType()
          && isStringBuilderType(value.getType().asClassType().getClassType());
    }

    @Override
    public boolean isStringBuilderType(DexType type) {
      return type == factory.stringBuilderType || type == factory.stringBufferType;
    }

    @Override
    public boolean isToString(Instruction instruction) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return false;
      }
      InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      return factory.stringBuilderMethods.toString == invokedMethod
          || factory.stringBufferMethods.toString == invokedMethod;
    }

    @Override
    public String getConstantArgument(Instruction instruction) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return null;
      }
      if (isAppend(instruction)) {
        return getConstantStringForAppend(instruction.asInvokeVirtual());
      } else if (isInit(instruction)) {
        return getConstantStringForInit(instruction.asInvokeDirect());
      }
      return null;
    }

    private DexType getAppendType(InvokeVirtual invokeMethodWithReceiver) {
      DexMethod invokedMethod = invokeMethodWithReceiver.getInvokedMethod();
      if (!factory.stringBuilderMethods.isAppendMethod(invokedMethod)
          && !factory.stringBufferMethods.isAppendMethod(invokedMethod)) {
        return null;
      }
      return invokedMethod.getParameter(0);
    }

    private String getConstantStringForAppend(InvokeVirtual invoke) {
      DexType appendType = getAppendType(invoke);
      Value arg = invoke.getFirstNonReceiverArgument().getAliasedValue();
      return appendType != null
          ? extractConstantArgument(factory, invoke.getInvokedMethod(), arg, appendType)
          : null;
    }

    private String getConstantStringForInit(InvokeDirect invoke) {
      assert invoke.isInvokeConstructor(factory);
      List<Value> inValues = invoke.inValues();
      if (inValues.size() == 1) {
        return "";
      } else if (inValues.size() == 2 && !invoke.getArgument(1).getType().isPrimitiveType()) {
        Value arg = invoke.getArgument(1).getAliasedValue();
        DexType argType = invoke.getInvokedMethod().getParameter(0);
        return argType != null
            ? extractConstantArgument(factory, invoke.getInvokedMethod(), arg, argType)
            : null;
      }
      return null;
    }

    @Override
    public boolean isInspecting(Instruction instruction) {
      if (!instruction.isInvokeMethodWithReceiver()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethodWithReceiver().getInvokedMethod();
      return factory.stringBuilderMethods.capacity == invokedMethod
          || factory.stringBufferMethods.capacity == invokedMethod;
    }

    @Override
    public boolean isAppend(Instruction instruction) {
      if (!instruction.isInvokeMethod()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isAppendMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendMethod(invokedMethod);
    }

    @Override
    public boolean canObserveStringBuilderCall(Instruction instruction) {
      assert isModeledStringBuilderInstruction(instruction);
      if (!instruction.isInvokeMethod()) {
        assert false : "Expecting a call to string builder";
        return true;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      if (factory.stringBuilderMethods.isAppendObjectOrCharSequenceMethod(invokedMethod)
          || factory.stringBufferMethods.isAppendObjectOrCharSequenceMethod(invokedMethod)
          || factory.stringBuilderMethods.charSequenceConstructor == invokedMethod
          || factory.stringBufferMethods.charSequenceConstructor == invokedMethod) {
        assert instruction.inValues().size() == 2;
        return instruction.inValues().get(1).getType().isStringType(factory);
      }
      return false;
    }

    @Override
    public boolean isInit(Instruction instruction) {
      if (!instruction.isInvokeDirect()) {
        return false;
      }
      DexMethod invokedMethod = instruction.asInvokeMethod().getInvokedMethod();
      return factory.stringBuilderMethods.isConstructorMethod(invokedMethod)
          || factory.stringBufferMethods.isConstructorMethod(invokedMethod);
    }
  }
}
