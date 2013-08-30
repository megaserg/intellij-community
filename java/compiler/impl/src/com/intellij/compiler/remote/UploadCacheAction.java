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
import com.jcraft.jsch.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.outputroots.OutputRootIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sergey Serebryakov
 */
public class UploadCacheAction extends AbstractCacheAction {
  private static final Logger LOG = Logger.getInstance(UploadCacheAction.class);
  private static final String TEMPORARY_ARCHIVE_DIRECTORY_PREFIX = "temp-archive-upload";
  private static final String NOTIFICATION_TITLE = "Uploading remote cache/output";
  private static final int SFTP_CONNECTION_PORT = 22;
  private static final int STEPS = 6;
  private static final double STEP_FRACTION = 1.0 / STEPS;
  private String myServerAddress;
  private String mySftpUsername;
  private String mySftpPassword;
  private String mySftpInitialPath;

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected String getNotificationTitle() {
    return NOTIFICATION_TITLE;
  }

  private static boolean compress(String localArchivePath, File directoryToCompress) {
    try {
      DirectoryCompressor.compressDirectoryTar(localArchivePath, directoryToCompress);
    }
    catch (IOException e) {
      LOG.error("IOException while compressing " + directoryToCompress, e);
      return false;
    }
    return true;
  }

  private boolean runUploadSession(JSch jsch, Collection<UploadTask> uploadTasks) {
    try {
      long startUpload = System.currentTimeMillis();

      Session session = jsch.getSession(mySftpUsername, myServerAddress, SFTP_CONNECTION_PORT);
      session.setConfig("StrictHostKeyChecking", "no");
      session.setPassword(mySftpPassword);
      session.connect();

      Channel channel = session.openChannel("sftp");
      channel.connect();
      ChannelSftp sftpChannel = (ChannelSftp)channel;
      try {
        sftpChannel.cd(mySftpInitialPath);
        try {
          sftpChannel.cd(myRemoteDirectoryName);
        }
        catch (SftpException e) { // no such remote directory
          sftpChannel.mkdir(myRemoteDirectoryName);
          sftpChannel.cd(myRemoteDirectoryName);
        }

        for (UploadTask task : uploadTasks) {
          String fileName = task.getFileName();
          String localPath = task.getSourcePath();
          sftpChannel.put(localPath, fileName);
        }
      }
      catch (SftpException e) {
        LOG.error("SftpException while uploading files", e);
        return false;
      }
      finally {
        sftpChannel.disconnect();
        session.disconnect();
      }

      long finishUpload = System.currentTimeMillis();
      logTimeConsumed("Uploading files via SFTP: ", (finishUpload - startUpload));
    }
    catch (JSchException e) {
      LOG.error("JSchException while uploading files", e);
      return false;
    }
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = event.getProject();

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    while (settings.getServerAddress().isEmpty() || settings.getSFTPUsername().isEmpty() || settings.getSFTPPath().isEmpty()) {
      if (!ShowSettingsUtil.getInstance().editConfigurable(project, new RemoteCacheStorageConfigurable())) {
        return;
      }
    }

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
      logError("Error while initializing directories");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Compressing cache");
    long startCompressCache = System.currentTimeMillis();
    if (!compress(myLocalCacheArchivePath, myCacheDirectory)) {
      logError("Error while compressing cache");
      return;
    }
    long finishCompressCache = System.currentTimeMillis();
    logTimeConsumed("Compressing cache: ", (finishCompressCache - startCompressCache));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Actualizing hashtree for cache");
    if (!actualize(myCacheDirectory, CACHE_FILE_NAME_PREFIX)) {
      logError("Error while actualizing hashtree for cache");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Reading output roots index");
    OutputRootIndex outputRootIndex = readOutputRootIndex();
    if (outputRootIndex == null) {
      logError("Error while reading output roots index");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    indicator.setText("Compressing output roots");
    long startCompressOutput = System.currentTimeMillis();
    List<UploadTask> filesToUpload = new ArrayList<UploadTask>();
    for (File outputRoot : outputRootIndex.getOutputRoots()) {
      String relativeOutputRoot = FileUtil.getRelativePath(myProjectBaseDir, outputRoot);
      String prefix = OutputRootIndex.getFilenameByOutputRoot(relativeOutputRoot);
      String outputRootArchiveName = prefix + ARCHIVE_EXTENSION;
      String localOutputRootArchivePath = new File(myTempDirectory, outputRootArchiveName).getPath();
      if (!compress(localOutputRootArchivePath, outputRoot)) {
        logError("Error while compressing output roots");
        return;
      }
      filesToUpload.add(new UploadTask(outputRootArchiveName, localOutputRootArchivePath));
    }
    long finishCompressOutput = System.currentTimeMillis();
    logTimeConsumed("Compressing output roots: ", (finishCompressOutput - startCompressOutput));
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    filesToUpload.add(new UploadTask(CACHE_ARCHIVE_FILE_NAME, myLocalCacheArchivePath));
    filesToUpload.add(new UploadTask(CACHE_HASHES_FILE_NAME, myLocalCacheHashesPath));
    filesToUpload.add(new UploadTask(CACHE_TREE_FILE_NAME, myLocalCacheTreePath));

    final RemoteCacheStorageSettings settings = RemoteCacheStorageSettings.getInstance();
    myServerAddress = settings.getServerAddress();
    mySftpUsername = settings.getSFTPUsername();
    mySftpPassword = settings.getSFTPPassword();
    mySftpInitialPath = settings.getSFTPPath();

    indicator.setText("Uploading files");
    JSch jsch = new JSch();
    if (!runUploadSession(jsch, filesToUpload)) {
      logError("Error while uploading files");
      return;
    }
    indicator.setFraction(indicator.getFraction() + STEP_FRACTION);

    long finishWhole = System.currentTimeMillis();
    logTimeConsumed("Operation completed. Total time: ", (finishWhole - startWhole));
    logInfoToEventLog("Uploading completed successfully in " + (finishWhole - startWhole) / 1000.0 + " sec");

    indicator.setFraction(1.0);

    // TODO(serebryakov): Cleanup temp directory (in finally block?)
  }

  private static class UploadTask {
    private String myFileName;
    private String mySourcePath;

    private UploadTask(String fileName, String sourcePath) {
      myFileName = fileName;
      mySourcePath = sourcePath;
    }

    private String getFileName() {
      return myFileName;
    }

    private String getSourcePath() {
      return mySourcePath;
    }
  }
}
