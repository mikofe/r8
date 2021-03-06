// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexType;
import java.util.function.Function;

public interface SyntheticDefinitionsProvider {
  ClassResolutionResult definitionFor(
      DexType type, Function<DexType, ClassResolutionResult> baseDefinitionFor);
}
