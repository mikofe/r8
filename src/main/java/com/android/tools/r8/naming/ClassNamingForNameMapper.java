// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MemberNaming.Signature.SignatureKind;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Stores name information for a class.
 * <p>
 * This includes how the class was renamed and information on the classes members.
 */
public class ClassNamingForNameMapper implements ClassNaming {

  public static class Builder extends ClassNaming.Builder {

    private final String originalName;
    private final String renamedName;
    private final Map<MethodSignature, MemberNaming> methodMembers = Maps.newHashMap();
    private final Map<FieldSignature, MemberNaming> fieldMembers = Maps.newHashMap();
    private final Map<String, List<MappedRange>> mappedRangesByName = Maps.newHashMap();
    private final Map<String, List<MemberNaming>> mappedFieldNamingsByName = Maps.newHashMap();
    private final List<MappingInformation> additionalMappingInfo = new ArrayList<>();
    private final BiConsumer<String, String> originalSourceFileConsumer;

    private Builder(
        String renamedName,
        String originalName,
        BiConsumer<String, String> originalSourceFileConsumer) {
      this.originalName = originalName;
      this.renamedName = renamedName;
      this.originalSourceFileConsumer = originalSourceFileConsumer;
    }

    @Override
    public ClassNaming.Builder addMemberEntry(MemberNaming entry) {
      if (entry.isMethodNaming()) {
        methodMembers.put(entry.getRenamedSignature().asMethodSignature(), entry);
      } else {
        fieldMembers.put(entry.getRenamedSignature().asFieldSignature(), entry);
        mappedFieldNamingsByName
            .computeIfAbsent(entry.getRenamedName(), m -> new ArrayList<>())
            .add(entry);
      }
      return this;
    }

    @Override
    public ClassNamingForNameMapper build() {
      Map<String, MappedRangesOfName> map;

      if (mappedRangesByName.isEmpty()) {
        map = Collections.emptyMap();
      } else {
        map = new HashMap<>(mappedRangesByName.size());
        for (Map.Entry<String, List<MappedRange>> entry : mappedRangesByName.entrySet()) {
          map.put(entry.getKey(), new MappedRangesOfName(entry.getValue()));
        }
      }

      return new ClassNamingForNameMapper(
          renamedName,
          originalName,
          methodMembers,
          fieldMembers,
          map,
          mappedFieldNamingsByName,
          additionalMappingInfo);
    }

    /** The parameters are forwarded to MappedRange constructor, see explanation there. */
    @Override
    public MappedRange addMappedRange(
        Range minifiedRange,
        MemberNaming.MethodSignature originalSignature,
        Range originalRange,
        String renamedName) {
      MappedRange range =
          new MappedRange(minifiedRange, originalSignature, originalRange, renamedName);
      mappedRangesByName.computeIfAbsent(renamedName, k -> new ArrayList<>()).add(range);
      return range;
    }

    @Override
    public void addMappingInformation(
        MappingInformation info, Consumer<MappingInformation> onProhibitedAddition) {
      for (MappingInformation existing : additionalMappingInfo) {
        if (!existing.allowOther(info)) {
          onProhibitedAddition.accept(existing);
          return;
        }
      }
      additionalMappingInfo.add(info);
      if (info.isFileNameInformation()) {
        originalSourceFileConsumer.accept(originalName, info.asFileNameInformation().getFileName());
      }
    }
  }

  /** List of MappedRanges that belong to the same renamed name. */
  public static class MappedRangesOfName {
    private final List<MappedRange> mappedRanges;

    public MappedRangesOfName(List<MappedRange> mappedRanges) {
      this.mappedRanges = mappedRanges;
    }

    /**
     * Return the first MappedRange that contains {@code line}. Return general MappedRange ("a() ->
     * b") if no concrete mapping found or null if nothing found.
     */
    public MappedRange firstRangeForLine(int line) {
      MappedRange bestRange = null;
      for (MappedRange range : mappedRanges) {
        if (range.minifiedRange == null) {
          if (bestRange == null) {
            // This is an "a() -> b" mapping (no concrete line numbers), remember this if there'll
            // be no better one.
            bestRange = range;
          }
        } else if (range.minifiedRange.contains(line)) {
          // Concrete minified range found ("x:y:a()[:u[:v]] -> b")
          return range;
        }
      }
      return bestRange;
    }

    /**
     * Search for a MappedRange where the minified range contains the specified {@code line} and
     * return that and the subsequent MappedRanges with the same minified range. Return general
     * MappedRange ("a() -> b") if no concrete mapping found or empty list if nothing found.
     */
    public List<MappedRange> allRangesForLine(int line) {
      return allRangesForLine(line, true);
    }

