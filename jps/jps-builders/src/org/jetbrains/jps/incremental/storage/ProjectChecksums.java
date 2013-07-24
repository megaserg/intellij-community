/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public class ProjectChecksums {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.ProjectChecksums");
  private static final String CHECKSUM_STORAGE = "checksums";
  private final ChecksumStorage myChecksums;
  private final File myChecksumsRoot;

  public ProjectChecksums(final File dataStorageRoot, BuildTargetsState targetsState, File projectRootFile) throws IOException {
    myChecksumsRoot = new File(dataStorageRoot, CHECKSUM_STORAGE);
    /*
     * Could be switched to InMemoryChecksumStorage.
     */
    myChecksums = new ChecksumStorage(new File(myChecksumsRoot, "data"), targetsState, projectRootFile);
    //myChecksums = new InMemoryChecksumStorage(new File(myChecksumsRoot, "data"), targetsState);
  }

  public ChecksumStorage getStorage() {
    return myChecksums;
  }

  public void clean() throws IOException {
    final ChecksumStorage checksums = myChecksums;
    if (checksums != null) {
      checksums.wipe();
    }
    else {
      FileUtil.delete(myChecksumsRoot);
    }
  }

  public void close() {
    final ChecksumStorage checksums = myChecksums;
    if (checksums != null) {
      try {
        checksums.close();
      }
      catch (IOException e) {
        LOG.error(e);
        FileUtil.delete(myChecksumsRoot);
      }
    }
  }
}
