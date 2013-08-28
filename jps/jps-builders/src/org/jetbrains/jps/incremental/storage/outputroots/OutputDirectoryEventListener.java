package org.jetbrains.jps.incremental.storage.outputroots;

import com.intellij.openapi.util.Pair;
import org.jetbrains.jps.incremental.BuildListener;

import java.io.File;
import java.util.Collection;

/**
 * @author Sergey Serebryakov
 */
public class OutputDirectoryEventListener implements BuildListener {
  private final OutputRootToHashedFileTreeMapping myOutputRootToHashedFileTreeMap;

  public OutputDirectoryEventListener(OutputRootToHashedFileTreeMapping outputRootToHashedFileTreeMap) {
    myOutputRootToHashedFileTreeMap = outputRootToHashedFileTreeMap;
  }

  @Override
  public void filesGenerated(Collection<Pair<String, String>> paths) {
    for (Pair<String, String> pair : paths) {
      String root = pair.getFirst();
      String relativePath = pair.getSecond();
      File file = relativePath.equals(".") ? new File(root) : new File(root, relativePath);
      myOutputRootToHashedFileTreeMap.registerGeneratedFile(file);
    }
  }

  @Override
  public void filesDeleted(Collection<String> paths) {
    for (String path : paths) {
      File file = new File(path);
      myOutputRootToHashedFileTreeMap.registerDeletedFile(file);
    }
  }
}
