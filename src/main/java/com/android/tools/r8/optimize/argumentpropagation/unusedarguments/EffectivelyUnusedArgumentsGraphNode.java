// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.unusedarguments;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Set;

class EffectivelyUnusedArgumentsGraphNode {

  private final ProgramMethod method;
  private final int argumentIndex;

  private final Set<EffectivelyUnusedArgumentsGraphNode> predecessors = Sets.newIdentityHashSet();
  private final Set<EffectivelyUnusedArgumentsGraphNode> successors = Sets.newIdentityHashSet();

  private boolean unoptimizable;

  EffectivelyUnusedArgumentsGraphNode(ProgramMethod method, int argumentIndex) {
    this.method = method;
    this.argumentIndex = argumentIndex;
  }

  void addSuccessor(EffectivelyUnusedArgumentsGraphNode node) {
    if (successors.add(node)) {
      node.predecessors.add(this);
    } else {
      assert node.predecessors.contains(this);
    }
  }

  void cleanForRemoval() {
    clearSuccessors();
    clearPredecessors();
  }

  void clearSuccessors() {
    for (EffectivelyUnusedArgumentsGraphNode successor : successors) {
      boolean removed = successor.predecessors.remove(this);
      assert removed;
    }
    successors.clear();
  }

  void clearPredecessors() {
    for (EffectivelyUnusedArgumentsGraphNode predecessor : predecessors) {
      boolean removed = predecessor.successors.remove(this);
      assert removed;
    }
    predecessors.clear();
  }

  public int getArgumentIndex() {
    return argumentIndex;
  }

  public ProgramMethod getMethod() {
    return method;
  }

  Set<EffectivelyUnusedArgumentsGraphNode> getPredecessors() {
    return predecessors;
  }

  Set<EffectivelyUnusedArgumentsGraphNode> getSuccessors() {
    return successors;
  }

  boolean isNullable() {
    if (method.getDefinition().isInstance() && argumentIndex == 0) {
      return false;
    }
    DynamicType dynamicType =
        method.getOptimizationInfo().getArgumentInfos().getDynamicType(argumentIndex);
    return dynamicType.getNullability().isNullable();
  }

  boolean isUnoptimizable() {
    return unoptimizable;
  }

  boolean isUnused() {
    return method.getOptimizationInfo().hasUnusedArguments()
        && method.getOptimizationInfo().getUnusedArguments().get(argumentIndex);
  }

  void removeUnusedSuccessors() {
    successors.removeIf(
        successor -> {
          if (successor.isUnused()) {
            boolean removed = successor.predecessors.remove(this);
            assert removed;
            return true;
          }
          return false;
        });
  }

  void setUnoptimizable() {
    unoptimizable = true;
  }

  void setUnused() {
    if (method.getOptimizationInfo().hasUnusedArguments()) {
      getSimpleFeedback()
          .fixupUnusedArguments(method, unusedArguments -> unusedArguments.set(argumentIndex));
    } else {
      BitSet unusedArguments = new BitSet(method.getDefinition().getNumberOfArguments());
      unusedArguments.set(argumentIndex);
      getSimpleFeedback().setUnusedArguments(method, unusedArguments);
    }
  }
}
