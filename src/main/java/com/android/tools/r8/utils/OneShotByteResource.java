// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

class OneShotByteResource implements ProgramResource {

  private final Origin origin;
  private final Kind kind;
  private byte[] bytes;
  private final Set<String> classDescriptors;

  static ProgramResource create(
      Kind kind, Origin origin, byte[] bytes, Set<String> classDescriptors) {
    return new OneShotByteResource(origin, kind, bytes, classDescriptors);
  }

  private OneShotByteResource(
      Origin origin, Kind kind, byte[] bytes, Set<String> classDescriptors) {
    assert bytes != null;
    this.origin = origin;
    this.kind = kind;
    this.bytes = bytes;
    this.classDescriptors = classDescriptors;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Kind getKind() {
    return kind;
  }

  @Override
  public InputStream getByteStream() throws ResourceException {
    assert bytes != null;
    InputStream result = new ByteArrayInputStream(bytes);
    bytes = null;
    return result;
  }

  @Override
  public Set<String> getClassDescriptors() {
    return classDescriptors;
  }

  @Override
  @Deprecated
  public InputStream getStream() throws IOException {
    try {
      return getByteStream();
    } catch (ResourceException e) {
      throw new Unreachable(e);
    }
  }
}
