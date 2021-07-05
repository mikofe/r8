// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.KeepFieldInfo.Joiner;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MapUtils;
import com.google.common.collect.Streams;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Non-mutable collection of keep information pertaining to a program.
public abstract class KeepInfoCollection {

  abstract void forEachRuleInstance(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      BiConsumer<DexProgramClass, KeepClassInfo.Joiner> classRuleInstanceConsumer,
      BiConsumer<ProgramField, KeepFieldInfo.Joiner> fieldRuleInstanceConsumer,
      BiConsumer<ProgramMethod, KeepMethodInfo.Joiner> methodRuleInstanceConsumer);

  // TODO(b/157538235): This should not be bottom.
  private static KeepClassInfo keepInfoForNonProgramClass() {
    return KeepClassInfo.bottom();
  }

  // TODO(b/157538235): This should not be bottom.
  private static KeepMethodInfo keepInfoForNonProgramMethod() {
    return KeepMethodInfo.bottom();
  }

  // TODO(b/157538235): This should not be bottom.
  private static KeepFieldInfo keepInfoForNonProgramField() {
    return KeepFieldInfo.bottom();
  }

  /**
   * Base accessor for keep info on a class.
   *
   * <p>Access may never be granted directly on DexType as the "keep info" for any non-program type
   * is not the same as the default keep info for a program type. By typing the interface at program
   * item we can eliminate errors where a reference to a non-program item results in optimizations
   * assuming aspects of it can be changed when in fact they can not.
   */
  public abstract KeepClassInfo getClassInfo(DexProgramClass clazz);

  /**
   * Base accessor for keep info on a method.
   *
   * <p>See comment on class access for why this is typed at program method.
   */
  public abstract KeepMethodInfo getMethodInfo(DexEncodedMethod method, DexProgramClass holder);

  /**
   * Base accessor for keep info on a field.
   *
   * <p>See comment on class access for why this is typed at program field.
   */
  public abstract KeepFieldInfo getFieldInfo(DexEncodedField field, DexProgramClass holder);

  public KeepMemberInfo<?, ?> getMemberInfo(DexEncodedMember<?, ?> member, DexProgramClass holder) {
    if (member.isDexEncodedField()) {
      return getFieldInfo(member.asDexEncodedField(), holder);
    }
    assert member.isDexEncodedMethod();
    return getMethodInfo(member.asDexEncodedMethod(), holder);
  }

