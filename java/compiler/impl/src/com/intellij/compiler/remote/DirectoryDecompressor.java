package com.intellij.compiler.remote;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.jetbrains.jps.incremental.storage.treediff.TreeDifferenceCollector;

import java.io.*;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * @author Sergey Serebryakov
 */
public class DirectoryDecompressor {
  private static final Logger LOG = Logger.getInstance(DirectoryDecompressor.class);

  private static byte[] buffer = new byte[1024];

  private static void copyStreamToFile(InputStream in, File file) throws IOException {
    FileUtil.createIfDoesntExist(file);
    OutputStream out = new FileOutputStream(file.getAbsolutePath());
    BufferedOutputStream bout = new BufferedOutputStream(out);
    try {
      int length;
      while ((length = in.read(buffer)) != -1) {
        bout.write(buffer, 0, length);
      }
    }
    finally {
      bout.close();
    }
  }

  private static void copyFromZip(String zipFilePath, File classDirectory, Set<String> pathsToDecompress) throws IOException {
    InputStream in = new FileInputStream(zipFilePath);
    BufferedInputStream bin = new BufferedInputStream(in);
    ZipInputStream zin = new ZipInputStream(bin);
    try {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        String relativePath = entry.getName();
        if (pathsToDecompress.contains(relativePath)) {
          File target = new File(classDirectory, relativePath);
          copyStreamToFile(bin, target);
        }
      }
    }
    finally {
      zin.close();
    }
  }

  private static void copyFromTar(String tarFilePath, File targetDirectory, Set<String> pathsToDecompress) throws IOException {
    InputStream in = new FileInputStream(tarFilePath);
    BufferedInputStream bin = new BufferedInputStream(in);
    TarInputStream tin = new TarInputStream(bin);
    try {
      TarEntry entry;
      tin.getNextEntry();
      while ((entry = tin.getNextEntry()) != null) {
        String relativePath = entry.getName();
        if (pathsToDecompress.contains(relativePath)) {
          File target = new File(targetDirectory, relativePath);
          copyStreamToFile(tin, target);
        }
      }
    }
    finally {
      tin.close();
    }
  }

  public static boolean decompress(String archiveFilePath, File actualDirectoryFile, TreeDifferenceCollector diff) {
    File archiveFile = new File(archiveFilePath);
    if (!archiveFile.exists() && diff.hasCreatedOrChanged()) {
      LOG.error("Missing archive file: " + archiveFilePath);
      return false;
    }

    Set<String> pathsToDecompress = new HashSet<String>();

    int changesCount = 0;

    for (String deleted : diff.getDeletedFiles()) {
      File deletedFile = new File(actualDirectoryFile, deleted);
      if (!deletedFile.exists()) {
        LOG.warn("Comparator says " + deleted + " was deleted, but there was no file initially");
        continue;
      }
      if (!FileUtil.delete(deletedFile)) {
        LOG.error("Cannot delete file " + deleted);
      }
      changesCount++;
    }

    for (String created : diff.getCreatedFiles()) {
      File createdFile = new File(actualDirectoryFile, created);
      if (createdFile.exists()) {
        LOG.warn("Comparator says " + created + " was created, but there was a file already");
        continue;
      }

      FileUtil.createIfDoesntExist(createdFile);
      String relativePath = FileUtil.toSystemIndependentName(created).replaceFirst("\\./", "");
      pathsToDecompress.add(relativePath);

      changesCount++;
    }

    for (String changed : diff.getChangedFiles()) {
      File changedFile = new File(actualDirectoryFile, changed);
      if (!changedFile.exists()) {
        LOG.warn("Comparator says " + changed + " was changed, but there was no file initially");
        continue;
      }
      if (!FileUtil.delete(changedFile)) {
        LOG.error("Cannot delete file " + changed);
      }

      FileUtil.createIfDoesntExist(changedFile);
      String relativePath = FileUtil.toSystemIndependentName(changed).replaceFirst("\\./", "");
      pathsToDecompress.add(relativePath);

      changesCount++;
    }

    try {
      copyFromTar(archiveFilePath, actualDirectoryFile, pathsToDecompress);
    }
    catch (IOException e) {
      LOG.error("IOException while decompressing " + archiveFilePath, e);
      return false;
    }

    LOG.info("Applied " + changesCount + " changes");

    return true;
  }
}
