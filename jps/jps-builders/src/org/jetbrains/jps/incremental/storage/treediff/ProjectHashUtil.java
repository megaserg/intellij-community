package org.jetbrains.jps.incremental.storage.treediff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Sergey Serebryakov
 */
public class ProjectHashUtil {
  private static final Logger LOG = Logger.getInstance(ProjectHashUtil.class);

  private static void logTimeConsumed(String comment, long ms) {
    LOG.info(comment + ms / 1000.0 + " sec");
  }

  private static void logTimeConsumed(String prefix, String suffix, long ms) {
    LOG.info(prefix + ms / 1000.0 + " sec (" + suffix + ")");
  }

  public static boolean actualize(File actualDirectoryFile, File storageDirectoryFile, String storageFilesPrefix) {
    if (!storageDirectoryFile.exists() && !storageDirectoryFile.mkdir()) {
      LOG.error("Cannot create storage directory");
      return false;
    }

    ProjectHashedFileTree tree = new ProjectHashedFileTreeImpl(storageDirectoryFile, storageFilesPrefix);

    try {
      tree.load();
    }
    catch (FileNotFoundException ignored) {
      LOG.debug("Hashtree storage file is missing and will be created at saving (" + storageFilesPrefix + " in " + storageDirectoryFile + ")");
    }
    catch (IOException e) {
      LOG.error("IOException while loading hashtree", e);
      return false;
    }

    TreeActualizer actualizer = new TreeActualizer();
    try {
      long startActualize = System.currentTimeMillis();
      actualizer.actualize(actualDirectoryFile, tree, ".", ".");
      long finishActualize = System.currentTimeMillis();
      logTimeConsumed("Actualizing hashtree: ", actualDirectoryFile.toString(), (finishActualize - startActualize));
    }
    catch (IOException e) {
      LOG.error("IOException while actualizing hashtree for " + actualDirectoryFile, e);
      return false;
    }

    try {
      long startSave = System.currentTimeMillis();
      tree.save();
      long finishSave = System.currentTimeMillis();
      logTimeConsumed("Saving hashtree: ", actualDirectoryFile.toString(), (finishSave - startSave));
    }
    catch (IOException e) {
      LOG.error("IOException while saving hashtree for " + actualDirectoryFile, e);
      return false;
    }

    return true;
  }

  private static void copyFromZip(String zipFilePath, File classDirectory, String relativePath) throws IOException {
    ZipFile zip = new ZipFile(zipFilePath);
    try {
      ZipEntry entry = zip.getEntry(relativePath);
      InputStream in = zip.getInputStream(entry);
      BufferedInputStream bin = new BufferedInputStream(in);
      try {
        File target = new File(classDirectory, relativePath);
        OutputStream out = new FileOutputStream(target.getAbsolutePath());
        BufferedOutputStream bout = new BufferedOutputStream(out);
        try {
          byte[] buffer = new byte[8192];
          int length;
          while ((length = bin.read(buffer)) != -1) {
            bout.write(buffer, 0, length);
          }
        }
        finally {
          bout.close();
        }
      }
      finally {
        bin.close();
      }
    }
    finally {
      zip.close();
    }
  }

  public static boolean compareAndUpdate(File oldStorageDirectoryFile,
                                         String oldStorageFilesPrefix,
                                         File newStorageDirectoryFile,
                                         String newStorageFilesPrefix,
                                         String zipFilePath,
                                         File actualDirectoryFile) {
    TreeDifferenceCollector collector = new TreeDifferenceCollector();

    long startCompare = System.currentTimeMillis();
    if (!compare(oldStorageDirectoryFile, oldStorageFilesPrefix, newStorageDirectoryFile, newStorageFilesPrefix, collector)) {
      return false;
    }
    long finishCompare = System.currentTimeMillis();
    logTimeConsumed("Comparing hashtrees: ", (finishCompare - startCompare));

    long startApply = System.currentTimeMillis();
    if (!apply(zipFilePath, actualDirectoryFile, collector)) {
      return false;
    }
    long finishApply = System.currentTimeMillis();
    logTimeConsumed("Applying changes: ", (finishApply - startApply));

    return true;
  }