  public final KeepClassInfo getClassInfo(DexType type, DexDefinitionSupplier definitions) {
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(type));
    return clazz == null ? keepInfoForNonProgramClass() : getClassInfo(clazz);
  }

  public final KeepMemberInfo<?, ?> getMemberInfo(ProgramMember<?, ?> member) {
    return getMemberInfo(member.getDefinition(), member.getHolder());
  }

  public final KeepMethodInfo getMethodInfo(ProgramMethod method) {
    return getMethodInfo(method.getDefinition(), method.getHolder());
  }

  public final KeepMethodInfo getMethodInfo(DexMethod method, DexDefinitionSupplier definitions) {
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionFor(method.holder));
    if (holder == null) {
      return keepInfoForNonProgramMethod();
    }
    DexEncodedMethod definition = holder.lookupMethod(method);
    return definition == null ? KeepMethodInfo.bottom() : getMethodInfo(definition, holder);
  }

  public final KeepFieldInfo getFieldInfo(ProgramField field) {
    return getFieldInfo(field.getDefinition(), field.getHolder());
  }

  public final KeepFieldInfo getFieldInfo(DexField field, DexDefinitionSupplier definitions) {
    DexProgramClass holder = asProgramClassOrNull(definitions.definitionFor(field.holder));
    if (holder == null) {
      return keepInfoForNonProgramField();
    }
    DexEncodedField definition = holder.lookupField(field);
    return definition == null ? KeepFieldInfo.bottom() : getFieldInfo(definition, holder);
  }

  public final KeepInfo<?, ?> getInfo(DexReference reference, DexDefinitionSupplier definitions) {
    if (reference.isDexType()) {
      return getClassInfo(reference.asDexType(), definitions);
    }
    if (reference.isDexMethod()) {
      return getMethodInfo(reference.asDexMethod(), definitions);
    }
    if (reference.isDexField()) {
      return getFieldInfo(reference.asDexField(), definitions);
    }
    throw new Unreachable();
  }

  public final KeepInfo<?, ?> getInfo(ProgramDefinition definition) {
    if (definition.isProgramClass()) {
      return getClassInfo(definition.asProgramClass());
    }
    if (definition.isProgramMethod()) {
      return getMethodInfo(definition.asProgramMethod());
    }
    if (definition.isProgramField()) {
      return getFieldInfo(definition.asProgramField());
    }
    throw new Unreachable();
  }

  public final boolean isPinned(DexReference reference, DexDefinitionSupplier definitions) {
    return getInfo(reference, definitions).isPinned();
  }

  public final boolean isPinned(DexType type, DexDefinitionSupplier definitions) {
    return getClassInfo(type, definitions).isPinned();
  }

  public final boolean isPinned(DexMethod method, DexDefinitionSupplier definitions) {
    return getMethodInfo(method, definitions).isPinned();
  }

  public final boolean isPinned(DexField field, DexDefinitionSupplier definitions) {
    return getFieldInfo(field, definitions).isPinned();
  }

  public final boolean isMinificationAllowed(
      DexReference reference,
      DexDefinitionSupplier definitions,
      GlobalKeepInfoConfiguration configuration) {
    return configuration.isMinificationEnabled()
        && getInfo(reference, definitions).isMinificationAllowed(configuration);
  }

  public abstract boolean verifyPinnedTypesAreLive(Set<DexType> liveTypes);

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedType(Consumer<DexType> consumer);

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedMethod(Consumer<DexMethod> consumer);

  // TODO(b/156715504): We should try to avoid the need for iterating pinned items.
  @Deprecated
  public abstract void forEachPinnedField(Consumer<DexField> consumer);

  public abstract KeepInfoCollection rewrite(NonIdentityGraphLens lens, InternalOptions options);

  public abstract KeepInfoCollection mutate(Consumer<MutableKeepInfoCollection> mutator);

  // Mutation interface for building up the keep info.
  public static class MutableKeepInfoCollection extends KeepInfoCollection {

    // These are typed at signatures but the interface should make sure never to allow access
    // directly with a signature. See the comment in KeepInfoCollection.
    private final Map<DexType, KeepClassInfo> keepClassInfo;
    private final Map<DexMethod, KeepMethodInfo> keepMethodInfo;
    private final Map<DexField, KeepFieldInfo> keepFieldInfo;

    // Map of applied rules for which keys may need to be mutated.
    private final Map<DexType, KeepClassInfo.Joiner> classRuleInstances;
    private final Map<DexField, KeepFieldInfo.Joiner> fieldRuleInstances;
    private final Map<DexMethod, KeepMethodInfo.Joiner> methodRuleInstances;

    MutableKeepInfoCollection() {
      this(
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>(),
          new IdentityHashMap<>());
    }

    private MutableKeepInfoCollection(
        Map<DexType, KeepClassInfo> keepClassInfo,
        Map<DexMethod, KeepMethodInfo> keepMethodInfo,
        Map<DexField, KeepFieldInfo> keepFieldInfo,
        Map<DexType, KeepClassInfo.Joiner> classRuleInstances,
        Map<DexField, KeepFieldInfo.Joiner> fieldRuleInstances,
        Map<DexMethod, KeepMethodInfo.Joiner> methodRuleInstances) {
      this.keepClassInfo = keepClassInfo;
      this.keepMethodInfo = keepMethodInfo;
      this.keepFieldInfo = keepFieldInfo;
      this.classRuleInstances = classRuleInstances;
      this.fieldRuleInstances = fieldRuleInstances;
      this.methodRuleInstances = methodRuleInstances;
    }

    public void removeKeepInfoForPrunedItems(Set<DexType> removedClasses) {
      keepClassInfo.keySet().removeIf(removedClasses::contains);
      keepFieldInfo.keySet().removeIf(field -> removedClasses.contains(field.getHolderType()));
      keepMethodInfo.keySet().removeIf(method -> removedClasses.contains(method.getHolderType()));
    }

    @Override
    public KeepInfoCollection rewrite(NonIdentityGraphLens lens, InternalOptions options) {
      Map<DexType, KeepClassInfo> newClassInfo = new IdentityHashMap<>(keepClassInfo.size());
      keepClassInfo.forEach(
          (type, info) -> {
            DexType newType = lens.lookupType(type);
            assert newType == type
                || !info.isPinned()
                || info.isMinificationAllowed(options)
                || info.isRepackagingAllowed(options);
            KeepClassInfo previous = newClassInfo.put(newType, info);
            assert previous == null;
          });
      Map<DexMethod, KeepMethodInfo> newMethodInfo = new IdentityHashMap<>(keepMethodInfo.size());
      keepMethodInfo.forEach(
          (method, info) -> {
            DexMethod newMethod = lens.getRenamedMethodSignature(method);
            assert !info.isPinned()
                || info.isMinificationAllowed(options)
                || newMethod.name == method.name;
            assert !info.isPinned() || newMethod.getArity() == method.getArity();
            assert !info.isPinned()
                || Streams.zip(
                        newMethod.getParameters().stream(),
                        method.getParameters().stream().map(lens::lookupType),
                        Object::equals)
                    .allMatch(x -> x);
            assert !info.isPinned()
                || newMethod.getReturnType() == lens.lookupType(method.getReturnType());
            KeepMethodInfo previous = newMethodInfo.put(newMethod, info);
            // TODO(b/169927809): Avoid collisions.
            // assert previous == null;
          });
      Map<DexField, KeepFieldInfo> newFieldInfo = new IdentityHashMap<>(keepFieldInfo.size());
      keepFieldInfo.forEach(
          (field, info) -> {
            DexField newField = lens.getRenamedFieldSignature(field);
            assert newField.name == field.name
                || !info.isPinned()
                || info.isMinificationAllowed(options);
            KeepFieldInfo previous = newFieldInfo.put(newField, info);
            assert previous == null;
          });
      return new MutableKeepInfoCollection(
          newClassInfo,
          newMethodInfo,
          newFieldInfo,
          rewriteRuleInstances(
              classRuleInstances,
              clazz -> {
                DexType rewritten = lens.lookupType(clazz);
                if (rewritten.isClassType()) {
                  return rewritten;
                }
                assert rewritten.isIntType();
                return null;
              },
              KeepClassInfo::newEmptyJoiner),
          rewriteRuleInstances(
              fieldRuleInstances, lens::getRenamedFieldSignature, KeepFieldInfo::newEmptyJoiner),
          rewriteRuleInstances(
              methodRuleInstances,
              lens::getRenamedMethodSignature,
              KeepMethodInfo::newEmptyJoiner));
    }

    private static <R, J extends KeepInfo.Joiner<J, ?, ?>> Map<R, J> rewriteRuleInstances(
        Map<R, J> ruleInstances, Function<R, R> rewriter, Supplier<J> newEmptyJoiner) {
      return MapUtils.transform(
          ruleInstances,
          IdentityHashMap::new,
          rewriter,
          Function.identity(),
          (joiner, otherJoiner) -> newEmptyJoiner.get().merge(joiner).merge(otherJoiner));
    }

    @Override
    void forEachRuleInstance(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        BiConsumer<DexProgramClass, KeepClassInfo.Joiner> classRuleInstanceConsumer,
        BiConsumer<ProgramField, KeepFieldInfo.Joiner> fieldRuleInstanceConsumer,
        BiConsumer<ProgramMethod, KeepMethodInfo.Joiner> methodRuleInstanceConsumer) {
      classRuleInstances.forEach(
          (type, ruleInstance) -> {
            DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
            if (clazz != null) {
              classRuleInstanceConsumer.accept(clazz, ruleInstance);
            }
          });
      fieldRuleInstances.forEach(
          (fieldReference, ruleInstance) -> {
            DexProgramClass holder =
                asProgramClassOrNull(appView.definitionFor(fieldReference.getHolderType()));
            ProgramField field = holder.lookupProgramField(fieldReference);
            if (field != null) {
              fieldRuleInstanceConsumer.accept(field, ruleInstance);
            }
          });
      methodRuleInstances.forEach(
          (methodReference, ruleInstance) -> {
            DexProgramClass holder =
                asProgramClassOrNull(appView.definitionFor(methodReference.getHolderType()));
            ProgramMethod method = holder.lookupProgramMethod(methodReference);
            if (method != null) {
              methodRuleInstanceConsumer.accept(method, ruleInstance);
            }
          });
    }

    void evaluateClassRule(DexProgramClass clazz, KeepClassInfo.Joiner minimumKeepInfo) {
      if (!minimumKeepInfo.isBottom()) {
        joinClass(clazz, joiner -> joiner.merge(minimumKeepInfo));
        classRuleInstances
            .computeIfAbsent(clazz.getType(), ignoreKey(KeepClassInfo::newEmptyJoiner))
            .merge(minimumKeepInfo);
      }
    }

    void evaluateFieldRule(ProgramField field, KeepFieldInfo.Joiner minimumKeepInfo) {
      if (!minimumKeepInfo.isBottom()) {
        joinField(field, joiner -> joiner.merge(minimumKeepInfo));
        fieldRuleInstances
            .computeIfAbsent(field.getReference(), ignoreKey(KeepFieldInfo::newEmptyJoiner))
            .merge(minimumKeepInfo);
      }
    }

    void evaluateMethodRule(ProgramMethod method, KeepMethodInfo.Joiner minimumKeepInfo) {
      if (!minimumKeepInfo.isBottom()) {
        joinMethod(method, joiner -> joiner.merge(minimumKeepInfo));
        methodRuleInstances
            .computeIfAbsent(method.getReference(), ignoreKey(KeepMethodInfo::newEmptyJoiner))
            .merge(minimumKeepInfo);
      }
    }

    @Override
    public KeepClassInfo getClassInfo(DexProgramClass clazz) {
      return keepClassInfo.getOrDefault(clazz.type, KeepClassInfo.bottom());
    }

    @Override
    public KeepMethodInfo getMethodInfo(DexEncodedMethod method, DexProgramClass holder) {
      assert method.getHolderType() == holder.type;
      return keepMethodInfo.getOrDefault(method.getReference(), KeepMethodInfo.bottom());
    }

    @Override
    public KeepFieldInfo getFieldInfo(DexEncodedField field, DexProgramClass holder) {
      assert field.getHolderType() == holder.type;
      return keepFieldInfo.getOrDefault(field.getReference(), KeepFieldInfo.bottom());
    }

    public void joinClass(DexProgramClass clazz, Consumer<? super KeepClassInfo.Joiner> fn) {
      KeepClassInfo info = getClassInfo(clazz);
      if (info.isTop()) {
        assert info == KeepClassInfo.top();
        return;
      }
      KeepClassInfo.Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepClassInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepClassInfo.put(clazz.type, joined);
      }
    }

    public void keepClass(DexProgramClass clazz) {
      joinClass(clazz, KeepInfo.Joiner::top);
    }

    public void pinClass(DexProgramClass clazz) {
      joinClass(clazz, KeepInfo.Joiner::pin);
    }

    public void joinMethod(ProgramMethod method, Consumer<? super KeepMethodInfo.Joiner> fn) {
      KeepMethodInfo info = getMethodInfo(method);
      if (info.isTop()) {
        assert info == KeepMethodInfo.top();
        return;
      }
      KeepMethodInfo.Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepMethodInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepMethodInfo.put(method.getReference(), joined);
      }
    }

    public void keepMethod(ProgramMethod method) {
      joinMethod(method, KeepInfo.Joiner::top);
    }

    public void pinMethod(ProgramMethod method) {
      joinMethod(method, KeepInfo.Joiner::pin);
    }

    // TODO(b/157700141): Avoid pinning/unpinning references.
    @Deprecated
    public void unsafeUnpinMethod(DexMethod method) {
      KeepMethodInfo info = keepMethodInfo.get(method);
      if (info != null && info.isPinned()) {
        keepMethodInfo.put(method, info.builder().unpin().build());
      }
    }

    public void unsetRequireAllowAccessModificationForRepackaging(ProgramDefinition definition) {
      if (definition.isProgramClass()) {
        DexProgramClass clazz = definition.asProgramClass();
        KeepClassInfo info = getClassInfo(clazz);
        keepClassInfo.put(
            clazz.getType(), info.builder().unsetRequireAccessModificationForRepackaging().build());
      } else if (definition.isProgramMethod()) {
        ProgramMethod method = definition.asProgramMethod();
        KeepMethodInfo info = getMethodInfo(method);
        keepMethodInfo.put(
            method.getReference(),
            info.builder().unsetRequireAccessModificationForRepackaging().build());
      } else if (definition.isProgramField()) {
        ProgramField field = definition.asProgramField();
        KeepFieldInfo info = getFieldInfo(field);
        keepFieldInfo.put(
            field.getReference(),
            info.builder().unsetRequireAccessModificationForRepackaging().build());
      } else {
        throw new Unreachable();
      }
    }

    public void joinField(ProgramField field, Consumer<? super KeepFieldInfo.Joiner> fn) {
      KeepFieldInfo info = getFieldInfo(field);
      if (info.isTop()) {
        assert info == KeepFieldInfo.top();
        return;
      }
      Joiner joiner = info.joiner();
      fn.accept(joiner);
      KeepFieldInfo joined = joiner.join();
      if (!info.equals(joined)) {
        keepFieldInfo.put(field.getReference(), joined);
      }
    }

    public void keepField(ProgramField field) {
      joinField(field, KeepInfo.Joiner::top);
    }

    public void pinField(ProgramField field) {
      joinField(field, KeepInfo.Joiner::pin);
    }

    @Override
    public KeepInfoCollection mutate(Consumer<MutableKeepInfoCollection> mutator) {
      mutator.accept(this);
      return this;
    }

    @Override
    public boolean verifyPinnedTypesAreLive(Set<DexType> liveTypes) {
      keepClassInfo.forEach(
          (type, info) -> {
            assert !info.isPinned() || liveTypes.contains(type);
          });
      return true;
    }

    @Override
    public void forEachPinnedType(Consumer<DexType> consumer) {
      keepClassInfo.forEach(
          (type, info) -> {
            if (info.isPinned()) {
              consumer.accept(type);
            }
          });
    }

    @Override
    public void forEachPinnedMethod(Consumer<DexMethod> consumer) {
      keepMethodInfo.forEach(
          (method, info) -> {
            if (info.isPinned()) {
              consumer.accept(method);
            }
          });
    }

    @Override
    public void forEachPinnedField(Consumer<DexField> consumer) {
      keepFieldInfo.forEach(
          (field, info) -> {
            if (info.isPinned()) {
              consumer.accept(field);
            }
          });
    }
  }
}