    /**
     * Search for a MappedRange where the minified range contains the specified {@code line} and
     * return that and the subsequent MappedRanges with the same minified range.
     *
     * @param line The line number to find the range for
     * @param takeFirstWithNoLineRange Specify if no range is found, to take a general one that.
     * @return The list with all ranges for line.
     */
    public List<MappedRange> allRangesForLine(int line, boolean takeFirstWithNoLineRange) {
      MappedRange noLineRange = null;
      for (int i = 0; i < mappedRanges.size(); ++i) {
        MappedRange rangeI = mappedRanges.get(i);
        if (rangeI.minifiedRange == null) {
          if (noLineRange == null && takeFirstWithNoLineRange) {
            // This is an "a() -> b" mapping (no concrete line numbers), remember this if there'll
            // be no better one.
            noLineRange = rangeI;
          }
        } else if (rangeI.minifiedRange.contains(line)) {
          // Concrete minified range found ("x:y:a()[:u[:v]] -> b")
          int j = i + 1;
          for (; j < mappedRanges.size(); ++j) {
            if (!Objects.equals(mappedRanges.get(j).minifiedRange, rangeI.minifiedRange)) {
              break;
            }
          }
          return mappedRanges.subList(i, j);
        }
      }
      return noLineRange == null ? Collections.emptyList() : Collections.singletonList(noLineRange);
    }

    public List<MappedRange> getMappedRanges() {
      return mappedRanges;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MappedRangesOfName that = (MappedRangesOfName) o;

      return mappedRanges.equals(that.mappedRanges);
    }

    @Override
    public int hashCode() {
      return mappedRanges.hashCode();
    }
  }

  static Builder builder(
      String renamedName,
      String originalName,
      BiConsumer<String, String> originalSourceFileConsumer) {
    return new Builder(renamedName, originalName, originalSourceFileConsumer);
  }

  public final String originalName;
  public final String renamedName;

  /**
   * Mapping from the renamed signature to the naming information for a member.
   *
   * <p>A renamed signature is a signature where the member's name has been renamed but not the type
   * information.
   */
  private final ImmutableMap<MethodSignature, MemberNaming> methodMembers;
  private final ImmutableMap<FieldSignature, MemberNaming> fieldMembers;

  /** Map of renamed name -> MappedRangesOfName */
  public final Map<String, MappedRangesOfName> mappedRangesByRenamedName;

  public final Map<String, List<MemberNaming>> mappedFieldNamingsByName;

  private final List<MappingInformation> additionalMappingInfo;

  private ClassNamingForNameMapper(
      String renamedName,
      String originalName,
      Map<MethodSignature, MemberNaming> methodMembers,
      Map<FieldSignature, MemberNaming> fieldMembers,
      Map<String, MappedRangesOfName> mappedRangesByRenamedName,
      Map<String, List<MemberNaming>> mappedFieldNamingsByName,
      List<MappingInformation> additionalMappingInfo) {
    this.renamedName = renamedName;
    this.originalName = originalName;
    this.methodMembers = ImmutableMap.copyOf(methodMembers);
    this.fieldMembers = ImmutableMap.copyOf(fieldMembers);
    this.mappedRangesByRenamedName = mappedRangesByRenamedName;
    this.mappedFieldNamingsByName = mappedFieldNamingsByName;
    this.additionalMappingInfo = additionalMappingInfo;
  }

  public List<MappingInformation> getAdditionalMappingInfo() {
    return Collections.unmodifiableList(additionalMappingInfo);
  }

  public MappedRangesOfName getMappedRangesForRenamedName(String renamedName) {
    return mappedRangesByRenamedName.get(renamedName);
  }

  public boolean isEmpty() {
    return methodMembers.isEmpty() && fieldMembers.isEmpty();
  }

  public ClassNamingForNameMapper combine(ClassNamingForNameMapper otherMapping) {
    if (!originalName.equals(otherMapping.originalName)) {
      throw new RuntimeException(
          "Cannot combine mapping for "
              + renamedName
              + " because it maps back to both "
              + originalName
              + " and "
              + otherMapping.originalName
              + ".");
    }
    if (!renamedName.equals(otherMapping.renamedName)) {
      throw new RuntimeException(
          "Cannot combine mapping for "
              + originalName
              + " because it maps forward to both "
              + originalName
              + " and "
              + otherMapping.originalName
              + ".");
    }
    if (this.isEmpty()) {
      return otherMapping;
    } else if (otherMapping.isEmpty()) {
      return this;
    } else {
      throw new RuntimeException("R8 Retrace do not support merging of partial class mappings.");
    }
  }

