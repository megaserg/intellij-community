/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Relativator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public class RelativePathStringDescriptor extends EnumeratorStringDescriptor {
  private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();
  private Relativator myRelativator = null;

  public RelativePathStringDescriptor(Relativator relativator) {
    myRelativator = relativator;
  }

  @Override
  public void save(final DataOutput storage, @NotNull final String value) throws IOException {
    String relativePath = myRelativator.getRelativePath(value);
    IOUtil.writeUTFFast(buffer, storage, relativePath);
  }

  @Override
  public String read(final DataInput storage) throws IOException {
    String absolutePath = myRelativator.getAbsolutePath(IOUtil.readUTFFast(buffer, storage));
    return absolutePath;
  }

  @Override
  public int getHashCode(String value) {
    String relativePath = myRelativator.getRelativePath(value);
    return FileUtil.pathHashCode(relativePath);
  }

  @Override
  public boolean isEqual(String val1, String val2) {
    String relativePath1 = myRelativator.getRelativePath(val1);
    String relativePath2 = myRelativator.getRelativePath(val2);
    return FileUtil.pathsEqual(relativePath1, relativePath2);
  }
}
