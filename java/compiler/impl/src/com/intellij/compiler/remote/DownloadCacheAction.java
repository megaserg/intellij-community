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

import java.io.*;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * @author Sergey Serebryakov
 */
public class DownloadCacheAction extends AbstractCacheAction {
  private static final Logger LOG = Logger.getInstance(DownloadCacheAction.class);
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-zip-download";
  private static final String EXISTING_CACHE_HASHTREE_PREFIX = "existing-cache";
  private static final String EXISTING_OUTPUT_HASHTREE_PREFIX = "existing-output";
  private static final String TEMPORARY_OUTPUTROOTS_HASHTREES_DIRECTORY_NAME = "existing-output-roots";
  private static final int STEPS = 7;
  private static final double STEP_FRACTION = 1.0 / STEPS;

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
      LOG.info("Error while downloading file", e);
    }
  }

  private static boolean readContentList(String contentListPath, List<String> paths) {
    Scanner in;
    try {
      in = new Scanner(new File(contentListPath));
      while (in.hasNextLine()) {
        String path = in.nextLine();
        if (!path.isEmpty()) {
          paths.add(path);
        }
      }
    }
    catch (FileNotFoundException e) {
      LOG.info(e);
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

  private boolean compareAndUpdate(String oldStorageFilesPrefix,
                                   String newStorageFilesPrefix,
                                   String zipFilePath,
                                   File actualDirectoryFile) {
    if (ProjectHashUtil
      .compareAndUpdate(myTempDirectory, oldStorageFilesPrefix, myTempDirectory, newStorageFilesPrefix, zipFilePath,
                        actualDirectoryFile)) {
      LOG.info("Updated successfully: " + actualDirectoryFile);
      return true;
    }
    else {
      LOG.info("Error while updating " + actualDirectoryFile);
      return false;
    }
  }

  private void downloadAndDecompress(@Nullable Project project, ProgressIndicator indicator) {
    indicator.setFraction(0.0);

    long startWhole = System.currentTimeMillis();

    indicator.setText("Initializing directories");
    if (initDirectoryVariables(project, TEMPORARY_ARCHIVE_DIRECTORY_PREFIX)) {
      LOG.info("Directories initialized successfully");
    }
    else {
      LOG.info("Error while initializing directories");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    String serverAddress = settings.getServerAddress();
    String ftpUsername = settings.getFTPUsername();
    String ftpPassword = settings.getFTPPassword();
    String ftpInitialPath = settings.getFTPPath();

    List<String> outputRootZipNames = new LinkedList<String>();

    indicator.setText("Downloading files");
    FTPClient ftp = new FTPClient();
    try {
      try {
        long startDownload = System.currentTimeMillis();

        ftp.connect(serverAddress);
        if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
          ftp.disconnect();
          throw new SocketException("Reply code is bad");
        }
        ftp.login(ftpUsername, ftpPassword);
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        //ftp.enterLocalPassiveMode();
        ftp.changeWorkingDirectory(ftpInitialPath);
        ftp.changeWorkingDirectory(myRemoteDirectoryName);

        downloadFile(ftp, CONTENT_LIST_FILENAME, myLocalContentListPath);
        downloadFile(ftp, CACHE_ZIP_NAME, myLocalCacheZipPath);
        downloadFile(ftp, CACHE_HASHES_FILE_NAME, myLocalCacheHashesPath);
        downloadFile(ftp, CACHE_TREE_FILE_NAME, myLocalCacheTreePath);

        if (!readContentList(myLocalContentListPath, outputRootZipNames)) {
          return;
        }

        for (String fileName : outputRootZipNames) {
          String localPath = new File(myTempDirectory, fileName).getAbsolutePath();
          downloadFile(ftp, fileName, localPath);
        }

        long finishDownload = System.currentTimeMillis();
        logTimeConsumed("Downloading files via FTP: ", startDownload, finishDownload);
      }
      catch (SocketException e) {
        LOG.info(e);
        return;
      }
      finally {
        ftp.logout();
        ftp.disconnect();
      }
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    // copy output roots hashtrees
    indicator.setText("Copying existing output roots hashtrees");
    File temporaryOutputRootsHashtreesDirectory = new File(myTempDirectory, TEMPORARY_OUTPUTROOTS_HASHTREES_DIRECTORY_NAME);
    try {
      FileUtil.copyDir(myOutputRootsHashtreesDirectory, temporaryOutputRootsHashtreesDirectory);
    }
    catch (IOException e) {
      LOG.info("IOException while copying output roots hashtrees", e);
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, EXISTING_CACHE_HASHTREE_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Applying changes to cache");
    if (!compareAndUpdate(EXISTING_CACHE_HASHTREE_PREFIX, CACHE_HASHTREE_PREFIX, myLocalCacheZipPath, myCacheDirectory)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Reading output roots index");
    OutputRootIndex outputRootIndex = readOutputRootIndex();
    if (outputRootIndex == null) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Applying changes to output");
    for (File outputRoot : outputRootIndex.getOutputRoots()) {
      String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
      String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
      String outputRootZipName = prefix + ZIP_EXTENSION;
      String localOutputRootZipPath = new File(myTempDirectory, outputRootZipName).getAbsolutePath();
      if (!ProjectHashUtil.compareAndUpdate(temporaryOutputRootsHashtreesDirectory, prefix,
                                            myOutputRootsHashtreesDirectory, prefix,
                                            localOutputRootZipPath, outputRoot)) {
        return;
      }
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    /*indicator.setText("Actualizing hashtree for output");
    if (!actualize(myOutputDirectory, EXISTING_OUTPUT_HASHTREE_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Applying changes to output");
    if (!compareAndUpdate(EXISTING_OUTPUT_HASHTREE_PREFIX, OUTPUT_HASHTREE_PREFIX, myLocalOutputZipPath, myOutputDirectory)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);*/

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Operation completed. Total time: ", startWhole, finishWhole);

    indicator.setFraction(1.0);

    // TODO(serebryakov): Cleanup temp directory (in finally block?)
  }
}
