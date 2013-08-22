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
import com.jcraft.jsch.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public class UploadCacheAction extends AbstractCacheAction {
  private static final Logger LOG = Logger.getInstance(UploadCacheAction.class);
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-zip-upload";
  private static final int SFTP_CONNECTION_PORT = 22;
  private static final int STEPS = 6;
  private static final double STEP_FRACTION = 1.0 / STEPS;

  private static boolean compress(String localZipPath, File directoryToCompress, String displayName) {
    long startCompress = System.currentTimeMillis();
    try {
      DirectoryCompressor.compressDirectory(localZipPath, directoryToCompress);
    }
    catch (IOException e) {
      LOG.info("Error while compressing " + directoryToCompress, e);
      return false;
    }
    long finishCompress = System.currentTimeMillis();
    logTimeConsumed("Compressing " + displayName + ": ", startCompress, finishCompress);
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
      LOG.info("Error while initializing directories");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Compressing cache");
    if (!compress(myLocalCacheZipPath, myCacheDirectory, CACHE_ZIP_NAME)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Compressing output");
    if (!compress(myLocalOutputZipPath, myOutputDirectory, OUTPUT_ZIP_NAME)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, CACHE_HASHTREE_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for output");
    if (!actualize(myOutputDirectory, OUTPUT_HASHTREE_PREFIX)) {
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

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

      sftpChannel.put(myLocalCacheZipPath, CACHE_ZIP_NAME);
      sftpChannel.put(myLocalCacheHashesPath, CACHE_HASHES_FILE_NAME);
      sftpChannel.put(myLocalCacheTreePath, CACHE_TREE_FILE_NAME);

      sftpChannel.put(myLocalOutputZipPath, OUTPUT_ZIP_NAME);
      sftpChannel.put(myLocalOutputHashesPath, OUTPUT_HASHES_FILE_NAME);
      sftpChannel.put(myLocalOutputTreePath, OUTPUT_TREE_FILE_NAME);

      sftpChannel.exit();
      session.disconnect();

      long finishUpload = System.currentTimeMillis();
      logTimeConsumed("Uploading files via SFTP: ", startUpload, finishUpload);
    }
    catch (JSchException e) {
      LOG.info(e);
      return;
    }
    catch (SftpException e) {
      LOG.info(e);
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Operation completed. Total time: ", startWhole, finishWhole);

    indicator.setFraction(1.0);

    // TODO(serebryakov): Cleanup temp directory (in finally block?)
  }
}
