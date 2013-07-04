package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
class PathToPathMapping extends PathToStringMapping {

  public PathToPathMapping(File storePath) throws IOException {
    super(storePath);
  }

  @Override
  public void update(@NotNull String keyPath, @NotNull String valuePath) throws IOException {
    super.update(FileUtil.toSystemIndependentName(keyPath), FileUtil.toSystemIndependentName(valuePath));
  }

}