  public static boolean compare(File oldStorageDirectoryFile,
                                 String oldStorageFilesPrefix,
                                 File newStorageDirectoryFile,
                                 String newStorageFilesPrefix,
                                 TreeDifferenceCollector diff) {
    if (!oldStorageDirectoryFile.exists() || !oldStorageDirectoryFile.isDirectory()) {
      LOG.error("Missing storage directory: " + oldStorageDirectoryFile);
      return false;
    }

    if (!newStorageDirectoryFile.exists() || !newStorageDirectoryFile.isDirectory()) {
      LOG.error("Missing storage directory: " + newStorageDirectoryFile);
      return false;
    }

    ProjectHashedFileTree oldTree = new ProjectHashedFileTreeImpl(oldStorageDirectoryFile, oldStorageFilesPrefix);
    ProjectHashedFileTree newTree = new ProjectHashedFileTreeImpl(newStorageDirectoryFile, newStorageFilesPrefix);

    try {
      oldTree.load();
    }
    catch (FileNotFoundException ignored) {
      LOG.debug("Hashtree storage file is missing and will be created at saving (" + oldStorageFilesPrefix + " in " + oldStorageDirectoryFile + ")");
    }
    catch (IOException e) {
      LOG.error("IOException while loading a hashtree", e);
      return false;
    }

    try {
      newTree.load();
    }
    catch (FileNotFoundException ignored) {
      LOG.debug("Hashtree storage file is missing and will be created at saving (" + newStorageFilesPrefix + " in " + newStorageDirectoryFile + ")");
    }
    catch (IOException e) {
      LOG.error("IOException while loading a hashtree", e);
      return false;
    }

    TreeComparator.compare(newTree, oldTree, diff, ".");

    LOG.info(diff.getSizes());

    if (Debug.DEBUG) {
      LOG.info(diff.toString());
    }

    return true;
  }

  public static boolean apply(String zipFilePath,
                               File actualDirectoryFile,
                               TreeDifferenceCollector diff) {
    File zipFile = new File(zipFilePath);
    if (!zipFile.exists() && diff.hasCreatedOrChanged()) {
      LOG.error("Missing zip file: " + zipFilePath);
      return false;
    }

    int changesCount = 0;

    for (String deleted : diff.getDeletedFiles()) {
      File deletedFile = new File(actualDirectoryFile, deleted);
      if (!deletedFile.exists()) {
        LOG.error("Comparator says " + deleted + " was deleted, but there was no file initially");
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
        LOG.error("Comparator says " + created + " was created, but there was a file already");
        continue;
      }

      FileUtil.createIfDoesntExist(createdFile);
      try {
        String relativePath = FileUtil.toSystemIndependentName(created).replaceFirst("\\./", "");
        copyFromZip(zipFilePath, actualDirectoryFile, relativePath);
      }
      catch (IOException e) {
        LOG.error("IOException while decompressing " + created, e);
      }
      changesCount++;
    }

    for (String changed : diff.getChangedFiles()) {
      File changedFile = new File(actualDirectoryFile, changed);
      if (!changedFile.exists()) {
        LOG.error("Comparator says " + changed + " was changed, but there was no file initially");
        continue;
      }
      if (!FileUtil.delete(changedFile)) {
        LOG.error("Cannot delete file " + changed);
      }

      FileUtil.createIfDoesntExist(changedFile);
      try {
        String relativePath = FileUtil.toSystemIndependentName(changed).replaceFirst("\\./", "");
        copyFromZip(zipFilePath, actualDirectoryFile, relativePath);
      }
      catch (IOException e) {
        LOG.error("IOException while decompressing " + changed, e);
      }
      changesCount++;
    }

    LOG.info("Applied " + changesCount + " changes");

    return true;
  }
}
