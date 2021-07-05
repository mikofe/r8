// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class MapUtils {

  public static <K, V> Map<K, V> clone(
      Map<K, V> mapToClone, Map<K, V> newMap, Function<V, V> valueCloner) {
    mapToClone.forEach((key, value) -> newMap.put(key, valueCloner.apply(value)));
    return newMap;
  }

  public static <K, V> K firstKey(Map<K, V> map) {
    return map.keySet().iterator().next();
  }

  public static <K, V> V firstValue(Map<K, V> map) {
    return map.values().iterator().next();
  }

  public static <T, R> Function<T, R> ignoreKey(Supplier<R> supplier) {
    return ignore -> supplier.get();
  }

  public static <K, V> IdentityHashMap<K, V> newIdentityHashMap(BiForEachable<K, V> forEachable) {
    IdentityHashMap<K, V> map = new IdentityHashMap<>();
    forEachable.forEach(map::put);
    return map;
  }

  public static <T> void removeIdentityMappings(Map<T, T> map) {
    map.entrySet().removeIf(entry -> entry.getKey() == entry.getValue());
  }

  public static String toString(Map<?, ?> map) {
    return StringUtils.join(
        ",", map.entrySet(), entry -> entry.getKey() + ":" + entry.getValue(), BraceType.TUBORG);
  }

  public static <K, V> Map<K, V> transform(
      Map<K, V> map,
      IntFunction<Map<K, V>> factory,
      Function<K, K> keyMapping,
      Function<V, V> valueMapping,
      BiFunction<V, V, V> valueMerger) {
    Map<K, V> result = factory.apply(map.size());
    map.forEach(
        (key, value) -> {
          K newKey = keyMapping.apply(key);
          if (newKey == null) {
            return;
          }
          V newValue = valueMapping.apply(value);
          V existingValue = result.put(newKey, newValue);
          if (existingValue != null) {
            result.put(newKey, valueMerger.apply(existingValue, newValue));
          }
        });
    return result;
  }
}
