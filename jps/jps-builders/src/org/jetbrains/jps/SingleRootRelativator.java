package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtil;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;

/**
 * @author Sergey Serebryakov
 */
public class SingleRootRelativator implements Relativator {
  private File myProjectRootFile;

  public SingleRootRelativator(File projectRootFile) {
    myProjectRootFile = projectRootFile;
  }

  @Override
  public void enumerateProjectPath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRelativePath(String absolutePath) {
    return getRelativePath(new File(absolutePath));
  }

  @Override
  public String getRelativePath(File absolutePath) {
    return FileUtil.getRelativePath(myProjectRootFile, absolutePath);
  }

  @Override
  public String getAbsolutePath(String relativePath) {
    return FileUtil.join(myProjectRootFile.getAbsolutePath(), relativePath);
  }

  @Override
  public String getAbsolutePath(File relativePath) {
    return getAbsolutePath(relativePath.getPath()); //TODO(serebryakov): investigate whether getPath() is going to be relative
  }
}
