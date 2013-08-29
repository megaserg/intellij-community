package com.intellij.compiler.remote;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.outputroots.OutputRootIndex;
import org.jetbrains.jps.incremental.storage.treediff.ProjectHashUtil;
import org.jetbrains.jps.incremental.storage.treediff.ProjectHashedFileTree;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public abstract class AbstractCacheAction extends AnAction {
  protected static final String CACHE_ZIP_NAME = "cache.zip";
  protected static final String CACHE_HASHTREE_PREFIX = "cache";
  protected static final String CACHE_HASHES_FILE_NAME = ProjectHashedFileTree.getHashesFileName(CACHE_HASHTREE_PREFIX);
  protected static final String CACHE_TREE_FILE_NAME = ProjectHashedFileTree.getTreeFileName(CACHE_HASHTREE_PREFIX);

  protected static final String OUTPUTROOTS_HASHTREES_DIRECTORY_NAME = "output-roots";
  protected static final String OUTPUTROOTS_LIST_FILENAME = "roots.list";
  protected static final String ZIP_EXTENSION = ".zip";

  private static final Logger LOG = Logger.getInstance(AbstractCacheAction.class);

  protected String myRemoteDirectoryName;
  protected File myProjectBaseDir;
  protected File myCacheDirectory;
  protected File myTempDirectory;

  protected String myLocalCacheZipPath;
  protected String myLocalCacheHashesPath;
  protected String myLocalCacheTreePath;

  protected File myOutputRootsHashtreesDirectory;
  protected File myOutputRootsListFile;

  protected static void logTimeConsumed(String comment, long ms) {
    LOG.info(comment + ms / 1000.0 + " sec");
  }

  protected boolean initDirectoryVariables(@Nullable Project project, String tempArchiveDirectoryPrefix) {
    if (project == null) {
      LOG.error("Error: project is null");
      return false;
    }

    myRemoteDirectoryName = project.getName();
    myProjectBaseDir = new File(project.getBasePath());
    myCacheDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);

    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      LOG.error("Error: CompilerProjectExtension is null");
      return false;
    }

    myTempDirectory = null;
    try {
      myTempDirectory = FileUtil.createTempDirectory(tempArchiveDirectoryPrefix + "-" + myRemoteDirectoryName + "-", null);
    }
    catch (IOException e) {
      LOG.error("IOException while creating temporary archive directory", e);
      return false;
    }

    myLocalCacheZipPath = new File(myTempDirectory, CACHE_ZIP_NAME).getAbsolutePath();
    myLocalCacheHashesPath = new File(myTempDirectory, CACHE_HASHES_FILE_NAME).getAbsolutePath();
    myLocalCacheTreePath = new File(myTempDirectory, CACHE_TREE_FILE_NAME).getAbsolutePath();

    myOutputRootsHashtreesDirectory = new File(myCacheDirectory, OUTPUTROOTS_HASHTREES_DIRECTORY_NAME);
    myOutputRootsListFile = new File(myOutputRootsHashtreesDirectory, OUTPUTROOTS_LIST_FILENAME);

    return true;
  }

  protected boolean actualize(File actualDirectoryFile, String storageFilesPrefix) {
    if (ProjectHashUtil.actualize(actualDirectoryFile, myTempDirectory, storageFilesPrefix)) {
      LOG.info("Actualized successfully: " + actualDirectoryFile);
      return true;
    }
    else {
      LOG.error("Error while actualizing " + actualDirectoryFile);
      return false;
    }
  }

  protected OutputRootIndex readOutputRootIndex() {
    OutputRootIndex outputRootIndex;
    try {
      outputRootIndex = new OutputRootIndex(myOutputRootsListFile, myProjectBaseDir);
    }
    catch (IOException e) {
      LOG.error("IOException while reading output roots index", e);
      return null;
    }
    return outputRootIndex;
  }
}
