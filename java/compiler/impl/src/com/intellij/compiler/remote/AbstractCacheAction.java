package com.intellij.compiler.remote;

import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.treediff.ProjectHashUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.compiler.treediff.ProjectHashedFileTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public abstract class AbstractCacheAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AbstractCacheAction.class);

  protected static final String CACHE_ZIP_NAME = "cache.zip";
  protected static final String CACHE_HASHTREE_PREFIX = "cache";
  protected static final String CACHE_HASHES_FILE_NAME = ProjectHashedFileTree.getHashesFileName(CACHE_HASHTREE_PREFIX);
  protected static final String CACHE_TREE_FILE_NAME = ProjectHashedFileTree.getTreeFileName(CACHE_HASHTREE_PREFIX);

  protected static final String OUTPUT_ZIP_NAME = "output.zip";
  protected static final String OUTPUT_HASHTREE_PREFIX = "output";
  protected static final String OUTPUT_HASHES_FILE_NAME = ProjectHashedFileTree.getHashesFileName(OUTPUT_HASHTREE_PREFIX);
  protected static final String OUTPUT_TREE_FILE_NAME = ProjectHashedFileTree.getTreeFileName(OUTPUT_HASHTREE_PREFIX);

  protected String myRemoteDirectoryName;
  protected File myCacheDirectory;
  protected File myOutputDirectory;
  protected File myTempArchiveDirectory;

  protected String myLocalCacheZipPath;
  protected String myLocalCacheHashesPath;
  protected String myLocalCacheTreePath;

  protected String myLocalOutputZipPath;
  protected String myLocalOutputHashesPath;
  protected String myLocalOutputTreePath;

  protected static File convertURLPathToFile(@NotNull String urlPath) {
    if (urlPath.startsWith("file:")) {
      return new File(urlPath.substring(5));
    }
    else {
      return new File(urlPath);
    }
  }

  protected static void logTimeConsumed(String comment, long start, long finish) {
    LOG.info(comment + (finish - start) / 1000.0 + " sec");
  }

  protected boolean initDirectoryVariables(@Nullable Project project, String tempArchiveDirectoryPrefix) {
    if (project == null) {
      LOG.info("Error: project is null");
      return false;
    }

    myRemoteDirectoryName = project.getName();
    myCacheDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);

    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      LOG.info("Error: CompilerProjectExtension is null");
      return false;
    }

    myOutputDirectory = convertURLPathToFile(extension.getCompilerOutputUrl());

    myTempArchiveDirectory = null;
    try {
      myTempArchiveDirectory = FileUtil.createTempDirectory(tempArchiveDirectoryPrefix + "-" + myRemoteDirectoryName + "-", null);
    }
    catch (IOException e) {
      LOG.info("Error while creating temporary archive directory", e);
      return false;
    }

    myLocalCacheZipPath = new File(myTempArchiveDirectory, CACHE_ZIP_NAME).getAbsolutePath();
    myLocalCacheHashesPath = new File(myTempArchiveDirectory, CACHE_HASHES_FILE_NAME).getAbsolutePath();
    myLocalCacheTreePath = new File(myTempArchiveDirectory, CACHE_TREE_FILE_NAME).getAbsolutePath();

    myLocalOutputZipPath = new File(myTempArchiveDirectory, OUTPUT_ZIP_NAME).getAbsolutePath();
    myLocalOutputHashesPath = new File(myTempArchiveDirectory, OUTPUT_HASHES_FILE_NAME).getAbsolutePath();
    myLocalOutputTreePath = new File(myTempArchiveDirectory, OUTPUT_TREE_FILE_NAME).getAbsolutePath();

    return true;
  }

  protected boolean actualize(File actualDirectoryFile, String storageFilesPrefix) {
    if (ProjectHashUtil.actualize(actualDirectoryFile, myTempArchiveDirectory, storageFilesPrefix)) {
      LOG.info("Actualized successfully: " + actualDirectoryFile);
      return true;
    }
    else {
      LOG.info("Error while actualizing " + actualDirectoryFile);
      return false;
    }
  }
}
