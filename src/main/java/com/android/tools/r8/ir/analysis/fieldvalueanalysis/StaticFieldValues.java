// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.google.common.collect.ImmutableMap;

public abstract class StaticFieldValues {

  public boolean isEnumStaticFieldValues() {
    return false;
  }

  public EnumStaticFieldValues asEnumStaticFieldValues() {
    return null;
  }

  public static Builder builder(DexProgramClass clazz) {
    return clazz.isEnum() ? EnumStaticFieldValues.builder() : EmptyStaticValues.builder();
  }

  public abstract static class Builder {

    public abstract void recordStaticField(
        DexEncodedField staticField, AbstractValue value, DexItemFactory factory);

    public abstract StaticFieldValues build();
  }

  // All the abstract values stored here may match a pinned field, using them requires therefore
  // to check the field is not pinned or prove it is no longer pinned.
  public static class EnumStaticFieldValues extends StaticFieldValues {
    private final ImmutableMap<DexField, AbstractValue> enumAbstractValues;
    private final DexField valuesField;
    private final AbstractValue valuesAbstractValue;

    public EnumStaticFieldValues(
        ImmutableMap<DexField, AbstractValue> enumAbstractValues,
        DexField valuesField,
        AbstractValue valuesAbstractValue) {
      this.enumAbstractValues = enumAbstractValues;
      this.valuesField = valuesField;
      this.valuesAbstractValue = valuesAbstractValue;
    }

    static StaticFieldValues.Builder builder() {
      return new Builder();
    }

    public static class Builder extends StaticFieldValues.Builder {
      private final ImmutableMap.Builder<DexField, AbstractValue> enumAbstractValuesBuilder =
          ImmutableMap.builder();
      private DexField valuesFields;
      private AbstractValue valuesAbstractValue;

      Builder() {}

      @Override
      public void recordStaticField(
          DexEncodedField staticField, AbstractValue value, DexItemFactory factory) {
        // TODO(b/166532388): Stop relying on the values name.
        if (staticField.getName() == factory.enumValuesFieldName) {
          valuesFields = staticField.field;
          valuesAbstractValue = value;
        } else if (staticField.isEnum()) {
          enumAbstractValuesBuilder.put(staticField.field, value);
        }
      }

      @Override
      public StaticFieldValues build() {
        ImmutableMap<DexField, AbstractValue> enumAbstractValues =
            enumAbstractValuesBuilder.build();
        if (valuesAbstractValue == null && enumAbstractValues.isEmpty()) {
          return EmptyStaticValues.getInstance();
        }
        return new EnumStaticFieldValues(enumAbstractValues, valuesFields, valuesAbstractValue);
      }
    }

    @Override
    public boolean isEnumStaticFieldValues() {
      return true;
    }

    @Override
    public EnumStaticFieldValues asEnumStaticFieldValues() {
      return this;
    }

    // We need to access the enum instance object state to figure out if it contains known constant
    // field values. The enum instance may be accessed in two ways, directly through the enum
    // static field, or through the enum $VALUES field. If none of them are kept, the instance is
    // effectively unused. The object state may be stored in the enum static field optimization
    // info, if kept, or in the $VALUES optimization info, if kept.
    // If the enum instance is unused, this method answers null.
    public ObjectState getObjectStateForPossiblyPinnedEnumInstance(DexField field, int ordinal) {

      // Attempt 1: Get object state from the instance field's optimization info.
      AbstractValue fieldValue = enumAbstractValues.get(field);
      if (fieldValue != null) {
        if (fieldValue.isSingleFieldValue()) {
          return fieldValue.asSingleFieldValue().getState();
        }
        if (fieldValue.isUnknown()) {
          return ObjectState.empty();
        }
        assert fieldValue.isZero();
      }

      // Attempt 2: Get object state from the values field's optimization info.
      if (valuesAbstractValue.isZero()) {
        // Unused enum instance.
        return null;
      }
      if (valuesAbstractValue.isUnknown()) {
        return ObjectState.empty();
      }
      assert valuesAbstractValue.isSingleFieldValue();
      ObjectState valuesState = this.valuesAbstractValue.asSingleFieldValue().getState();
      if (valuesState.isEnumValuesObjectState()) {
        return valuesState.asEnumValuesObjectState().getObjectStateForOrdinal(ordinal);
      }
      return ObjectState.empty();
    }
  }

  public static class EmptyStaticValues extends StaticFieldValues {
    private static EmptyStaticValues INSTANCE = new EmptyStaticValues();

    private EmptyStaticValues() {}

    public static EmptyStaticValues getInstance() {
      return INSTANCE;
    }

    static StaticFieldValues.Builder builder() {
      return new Builder();
    }

    public static class Builder extends StaticFieldValues.Builder {

      @Override
      public void recordStaticField(
          DexEncodedField staticField, AbstractValue value, DexItemFactory factory) {
        // Do nothing.
      }

      @Override
      public StaticFieldValues build() {
        return EmptyStaticValues.getInstance();
      }
    }
  }
}