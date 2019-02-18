// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardWildcard.BackReference;
import com.android.tools.r8.shaking.ProguardWildcard.Pattern;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ProguardTypeMatcher {

  private static final String MATCH_ALL_PATTERN = "***";
  private static final String MATCH_ANY_ARG_SEQUENCE_PATTERN = "...";
  private static final String LEGACY_MATCH_CLASS_PATTERN = "*";
  private static final String MATCH_CLASS_PATTERN = "**";
  private static final String MATCH_BASIC_PATTERN = "%";

  private ProguardTypeMatcher() {
  }

  enum ClassOrType {
    CLASS,
    TYPE
  }

  // Evaluates this matcher on the given type.
  public abstract boolean matches(DexType type);

  // Evaluates this matcher on the given type, and on all types that have been merged into the given
  // type, if any.
  public final boolean matches(DexType type, AppView<? extends AppInfo> appView) {
    if (matches(type)) {
      return true;
    }
    if (appView.verticallyMergedClasses() != null) {
      return appView.verticallyMergedClasses().getSourcesFor(type).stream().anyMatch(this::matches);
    }
    return false;
  }

  protected Iterable<ProguardWildcard> getWildcards() {
    return Collections::emptyIterator;
  }

  static Iterable<ProguardWildcard> getWildcardsOrEmpty(ProguardTypeMatcher typeMatcher) {
    return typeMatcher == null ? Collections::emptyIterator : typeMatcher.getWildcards();
  }

  protected ProguardTypeMatcher materialize() {
    return this;
  }

  @Override
  public abstract String toString();

  public boolean isTripleDotPattern() {
    return false;
  }

  public static ProguardTypeMatcher create(
      IdentifierPatternWithWildcards identifierPatternWithWildcards,
      ClassOrType kind,
      DexItemFactory dexItemFactory) {
    if (identifierPatternWithWildcards == null || identifierPatternWithWildcards.pattern == null) {
      return null;
    }
    switch (identifierPatternWithWildcards.pattern) {
      case MATCH_ALL_PATTERN:
        return MatchAllTypes.MATCH_ALL_TYPES;
      case MATCH_ANY_ARG_SEQUENCE_PATTERN:
        return MatchAnyArgSequence.MATCH_ANY_ARG_SEQUENCE;
      case MATCH_CLASS_PATTERN:
        return MatchClassTypes.MATCH_CLASS_TYPES;
      case LEGACY_MATCH_CLASS_PATTERN:
        return MatchClassTypes.LEGACY_MATCH_CLASS_TYPES;
      case MATCH_BASIC_PATTERN:
        return MatchBasicTypes.MATCH_BASIC_TYPES;
      default:
        if (identifierPatternWithWildcards.wildcards.isEmpty()) {
          return new MatchSpecificType(dexItemFactory.createType(
              javaTypeToDescriptor(identifierPatternWithWildcards.pattern)));
        }
        return new MatchTypePattern(identifierPatternWithWildcards, kind);
    }
  }

  public static ProguardTypeMatcher create(DexType type) {
    return new MatchSpecificType(type);
  }

  public static ProguardTypeMatcher defaultAllMatcher() {
    return MatchAllTypes.MATCH_ALL_TYPES;
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  public DexType getSpecificType() {
    return null;
  }

  public final boolean matchesSpecificType() {
    return getSpecificType() != null;
  }

  private static class MatchAllTypes extends ProguardTypeMatcher {

    private static final ProguardTypeMatcher MATCH_ALL_TYPES = new MatchAllTypes();

    private final ProguardWildcard wildcard;

    MatchAllTypes() {
      this(new Pattern(MATCH_ALL_PATTERN));
    }

    private MatchAllTypes(ProguardWildcard wildcard) {
      this.wildcard = wildcard;
    }

    @Override
    public boolean matches(DexType type) {
      wildcard.setCaptured(type.toSourceString());
      return true;
    }

    @Override
    protected Iterable<ProguardWildcard> getWildcards() {
      return ImmutableList.of(wildcard);
    }

    @Override
    protected MatchAllTypes materialize() {
      return new MatchAllTypes(wildcard.materialize());
    }

    @Override
    public String toString() {
      return MATCH_ALL_PATTERN;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MatchAllTypes;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  private static class MatchAnyArgSequence extends ProguardTypeMatcher {

    private static final ProguardTypeMatcher MATCH_ANY_ARG_SEQUENCE = new MatchAnyArgSequence();

    @Override
    public boolean matches(DexType type) {
      throw new IllegalStateException();
    }

    @Override
    public String toString() {
      return MATCH_ANY_ARG_SEQUENCE_PATTERN;
    }

    @Override
    public boolean isTripleDotPattern() {
      return true;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MatchAnyArgSequence;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  private static class MatchClassTypes extends ProguardTypeMatcher {

    private static final ProguardTypeMatcher MATCH_CLASS_TYPES =
        new MatchClassTypes(MATCH_CLASS_PATTERN);
    private static final ProguardTypeMatcher LEGACY_MATCH_CLASS_TYPES =
        new MatchClassTypes(LEGACY_MATCH_CLASS_PATTERN);

    private final String pattern;
    private final ProguardWildcard wildcard;

    private MatchClassTypes(String pattern) {
      this(pattern, new Pattern(pattern));
    }

    private MatchClassTypes(String pattern, ProguardWildcard wildcard) {
      assert pattern.equals(LEGACY_MATCH_CLASS_PATTERN) || pattern.equals(MATCH_CLASS_PATTERN);
      this.pattern = pattern;
      this.wildcard = wildcard;
    }

    @Override
    public boolean matches(DexType type) {
      if (type.isClassType()) {
        wildcard.setCaptured(type.toSourceString());
        return true;
      }
      return false;
    }

    @Override
    protected Iterable<ProguardWildcard> getWildcards() {
      return ImmutableList.of(wildcard);
    }

    @Override
    protected MatchClassTypes materialize() {
      return new MatchClassTypes(pattern, wildcard.materialize());
    }

    @Override
    public String toString() {
      return pattern;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MatchClassTypes && pattern.equals(((MatchClassTypes) o).pattern);
    }

    @Override
    public int hashCode() {
      return pattern.hashCode();
    }
  }

  private static class MatchBasicTypes extends ProguardTypeMatcher {

    private static final ProguardTypeMatcher MATCH_BASIC_TYPES = new MatchBasicTypes();

    private final ProguardWildcard wildcard;

    MatchBasicTypes() {
      this(new Pattern(MATCH_BASIC_PATTERN));
    }

    private MatchBasicTypes(ProguardWildcard wildcard) {
      this.wildcard = wildcard;
    }

    @Override
    public boolean matches(DexType type) {
      if (type.isPrimitiveType()) {
        wildcard.setCaptured(type.toSourceString());
        return true;
      }
      return false;
    }

    @Override
    protected Iterable<ProguardWildcard> getWildcards() {
      return ImmutableList.of(wildcard);
    }

    @Override
    protected MatchBasicTypes materialize() {
      return new MatchBasicTypes(wildcard.materialize());
    }

    @Override
    public String toString() {
      return MATCH_BASIC_PATTERN;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MatchBasicTypes;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  public static class MatchSpecificType extends ProguardTypeMatcher {

    public final DexType type;

    private MatchSpecificType(DexType type) {
      this.type = type;
    }

    @Override
    public boolean matches(DexType type) {
      return this.type == type;
    }

    @Override
    public String toString() {
      return type.toSourceString();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof MatchSpecificType) {
        return type.equals(((MatchSpecificType) o).type);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }

    @Override
    public DexType getSpecificType() {
      return type;
    }
  }

  private static class MatchTypePattern extends ProguardTypeMatcher {

    private final String pattern;
    private final List<ProguardWildcard> wildcards;
    private final ClassOrType kind;

    private MatchTypePattern(
        IdentifierPatternWithWildcards identifierPatternWithWildcards, ClassOrType kind) {
      this.pattern = identifierPatternWithWildcards.pattern;
      this.wildcards = identifierPatternWithWildcards.wildcards;
      this.kind = kind;
    }

    @Override
    public boolean matches(DexType type) {
      // TODO(herhut): Translate pattern to work on descriptors instead.
      String typeName = type.toSourceString();
      boolean matched = matchClassOrTypeNameImpl(pattern, 0, typeName, 0, wildcards, 0, kind);
      if (!matched) {
        wildcards.forEach(ProguardWildcard::clearCaptured);
      }
      return matched;
    }

    @Override
    protected Iterable<ProguardWildcard> getWildcards() {
      return wildcards;
    }

    @Override
    protected MatchTypePattern materialize() {
      List<ProguardWildcard> materializedWildcards =
          wildcards.stream().map(ProguardWildcard::materialize).collect(Collectors.toList());
      IdentifierPatternWithWildcards identifierPatternWithMaterializedWildcards =
          new IdentifierPatternWithWildcards(pattern, materializedWildcards);
      return new MatchTypePattern(identifierPatternWithMaterializedWildcards, kind);
    }

    private static boolean matchClassOrTypeNameImpl(
        String pattern, int patternIndex,
        String name, int nameIndex,
        List<ProguardWildcard> wildcards, int wildcardIndex,
        ClassOrType kind) {
      ProguardWildcard wildcard;
      Pattern wildcardPattern;
      BackReference backReference;
      for (int i = patternIndex; i < pattern.length(); i++) {
        char patternChar = pattern.charAt(i);
        switch (patternChar) {
          case '*':
            wildcard = wildcards.get(wildcardIndex);
            assert wildcard.isPattern();
            wildcardPattern = wildcard.asPattern();

            boolean includeSeparators = pattern.length() > (i + 1) && pattern.charAt(i + 1) == '*';
            boolean includeAll =
                includeSeparators && pattern.length() > (i + 2) && pattern.charAt(i + 2) == '*';
            int nextPatternIndex = i + 1;
            if (includeAll) {
              nextPatternIndex += 2;
            } else if (includeSeparators) {
              nextPatternIndex += 1;
            }

            // Fast cases for the common case where a pattern ends with  '*', '**', or '***'.
            if (nextPatternIndex == pattern.length()) {
              wildcardPattern.setCaptured(name.substring(nameIndex));
              if (includeAll) {
                return true;
              }
              if (includeSeparators) {
                return kind == ClassOrType.CLASS || !isArrayType(name);
              }
              boolean hasSeparators = containsSeparatorsStartingAt(name, nameIndex);
              return !hasSeparators && (kind == ClassOrType.CLASS || !isArrayType(name));
            }

            // Match the rest of the pattern against the (non-empty) rest of the class name.
            for (int nextNameIndex = nameIndex; nextNameIndex < name.length(); nextNameIndex++) {
              wildcardPattern.setCaptured(name.substring(nameIndex, nextNameIndex));
              if (!includeSeparators) {
                if (name.charAt(nextNameIndex) == '.') {
                  return matchClassOrTypeNameImpl(
                      pattern,
                      nextPatternIndex,
                      name,
                      nextNameIndex,
                      wildcards,
                      wildcardIndex + 1,
                      kind);
                }
              }
              if (kind == ClassOrType.TYPE && name.charAt(nextNameIndex) == '[') {
                return matchClassOrTypeNameImpl(
                    pattern, nextPatternIndex, name, nextNameIndex, wildcards, wildcardIndex + 1,
                    kind);
              }
              if (matchClassOrTypeNameImpl(
                  pattern, nextPatternIndex, name, nextNameIndex, wildcards, wildcardIndex + 1,
                  kind)) {
                return true;
              }
            }

            // Finally, check the case where the '*', '**', or '***' eats all of the class name.
            wildcardPattern.setCaptured(name.substring(nameIndex));
            return matchClassOrTypeNameImpl(
                pattern, nextPatternIndex, name, name.length(), wildcards, wildcardIndex + 1, kind);

          case '?':
            wildcard = wildcards.get(wildcardIndex);
            assert wildcard.isPattern();
            if (nameIndex == name.length() || name.charAt(nameIndex) == '.') {
              return false;
            }
            wildcardPattern = wildcard.asPattern();
            wildcardPattern.setCaptured(name.substring(nameIndex, nameIndex + 1));
            nameIndex++;
            wildcardIndex++;
            break;

          case '<':
            wildcard = wildcards.get(wildcardIndex);
            assert wildcard.isBackReference();
            backReference = wildcard.asBackReference();
            String captured = backReference.getCaptured();
            if (captured == null
                || name.length() < nameIndex + captured.length()
                || !captured.equals(name.substring(nameIndex, nameIndex + captured.length()))) {
              return false;
            }
            nameIndex = nameIndex + captured.length();
            wildcardIndex++;
            i = pattern.indexOf(">", i);
            break;

          default:
            if (nameIndex == name.length() || patternChar != name.charAt(nameIndex++)) {
              return false;
            }
            break;
        }
      }
      return nameIndex == name.length();
    }

    private static boolean containsSeparatorsStartingAt(String className, int nameIndex) {
      return className.indexOf('.', nameIndex) != -1;
    }

    private static boolean isArrayType(String type) {
      int length = type.length();
      if (length < 2) {
        return false;
      }
      return type.charAt(length - 1) == ']' && type.charAt(length - 2) == '[';
    }

    @Override
    public String toString() {
      return pattern;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof MatchTypePattern) {
        MatchTypePattern that = (MatchTypePattern) o;
        return kind.equals(that.kind) && pattern.equals(that.pattern);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return pattern.hashCode() * 7 + kind.hashCode();
    }
  }
}
