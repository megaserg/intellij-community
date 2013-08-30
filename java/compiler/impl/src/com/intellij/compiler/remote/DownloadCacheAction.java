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
package com.intellij.compiler.remote;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.outputroots.OutputRootIndex;
import org.jetbrains.jps.incremental.storage.treediff.ProjectHashUtil;
import org.jetbrains.jps.incremental.storage.treediff.TreeDifferenceCollector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sergey Serebryakov
 */
public class DownloadCacheAction extends AbstractCacheAction {
  private static final Logger LOG = Logger.getInstance(DownloadCacheAction.class);
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-archive-download";
  private static final String CURRENT_CACHE_HASHTREE_PREFIX = "existing-cache";
  private static final String CURRENT_OUTPUTROOT_HASHTREES_DIRECTORY_NAME = "existing-output-roots";
  private static final String NOTIFICATION_TITLE = "Downloading remote cache/output";
  private static final int STEPS = 10;
  private static final double STEP_FRACTION = 1.0 / STEPS;
  private String myServerAddress;
  private String myFtpUsername;
  private String myFtpPassword;
  private String myFtpInitialPath;

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected String getNotificationTitle() {
    return NOTIFICATION_TITLE;
  }

  private static void downloadFile(FTPClient ftp, String name, String localPath) {
    try {
      OutputStream out = new FileOutputStream(localPath);
      try {
        ftp.retrieveFile(name, out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException e) {
      LOG.error("IOException while downloading file", e);
    }
  }

  private boolean runDownloadSession(FTPClient ftp, Collection<DownloadTask> tasks) {
    try {
      try {
        long startDownload = System.currentTimeMillis();

        ftp.connect(myServerAddress);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
          ftp.disconnect();
          throw new SocketException("Reply code is bad");
        }
        ftp.login(myFtpUsername, myFtpPassword);
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        //ftp.enterLocalPassiveMode();
        ftp.changeWorkingDirectory(myFtpInitialPath);
        ftp.changeWorkingDirectory(myRemoteDirectoryName);

        for (DownloadTask task : tasks) {
          downloadFile(ftp, task.getFileName(), task.getDestinationPath());
        }

        long finishDownload = System.currentTimeMillis();
        logTimeConsumed("Downloading files via FTP: ", (finishDownload - startDownload));
      }
      catch (SocketException e) {
        LOG.error("SocketException while downloading files", e);
        return false;
      }
      finally {
        ftp.logout();
        ftp.disconnect();
      }
    }
    catch (IOException e) {
      LOG.error("IOException while downloading files", e);
      return false;
    }
    return true;
  }

  private boolean copyExistingOutputRootHashtrees(File currentOutputRootHashtreesDirectory) {
    try {
      if (myOutputRootHashtreesDirectory.exists()) {
        FileUtil.copyDir(myOutputRootHashtreesDirectory, currentOutputRootHashtreesDirectory);
      }
      else {
        FileUtil.createDirectory(currentOutputRootHashtreesDirectory);
      }
    }
    catch (IOException e) {
      LOG.error("IOException while copying existing output roots hashtrees", e);
      return false;
    }
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getProject();

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    while (settings.getServerAddress().isEmpty() || settings.getFTPUsername().isEmpty() || settings.getFTPPath().isEmpty()) {
      if (!ShowSettingsUtil.getInstance().editConfigurable(project, new RemoteCacheStorageConfigurable())) {
        return;
      }
    }

    new Task.Backgroundable(project, "Downloading cache and output", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        downloadAndDecompress(project, indicator);
      }
    }.queue();
  }

