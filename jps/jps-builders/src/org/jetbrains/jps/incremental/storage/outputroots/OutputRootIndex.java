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
package org.jetbrains.jps.incremental.storage.outputroots;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.CompileContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Sergey Serebryakov
 */

/*
 * Optimization idea: after building the set, remove the roots that are descendants of some other root
 */
public class OutputRootIndex {
  // The set contains files with absolute paths, but they are serialized to the file as relative ones.
  private final Set<File> myOutputRoots;
  private final File myProjectBaseDir;

  public OutputRootIndex(File projectBaseDir, CompileContext context, Collection<? extends BuildTarget<?>> allTargets) {
    myProjectBaseDir = projectBaseDir;

    myOutputRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    for (BuildTarget<?> target : allTargets) {
      myOutputRoots.addAll(target.getOutputRoots(context));
    }
  }

  public OutputRootIndex(File file, File projectBaseDir) throws FileNotFoundException {
    myProjectBaseDir = projectBaseDir;

    Scanner in = new Scanner(file);
    myOutputRoots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    while (in.hasNextLine()) {
      String path = in.nextLine();
      if (!path.isEmpty()) {
        myOutputRoots.add(toCanonicalFile(new File(projectBaseDir, path)));
      }
    }
  }

  private static File toCanonicalFile(File file) {
    return new File(FileUtil.toCanonicalPath(file.getPath()));
  }

  public static String getFilenameByOutputRoot(String relativeOutputRootPath) {
    return "outputroot_" + relativeOutputRootPath.hashCode();
  }

  /*public static String getFilenameByOutputRoot(File relativeOutputRoot) {
    return getFilenameByOutputRoot(relativeOutputRoot.getPath());
  }*/

  public Collection<File> getOutputRootsByOutputFile(@NotNull File file) {
    File current = file;
    List<File> result = new ArrayList<File>();

    while (current != null) {
      if (myOutputRoots.contains(current)) {
        result.add(current);
      }
      current = FileUtil.getParentFile(current);
    }

    return result;
  }

  public void saveToFile(File file) throws FileNotFoundException {
    PrintWriter out = new PrintWriter(file);
    try {
      for (File outputRoot : myOutputRoots) {
        out.println(FileUtil.getRelativePath(myProjectBaseDir, outputRoot));
      }
    }
    finally {
      out.close();
    }
  }

  public Collection<File> getOutputRoots() {
    return new ArrayList<File>(myOutputRoots);
  }
}