  @Override
  public MemberNaming lookup(Signature renamedSignature) {
    if (renamedSignature.kind() == SignatureKind.METHOD) {
      assert renamedSignature instanceof MethodSignature;
      return methodMembers.get(renamedSignature);
    } else {
      assert renamedSignature.kind() == SignatureKind.FIELD;
      assert renamedSignature instanceof FieldSignature;
      return fieldMembers.get(renamedSignature);
    }
  }

  @Override
  public MemberNaming lookupByOriginalSignature(Signature original) {
    if (original.kind() == SignatureKind.METHOD) {
      for (MemberNaming memberNaming: methodMembers.values()) {
        if (memberNaming.signature.equals(original)) {
          return memberNaming;
        }
      }
      return null;
    } else {
      assert original.kind() == SignatureKind.FIELD;
      for (MemberNaming memberNaming : fieldMembers.values()) {
        if (memberNaming.signature.equals(original)) {
          return memberNaming;
        }
      }
      return null;
    }
  }

  public List<MemberNaming> lookupByOriginalName(String originalName) {
    List<MemberNaming> result = new ArrayList<>();
    for (MemberNaming naming : methodMembers.values()) {
      if (naming.signature.name.equals(originalName)) {
        result.add(naming);
      }
    }
    for (MemberNaming naming : fieldMembers.values()) {
      if (naming.signature.name.equals(originalName)) {
        result.add(naming);
      }
    }
    return result;
  }