  private void downloadAndDecompress(@Nullable Project project, ProgressIndicator indicator) {

    indicator.setFraction(0.0);

    long startWhole = System.currentTimeMillis();

    indicator.setText("Initializing directories");
    if (initDirectoryVariables(project, TEMPORARY_ARCHIVE_DIRECTORY_PREFIX)) {
      LOG.info("Directories initialized successfully");
    }
    else {
      logError("Error while initializing directories");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    myServerAddress = settings.getServerAddress();
    myFtpUsername = settings.getFTPUsername();
    myFtpPassword = settings.getFTPPassword();
    myFtpInitialPath = settings.getFTPPath();

    FTPClient ftp = new FTPClient();

    List<DownloadTask> cacheDownloadTasks = new ArrayList<DownloadTask>();
    cacheDownloadTasks.add(new DownloadTask(CACHE_ARCHIVE_FILE_NAME, myLocalCacheArchivePath));
    cacheDownloadTasks.add(new DownloadTask(CACHE_HASHES_FILE_NAME, myLocalCacheHashesPath));
    cacheDownloadTasks.add(new DownloadTask(CACHE_TREE_FILE_NAME, myLocalCacheTreePath));

    indicator.setText("Downloading cache");
    if (!runDownloadSession(ftp, cacheDownloadTasks)) {
      logError("Error while downloading cache");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Copying existing output roots hashtrees");
    File currentOutputRootHashtreesDirectory = new File(myTempDirectory, CURRENT_OUTPUTROOT_HASHTREES_DIRECTORY_NAME);
    if (!copyExistingOutputRootHashtrees(currentOutputRootHashtreesDirectory)) {
      logError("Error while copying existing output roots hashtrees");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, CURRENT_CACHE_HASHTREE_PREFIX)) {
      logError("Error while actualizing hashtree for cache");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Computing differences for cache");
    long startCompareCache = System.currentTimeMillis();
    TreeDifferenceCollector cacheDiff = new TreeDifferenceCollector();
    if (!ProjectHashUtil.compare(myTempDirectory, CURRENT_CACHE_HASHTREE_PREFIX, myTempDirectory, CACHE_FILE_NAME_PREFIX, cacheDiff)) {
      logError("Error while computing differences for cache");
      return;
    }
    long finishCompareCache = System.currentTimeMillis();
    logTimeConsumed("Computing differences for cache: ", (finishCompareCache - startCompareCache));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    if (!cacheDiff.isEmpty()) {
      indicator.setText("Applying changes to cache");
      long startApplyCache = System.currentTimeMillis();
      if (!DirectoryDecompressor.decompress(myLocalCacheArchivePath, myCacheDirectory, cacheDiff)) {
        logError("Error while applying changes to cache");
        return;
      }
      long finishApplyCache = System.currentTimeMillis();
      logTimeConsumed("Applying changes to cache: ", (finishApplyCache - startApplyCache));
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Reading output roots index");
    OutputRootIndex outputRootIndex = readOutputRootIndex();
    if (outputRootIndex == null) {
      logError("Error while reading output roots index");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    List<DownloadTask> outputRootsDownloadTasks = new ArrayList<DownloadTask>();
    List<DiffHolder> diffHolders = new ArrayList<DiffHolder>();

    indicator.setText("Computing differences for output roots");
    long startCompareOutput = System.currentTimeMillis();
    for (File outputRoot : outputRootIndex.getOutputRoots()) {
      String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
      String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
      String outputRootArchiveName = prefix + ARCHIVE_EXTENSION;
      String localOutputRootArchivePath = new File(myTempDirectory, outputRootArchiveName).getAbsolutePath();

      TreeDifferenceCollector outputDiff = new TreeDifferenceCollector();
      if (!ProjectHashUtil.compare(currentOutputRootHashtreesDirectory, prefix, myOutputRootHashtreesDirectory, prefix, outputDiff)) {
        logError("Error while computing differences for output roots");
        return;
      }
      if (!outputDiff.isEmpty()) {
        outputRootsDownloadTasks.add(new DownloadTask(outputRootArchiveName, localOutputRootArchivePath));
        diffHolders.add(new DiffHolder(localOutputRootArchivePath, outputRoot, outputDiff));
      }
    }
    long finishCompareOutput = System.currentTimeMillis();
    logTimeConsumed("Computing differences for output roots: ", (finishCompareOutput - startCompareOutput));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    if (!outputRootsDownloadTasks.isEmpty()) {
      indicator.setText("Downloading output roots");
      if (!runDownloadSession(ftp, outputRootsDownloadTasks)) {
        logError("Error while downloading output roots");
        return;
      }
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    if (!diffHolders.isEmpty()) {
      indicator.setText("Applying changes to output roots");
      long startApplyOutput = System.currentTimeMillis();
      for (DiffHolder holder : diffHolders) {
        if (!DirectoryDecompressor.decompress(holder.getLocalOutputRootArchivePath(), holder.getOutputRoot(), holder.getDiff())) {
          logError("Error while applying changes to output roots");
          return;
        }
      }
      long finishApplyOutput = System.currentTimeMillis();
      logTimeConsumed("Applying changes to output roots: ", (finishApplyOutput - startApplyOutput));
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Downloading and updating completed. Total time: ", (finishWhole - startWhole));
    logInfoToEventLog("Downloading and updating completed successfully in " + (finishWhole - startWhole) / 1000.0 + " sec");

    indicator.setFraction(1.0);

    // TODO(serebryakov): Cleanup temp directory (in finally block?)
  }

  private static class DownloadTask {
    private String myFileName;
    private String myDestinationPath;

    private DownloadTask(String fileName, String destinationPath) {
      myFileName = fileName;
      myDestinationPath = destinationPath;
    }

    private String getFileName() {
      return myFileName;
    }

    private String getDestinationPath() {
      return myDestinationPath;
    }
  }

  private static class DiffHolder {
    private String myLocalOutputRootArchivePath;
    private File myOutputRoot;
    private TreeDifferenceCollector myDiff;

    private DiffHolder(String localOutputRootArchivePath, File outputRoot, TreeDifferenceCollector diff) {
      myLocalOutputRootArchivePath = localOutputRootArchivePath;
      myOutputRoot = outputRoot;
      myDiff = diff;
    }

    private String getLocalOutputRootArchivePath() {
      return myLocalOutputRootArchivePath;
    }

    private File getOutputRoot() {
      return myOutputRoot;
    }

    private TreeDifferenceCollector getDiff() {
      return myDiff;
    }
  }
}
