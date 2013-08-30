package com.intellij.compiler.remote;

import com.intellij.compiler.server.BuildManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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
  protected static final String ARCHIVE_EXTENSION = ".tar";

  protected static final String CACHE_FILE_NAME_PREFIX = "cache";
  protected static final String CACHE_ARCHIVE_FILE_NAME = CACHE_FILE_NAME_PREFIX + ARCHIVE_EXTENSION;
  protected static final String CACHE_HASHES_FILE_NAME = ProjectHashedFileTree.getHashesFileName(CACHE_FILE_NAME_PREFIX);
  protected static final String CACHE_TREE_FILE_NAME = ProjectHashedFileTree.getTreeFileName(CACHE_FILE_NAME_PREFIX);

  protected static final String OUTPUTROOTS_HASHTREES_DIRECTORY_NAME = "output-roots";
  protected static final String OUTPUTROOTS_LIST_FILENAME = "roots.list";

  protected static final String UPLOAD_DOWNLOAD_CACHE_OUTPUT_GROUP_ID = "Upload/Download Cache and Output";

  protected Project myProject;
  protected String myRemoteDirectoryName;
  protected File myProjectBaseDir;
  protected File myCacheDirectory;
  protected File myTempDirectory;

  protected String myLocalCacheArchivePath;
  protected String myLocalCacheHashesPath;
  protected String myLocalCacheTreePath;

  protected File myOutputRootHashtreesDirectory;
  protected File myOutputRootsListFile;

  protected abstract Logger getLogger();

  protected abstract String getNotificationTitle();

  protected void logTimeConsumed(String comment, long ms) {
    getLogger().info(comment + ms / 1000.0 + " sec");
  }

  protected void logMessageToEventLog(String content, NotificationType type) {
    Notifications.Bus.notify(new Notification(UPLOAD_DOWNLOAD_CACHE_OUTPUT_GROUP_ID, getNotificationTitle(), content, type));
  }

  protected void logErrorToEventLog(String content) {
    logMessageToEventLog(content, NotificationType.ERROR);
  }

  protected void logInfoToEventLog(String content) {
    logMessageToEventLog(content, NotificationType.INFORMATION);
  }

  protected void logError(String message) {
    getLogger().error(message);
    logErrorToEventLog(message);
  }

  protected boolean initDirectoryVariables(@Nullable Project project, String tempArchiveDirectoryPrefix) {
    if (project == null) {
      getLogger().error("Error: project is null");
      return false;
    }

    myProject = project;
    myRemoteDirectoryName = project.getName();
    myProjectBaseDir = new File(project.getBasePath());
    myCacheDirectory = BuildManager.getInstance().getProjectSystemDirectory(project);

    CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      getLogger().error("Error: CompilerProjectExtension is null");
      return false;
    }

    myTempDirectory = null;
    try {
      myTempDirectory = FileUtil.createTempDirectory(tempArchiveDirectoryPrefix + "-" + myRemoteDirectoryName + "-", null);
    }
    catch (IOException e) {
      getLogger().error("IOException while creating temporary archive directory", e);
      return false;
    }

    myLocalCacheArchivePath = new File(myTempDirectory, CACHE_ARCHIVE_FILE_NAME).getAbsolutePath();
    myLocalCacheHashesPath = new File(myTempDirectory, CACHE_HASHES_FILE_NAME).getAbsolutePath();
    myLocalCacheTreePath = new File(myTempDirectory, CACHE_TREE_FILE_NAME).getAbsolutePath();

    myOutputRootHashtreesDirectory = new File(myCacheDirectory, OUTPUTROOTS_HASHTREES_DIRECTORY_NAME);
    myOutputRootsListFile = new File(myOutputRootHashtreesDirectory, OUTPUTROOTS_LIST_FILENAME);

    return true;
  }

  protected boolean actualize(File actualDirectoryFile, String storageFilesPrefix) {
    if (ProjectHashUtil.actualize(actualDirectoryFile, myTempDirectory, storageFilesPrefix)) {
      getLogger().info("Actualized successfully: " + actualDirectoryFile);
      return true;
    }
    else {
      getLogger().error("Error while actualizing " + actualDirectoryFile);
      return false;
    }
  }

  protected OutputRootIndex readOutputRootIndex() {
    OutputRootIndex outputRootIndex;
    try {
      outputRootIndex = new OutputRootIndex(myOutputRootsListFile, myProjectBaseDir);
    }
    catch (IOException e) {
      getLogger().error("IOException while reading output roots index", e);
      return null;
    }
    return outputRootIndex;
  }
}