  @Override
  public <T extends Throwable> void forAllMemberNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    forAllFieldNaming(consumer);
    forAllMethodNaming(consumer);
  }

  @Override
  public <T extends Throwable> void forAllFieldNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : fieldMembers.values()) {
      consumer.accept(naming);
    }
  }

  public Collection<MemberNaming> allFieldNamings() {
    return fieldMembers.values();
  }

  @Override
  public <T extends Throwable> void forAllMethodNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : methodMembers.values()) {
      consumer.accept(naming);
    }
  }

  public Collection<MemberNaming> allMethodNamings() {
    return methodMembers.values();
  }

  void write(ChainableStringConsumer consumer) {
    consumer.accept(originalName).accept(" -> ").accept(renamedName).accept(":\n");

    // Print all additional mapping information.
    additionalMappingInfo.forEach(info -> consumer.accept("# " + info.serialize()).accept("\n"));

    // Print field member namings.
    forAllFieldNaming(m -> consumer.accept("    ").accept(m.toString()).accept("\n"));

    // Sort MappedRanges by sequence number to restore construction order (original Proguard-map
    // input).
    List<MappedRange> mappedRangesSorted = new ArrayList<>();
    for (MappedRangesOfName ranges : mappedRangesByRenamedName.values()) {
      mappedRangesSorted.addAll(ranges.mappedRanges);
    }
    mappedRangesSorted.sort(Comparator.comparingInt(range -> range.sequenceNumber));
    for (MappedRange range : mappedRangesSorted) {
      consumer.accept("    ").accept(range.toString()).accept("\n");
      for (MappingInformation info : range.additionalMappingInfo) {
        consumer.accept("      # ").accept(info.serialize()).accept("\n");
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    write(ChainableStringConsumer.wrap(builder::append));
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassNamingForNameMapper)) {
      return false;
    }

    ClassNamingForNameMapper that = (ClassNamingForNameMapper) o;

    return originalName.equals(that.originalName)
        && renamedName.equals(that.renamedName)
        && methodMembers.equals(that.methodMembers)
        && fieldMembers.equals(that.fieldMembers)
        && mappedRangesByRenamedName.equals(that.mappedRangesByRenamedName);
  }

  @Override
  public int hashCode() {
    int result = originalName.hashCode();
    result = 31 * result + renamedName.hashCode();
    result = 31 * result + methodMembers.hashCode();
    result = 31 * result + fieldMembers.hashCode();
    result = 31 * result + mappedRangesByRenamedName.hashCode();
    return result;
  }

  /**
   * MappedRange describes an (original line numbers, signature) <-> (minified line numbers)
   * mapping. It can describe 3 different things:
   *
   * <p>1. The method is renamed. The original source lines are preserved. The corresponding
   * Proguard-map syntax is "a(...) -> b"
   *
   * <p>2. The source lines of a method in the original range are renumbered to the minified range.
   * In this case the {@link MappedRange#originalRange} is either a {@code Range} or null,
   * indicating that the original range is unknown or is the same as the {@link
   * MappedRange#minifiedRange}. The corresponding Proguard-map syntax is "x:y:a(...) -> b" or
   * "x:y:a(...):u:v -> b"
   *
   * <p>3. The source line of a method is the inlining caller of the previous {@code MappedRange}.
   * In this case the {@link MappedRange@originalRange} is either an {@code int} or null, indicating
   * that the original source line is unknown, or may be identical to a line of the minified range.
   * The corresponding Proguard-map syntax is "x:y:a(...) -> b" or "x:y:a(...):u -> b"
   */
  public static class MappedRange {

    private static int nextSequenceNumber = 0;

    private synchronized int getNextSequenceNumber() {
      return nextSequenceNumber++;
    }

    public final Range minifiedRange; // Can be null, if so then originalRange must also be null.
    public final MethodSignature signature;
    public final Range originalRange;
    public final String renamedName;

    /**
     * The sole purpose of {@link #sequenceNumber} is to preserve the order of members read from a
     * Proguard-map.
     */
    private final int sequenceNumber = getNextSequenceNumber();

    private List<MappingInformation> additionalMappingInfo = new ArrayList<>();

    private MappedRange(
        Range minifiedRange, MethodSignature signature, Range originalRange, String renamedName) {
      this.minifiedRange = minifiedRange;
      this.signature = signature;
      this.originalRange = originalRange;
      this.renamedName = renamedName;
    }

    public void addMappingInformation(
        MappingInformation info, Consumer<MappingInformation> onProhibitedAddition) {
      for (MappingInformation existing : additionalMappingInfo) {
        if (!existing.allowOther(info)) {
          onProhibitedAddition.accept(existing);
          return;
        }
      }
      additionalMappingInfo.add(info);
    }

    public boolean isCompilerSynthesized() {
      for (MappingInformation info : additionalMappingInfo) {
        if (info.isCompilerSynthesizedMappingInformation() || info.isOutlineMappingInformation()) {
          return true;
        }
      }
      return false;
    }

    public boolean isOutlineFrame() {
      for (MappingInformation info : additionalMappingInfo) {
        if (info.isOutlineMappingInformation()) {
          return true;
        }
      }
      return false;
    }

    public <T> List<T> filter(
        Predicate<MappingInformation> predicate, Function<MappingInformation, T> mapper) {
      ImmutableList.Builder<T> builder = ImmutableList.builder();
      for (MappingInformation mappingInformation : additionalMappingInfo) {
        if (predicate.test(mappingInformation)) {
          builder.add(mapper.apply(mappingInformation));
        }
      }
      return builder.build();
    }

    public List<OutlineCallsiteMappingInformation> getOutlineCallsiteInformation() {
      return filter(
          MappingInformation::isOutlineCallsiteInformation,
          MappingInformation::asOutlineCallsiteInformation);
    }

    public List<RewriteFrameMappingInformation> getRewriteFrameMappingInformation() {
      return filter(
          MappingInformation::isRewriteFrameMappingInformation,
          MappingInformation::asRewriteFrameMappingInformation);
    }

    public int getOriginalLineNumber(int lineNumberAfterMinification) {
      if (minifiedRange == null) {
        // General mapping without concrete line numbers: "a() -> b"
        return lineNumberAfterMinification;
      }
      assert minifiedRange.contains(lineNumberAfterMinification);
      if (originalRange == null) {
        // Concrete identity mapping: "x:y:a() -> b"
        return lineNumberAfterMinification;
      } else {
        // "x:y:a():u:v -> b"
        if (originalRange.to == originalRange.from) {
          // This is a single line mapping which we should report as the actual line.
          return originalRange.to;
        }
        return originalRange.from + lineNumberAfterMinification - minifiedRange.from;
      }
    }

    public int getFirstLineNumberOfOriginalRange() {
      if (originalRange == null) {
        return 0;
      } else {
        return originalRange.from;
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (minifiedRange != null) {
        builder.append(minifiedRange).append(':');
      }
      builder.append(signature);
      if (originalRange != null && !originalRange.equals(minifiedRange)) {
        builder.append(":").append(originalRange);
      }
      builder.append(" -> ").append(renamedName);
      return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
      // sequenceNumber is intentionally omitted from equality test since it doesn't contribute to
      // identity.
      if (this == o) {
        return true;
      }
      if (!(o instanceof MappedRange)) {
        return false;
      }

      MappedRange that = (MappedRange) o;

      return Objects.equals(minifiedRange, that.minifiedRange)
          && Objects.equals(originalRange, that.originalRange)
          && signature.equals(that.signature)
          && renamedName.equals(that.renamedName);
    }

    @Override
    public int hashCode() {
      // sequenceNumber is intentionally omitted from hashCode since it's not used in equality test.
      int result = Objects.hashCode(minifiedRange);
      result = 31 * result + Objects.hashCode(originalRange);
      result = 31 * result + signature.hashCode();
      result = 31 * result + renamedName.hashCode();
      return result;
    }

    public List<MappingInformation> getAdditionalMappingInfo() {
      return additionalMappingInfo;
    }
  }
}

