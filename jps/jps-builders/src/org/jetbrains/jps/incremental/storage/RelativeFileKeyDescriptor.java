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
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Relativator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public final class RelativeFileKeyDescriptor implements KeyDescriptor<File> {
  private final byte[] buffer = IOUtil.allocReadWriteUTFBuffer();
  private Relativator myRelativator = null;

  public RelativeFileKeyDescriptor(@NotNull Relativator relativator) {
    myRelativator = relativator;
  }

  @Override
  public void save(DataOutput out, @NotNull File value) throws IOException {
    String relativePath = myRelativator.getRelativePath(value);
    IOUtil.writeUTFFast(buffer, out, relativePath);
  }

  @Override
  public File read(DataInput in) throws IOException {
    String absolutePath = myRelativator.getAbsolutePath(IOUtil.readUTFFast(buffer, in));
    return new File(absolutePath);
  }

  @Override
  public int getHashCode(File value) {
    String relativePath = myRelativator.getRelativePath(value);
    return FileUtil.pathHashCode(relativePath);
  }

  @Override
  public boolean isEqual(File val1, File val2) {
    String relativePath1 = myRelativator.getRelativePath(val1);
    String relativePath2 = myRelativator.getRelativePath(val2);
    return FileUtil.pathsEqual(relativePath1, relativePath2);
  }
}
