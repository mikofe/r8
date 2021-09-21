// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordShrinkField {

  record Person(String name, int age, int unused) {
    Person(String name, int age) {
      this(name, age, -1);
    }
  }

  public static void main(String[] args) {
    Person jane = new Person("Jane Doe", 42);
    Person bob = new Person("Bob", 42);
    System.out.println(jane);
    System.out.println(bob);
  }
}
