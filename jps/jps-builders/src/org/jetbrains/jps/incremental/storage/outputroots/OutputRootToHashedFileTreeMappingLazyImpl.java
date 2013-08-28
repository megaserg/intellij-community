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
import gnu.trove.THashMap;
import org.jetbrains.jps.incremental.storage.treediff.ProjectHashedFileTree;
import org.jetbrains.jps.incremental.storage.treediff.ProjectHashedFileTreeImpl;
import org.jetbrains.jps.incremental.storage.treediff.TreeActualizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * @author Sergey Serebryakov
 */
public class OutputRootToHashedFileTreeMappingLazyImpl extends OutputRootToHashedFileTreeMapping {
  private static final String OUTPUTROOTS_STORAGE_DIR = "output-roots";
  private static final String OUTPUTROOTS_LIST_FILENAME = "roots.list";
  private final OutputRootIndex myOutputRootIndex;
  private final File myProjectBaseDir;
  private final File myStorageDir;
  private final Map<File, ProjectHashedFileTree> myMap;
  private final TreeActualizer myTreeActualizer;

  public OutputRootToHashedFileTreeMappingLazyImpl(OutputRootIndex outputRootIndex, File projectBaseDir, File dataStorageRoot) {
    myOutputRootIndex = outputRootIndex;
    myProjectBaseDir = projectBaseDir;
    myStorageDir = new File(dataStorageRoot, OUTPUTROOTS_STORAGE_DIR);
    myMap = new THashMap<File, ProjectHashedFileTree>(FileUtil.FILE_HASHING_STRATEGY);
    myTreeActualizer = new TreeActualizer();

    myStorageDir.mkdirs();
  }

  private void update(File file, ActualizationStrategy strategy) {
    file = new File(FileUtil.toCanonicalPath(file.getPath()));
    Collection<File> outputRoots = myOutputRootIndex.getOutputRootsByOutputFile(file);
    for (File outputRoot : outputRoots) {
      ProjectHashedFileTree tree = myMap.get(outputRoot);
      if (tree == null) {
        String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
        String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
        tree = new ProjectHashedFileTreeImpl(myStorageDir, prefix);
        try {
          tree.load();
        }
        catch (FileNotFoundException ignored) {
          System.err.println("Hashtree storage file is missing and will be created at saving");
        }
        catch (IOException e) {
          e.printStackTrace();
        }

        myMap.put(outputRoot, tree);
      }
      String relativePath = FileUtil.getRelativePath(outputRoot, file);

      try {
        strategy.actualize(outputRoot, tree, relativePath);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void registerGeneratedFile(File file) {
    update(file, new ActualizationStrategy() {
      @Override
      public void actualize(File projectRoot, ProjectHashedFileTree tree, String path) throws IOException {
        myTreeActualizer.actualizeWhenSingleFileGenerated(projectRoot, tree, path);
      }
    });
  }

  @Override
  public void registerDeletedFile(File file) {
    update(file, new ActualizationStrategy() {
      @Override
      public void actualize(File projectRoot, ProjectHashedFileTree tree, String path) throws IOException {
        myTreeActualizer.actualizeWhenSingleFileDeleted(projectRoot, tree, path);
      }
    });
  }

  @Override
  public void saveAll() {
    try {
      myOutputRootIndex.saveToFile(new File(myStorageDir, OUTPUTROOTS_LIST_FILENAME));
    }
    catch (IOException e) {
      System.err.println("IOException while saving list of output roots");
      e.printStackTrace();
    }

    for (ProjectHashedFileTree tree : myMap.values()) {
      try {
        tree.save();
      }
      catch (IOException e) {
        System.err.println("IOException while saving hashtree for output root");
        e.printStackTrace();
      }
    }
    myMap.clear();
  }

  private abstract class ActualizationStrategy {
    public abstract void actualize(File projectRoot, ProjectHashedFileTree tree, String path) throws IOException;
  }
}
