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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
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
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-zip-download";
  private static final String CURRENT_CACHE_HASHTREE_PREFIX = "existing-cache";
  private static final String CURRENT_OUTPUTROOTS_HASHTREES_DIRECTORY_NAME = "existing-output-roots";
  private static final int STEPS = 9;
  private static final double STEP_FRACTION = 1.0 / STEPS;
  private String myServerAddress;
  private String myFtpUsername;
  private String myFtpPassword;
  private String myFtpInitialPath;

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

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getProject();

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
      LOG.error("Error while initializing directories");
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
    cacheDownloadTasks.add(new DownloadTask(CACHE_ZIP_NAME, myLocalCacheZipPath));
    cacheDownloadTasks.add(new DownloadTask(CACHE_HASHES_FILE_NAME, myLocalCacheHashesPath));
    cacheDownloadTasks.add(new DownloadTask(CACHE_TREE_FILE_NAME, myLocalCacheTreePath));

    indicator.setText("Downloading cache");
    if (!runDownloadSession(ftp, cacheDownloadTasks)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Copying existing output roots hashtrees");
    File currentOutputRootsHashtreesDirectory = new File(myTempDirectory, CURRENT_OUTPUTROOTS_HASHTREES_DIRECTORY_NAME);
    try {
      if (myOutputRootsHashtreesDirectory.exists()) {
        FileUtil.copyDir(myOutputRootsHashtreesDirectory, currentOutputRootsHashtreesDirectory);
      }
      else {
        FileUtil.createDirectory(currentOutputRootsHashtreesDirectory);
      }
    }
    catch (IOException e) {
      LOG.error("IOException while copying output roots hashtrees", e);
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, CURRENT_CACHE_HASHTREE_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Applying changes to cache");
    if (!ProjectHashUtil
      .compareAndUpdate(myTempDirectory, CURRENT_CACHE_HASHTREE_PREFIX, myTempDirectory, CACHE_HASHTREE_PREFIX, myLocalCacheZipPath,
                        myCacheDirectory)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Reading output roots index");
    OutputRootIndex outputRootIndex = readOutputRootIndex();
    if (outputRootIndex == null) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    List<DownloadTask> outputRootsDownloadTasks = new ArrayList<DownloadTask>();
    List<DiffHolder> diffHolders = new ArrayList<DiffHolder>();

    indicator.setText("Computing differences for output roots");
    long startCompare = System.currentTimeMillis();
    for (File outputRoot : outputRootIndex.getOutputRoots()) {
      String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
      String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
      String outputRootZipName = prefix + ZIP_EXTENSION;
      String localOutputRootZipPath = new File(myTempDirectory, outputRootZipName).getAbsolutePath();

      TreeDifferenceCollector diff = new TreeDifferenceCollector();
      if (!ProjectHashUtil.compare(currentOutputRootsHashtreesDirectory, prefix, myOutputRootsHashtreesDirectory, prefix, diff)) {
        return;
      }
      if (!diff.isEmpty()) {
        outputRootsDownloadTasks.add(new DownloadTask(outputRootZipName, localOutputRootZipPath));
        diffHolders.add(new DiffHolder(localOutputRootZipPath, outputRoot, diff));
      }
    }
    long finishCompare = System.currentTimeMillis();
    logTimeConsumed("Computing differences for output roots: ", (finishCompare - startCompare));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    if (!outputRootsDownloadTasks.isEmpty()) {
      indicator.setText("Downloading output roots");

      if (!runDownloadSession(ftp, outputRootsDownloadTasks)) {
        return;
      }
      indicator.setFraction(indicator.getFraction() + STEP_FRACTION);
    }

    if (!diffHolders.isEmpty()) {
      indicator.setText("Applying changes to output roots");
      long startApply = System.currentTimeMillis();
      for (DiffHolder holder : diffHolders) {
        if (!ProjectHashUtil.apply(holder.getLocalOutputRootZipPath(), holder.getOutputRoot(), holder.getDiff())) {
          return;
        }
      }
      long finishApply = System.currentTimeMillis();
      logTimeConsumed("Applying changes: ", (finishApply - startApply));
      indicator.setFraction(indicator.getFraction() + STEP_FRACTION);
    }

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Operation completed. Total time: ", (finishWhole - startWhole));

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
    private String myLocalOutputRootZipPath;
    private File myOutputRoot;
    private TreeDifferenceCollector myDiff;

    private DiffHolder(String localOutputRootZipPath, File outputRoot, TreeDifferenceCollector diff) {
      myLocalOutputRootZipPath = localOutputRootZipPath;
      myOutputRoot = outputRoot;
      myDiff = diff;
    }

    private String getLocalOutputRootZipPath() {
      return myLocalOutputRootZipPath;
    }

    private File getOutputRoot() {
      return myOutputRoot;
    }

    private TreeDifferenceCollector getDiff() {
      return myDiff;
    }
  }
}
