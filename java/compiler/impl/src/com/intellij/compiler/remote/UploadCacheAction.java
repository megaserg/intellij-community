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
import com.jcraft.jsch.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.outputroots.OutputRootIndex;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Sergey Serebryakov
 */
public class UploadCacheAction extends AbstractCacheAction {
  private static final Logger LOG = Logger.getInstance(UploadCacheAction.class);
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-archive-upload";
  private static final int SFTP_CONNECTION_PORT = 22;
  private static final int STEPS = 6;
  private static final double STEP_FRACTION = 1.0 / STEPS;

  private static boolean compress(String localArchivePath, File directoryToCompress, String displayName) {
    try {
      DirectoryCompressor.compressDirectoryTar(localArchivePath, directoryToCompress);
    }
    catch (IOException e) {
      LOG.error("IOException while compressing " + directoryToCompress, e);
      return false;
    }
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getProject();

    new Task.Backgroundable(project, "Uploading cache and output", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        compressAndUpload(project, indicator);
      }
    }.queue();
  }

  private void compressAndUpload(@Nullable Project project, ProgressIndicator indicator) {
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

    indicator.setText("Compressing cache");
    long startCompressCache = System.currentTimeMillis();
    if (!compress(myLocalCacheArchivePath, myCacheDirectory, CACHE_ARCHIVE_FILE_NAME)) {
      return;
    }
    long finishCompressCache = System.currentTimeMillis();
    logTimeConsumed("Compressing cache: ", (finishCompressCache - startCompressCache));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, CACHE_FILE_NAME_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Reading output roots index");
    OutputRootIndex outputRootIndex = readOutputRootIndex();
    if (outputRootIndex == null) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Compressing output roots");
    long startCompressOutput = System.currentTimeMillis();
    List<String> filesToUpload = new LinkedList<String>();
    for (File outputRoot : outputRootIndex.getOutputRoots()) {
      String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
      String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
      String outputRootArchiveName = prefix + ARCHIVE_EXTENSION;
      String localOutputRootArchivePath = new File(myTempDirectory, outputRootArchiveName).getPath();
      if (!compress(localOutputRootArchivePath, outputRoot, outputRoot.getPath())) {
        return;
      }
      filesToUpload.add(localOutputRootArchivePath);
    }
    long finishCompressOutput = System.currentTimeMillis();
    logTimeConsumed("Compressing output roots: ", (finishCompressOutput - startCompressOutput));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    filesToUpload.add(myLocalCacheArchivePath);
    filesToUpload.add(myLocalCacheHashesPath);
    filesToUpload.add(myLocalCacheTreePath);

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    String serverAddress = settings.getServerAddress();
    String sftpUsername = settings.getSFTPUsername();
    String sftpPassword = settings.getSFTPPassword();
    String sftpInitialPath = settings.getSFTPPath();

    indicator.setText("Uploading files");
    JSch jsch = new JSch();
    try {
      long startUpload = System.currentTimeMillis();

      Session session = jsch.getSession(sftpUsername, serverAddress, SFTP_CONNECTION_PORT);
      session.setConfig("StrictHostKeyChecking", "no");
      session.setPassword(sftpPassword);
      session.connect();

      Channel channel = session.openChannel("sftp");
      channel.connect();
      ChannelSftp sftpChannel = (ChannelSftp)channel;
      sftpChannel.cd(sftpInitialPath);
      try {
        sftpChannel.cd(myRemoteDirectoryName);
      }
      catch (SftpException e) { // no such remote directory
        sftpChannel.mkdir(myRemoteDirectoryName);
        sftpChannel.cd(myRemoteDirectoryName);
      }

      for (String localPath : filesToUpload) {
        String fileName = new File(localPath).getName();
        sftpChannel.put(localPath, fileName);
      }

      sftpChannel.exit();
      session.disconnect();

      long finishUpload = System.currentTimeMillis();
      logTimeConsumed("Uploading files via SFTP: ", (finishUpload - startUpload));
    }
    catch (JSchException e) {
      LOG.error(e);
      return;
    }
    catch (SftpException e) {
      LOG.error(e);
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Operation completed. Total time: ", (finishWhole - startWhole));

    indicator.setFraction(1.0);

    // TODO(serebryakov): Cleanup temp directory (in finally block?)
  }
}
