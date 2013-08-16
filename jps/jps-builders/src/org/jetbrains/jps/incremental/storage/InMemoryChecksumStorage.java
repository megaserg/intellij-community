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

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Relativator;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */

/**
 * This is a thread-unsafe in-memory implementation of ChecksumStorage, where some methods of AbstractStateStorage (which is a superclass
 * of ChecksumStorage) are replaced with ones that use HashMap instead of PersistentHashMap. Very, very bad design.
 */
public class InMemoryChecksumStorage extends ChecksumStorage implements Checksums {
  private HashMap<File, ChecksumPerTarget[]> storage = new HashMap<File, ChecksumPerTarget[]>();

  public InMemoryChecksumStorage(File storePath, BuildTargetsState targetsState, Relativator relativator) throws IOException {
    super(storePath, targetsState, relativator);
  }

  @Override
  public ChecksumPerTarget[] getState(File key) throws IOException {
    return storage.get(key);
  }

  @Override
  public void update(File key, @Nullable ChecksumPerTarget[] state) throws IOException {
    if (state != null) {
      storage.put(key, state);
    }
    else {
      storage.remove(key);
    }
  }

  @Override
  public void remove(File key) throws IOException {
    storage.remove(key);
  }
}
