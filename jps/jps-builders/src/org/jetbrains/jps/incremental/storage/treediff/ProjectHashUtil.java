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
      LOG.info("Cannot create storage directory");
      return false;
    }

    ProjectHashedFileTree tree = new ProjectHashedFileTreeImpl(storageDirectoryFile, storageFilesPrefix);

    try {
      tree.load();
    }
    catch (IOException e) {
      // should be FileNotFoundException if no hashtree yet
      // or some other error otherwise
    }

    TreeActualizer a = new TreeActualizer();
    try {
      long startActualize = System.currentTimeMillis();
      a.actualize(actualDirectoryFile, tree, ".", ".");
      long finishActualize = System.currentTimeMillis();
      logTimeConsumed("Actualizing hashtree: ", actualDirectoryFile.toString(), (finishActualize - startActualize));
    }
    catch (IOException e) {
      LOG.info("IOException while actualizing hashtree for " + actualDirectoryFile, e);
      return false;
    }

    try {
      long startSave = System.currentTimeMillis();
      tree.save();
      long finishSave = System.currentTimeMillis();
      logTimeConsumed("Saving hashtree: ", actualDirectoryFile.toString(), (finishSave - startSave));
    }
    catch (IOException e) {
      LOG.info("IOException while saving hashtree for " + actualDirectoryFile, e);
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
    if (!compare(oldStorageDirectoryFile, oldStorageFilesPrefix, newStorageDirectoryFile, newStorageFilesPrefix, collector)) {
      return false;
    }
    if (!apply(zipFilePath, actualDirectoryFile, collector)) {
      return false;
    }
    return true;
  }

  private static boolean compare(File oldStorageDirectoryFile,
                                 String oldStorageFilesPrefix,
                                 File newStorageDirectoryFile,
                                 String newStorageFilesPrefix,
                                 TreeDifferenceCollector diff) {
    if (!oldStorageDirectoryFile.exists() || !oldStorageDirectoryFile.isDirectory()) {
      LOG.info("Missing storage directory: " + oldStorageDirectoryFile);
      return false;
    }

    if (!newStorageDirectoryFile.exists() || !newStorageDirectoryFile.isDirectory()) {
      LOG.info("Missing storage directory: " + newStorageDirectoryFile);
      return false;
    }

    ProjectHashedFileTree oldTree = new ProjectHashedFileTreeImpl(oldStorageDirectoryFile, oldStorageFilesPrefix);
    ProjectHashedFileTree newTree = new ProjectHashedFileTreeImpl(newStorageDirectoryFile, newStorageFilesPrefix);

    long startLoad = System.currentTimeMillis();

    try {
      oldTree.load();
    }
    catch (FileNotFoundException ignored) {
      // no hashtree file exists, an empty hashtree is created
    }
    catch (IOException e) {
      LOG.info("IOException while loading a hashtree", e);
      return false;
    }

    try {
      newTree.load();
    }
    catch (FileNotFoundException ignored) {
      // no hashtree file exists, an empty hashtree is created
    }
    catch (IOException e) {
      LOG.info("IOException while loading a hashtree", e);
      return false;
    }

    long finishLoad = System.currentTimeMillis();
    logTimeConsumed("Loading hashtrees: ", (finishLoad - startLoad));


    long startCompare = System.currentTimeMillis();
    TreeComparator.compare(newTree, oldTree, diff, ".");
    long finishCompare = System.currentTimeMillis();
    logTimeConsumed("Comparing hashtrees: ", (finishCompare - startCompare));

    LOG.info(diff.getSizes());

    if (Debug.DEBUG) {
      LOG.info(diff.toString());
    }

    return true;
  }

  private static boolean apply(String zipFilePath,
                               File actualDirectoryFile,
                               TreeDifferenceCollector diff) {
    File zipFile = new File(zipFilePath);
    if (!zipFile.exists()) {
      LOG.info("Missing zip file: " + zipFilePath);
      return false;
    }

    long startApply = System.currentTimeMillis();
    int changesCount = 0;

    for (String deleted : diff.getDeletedFiles()) {
      File deletedFile = new File(actualDirectoryFile, deleted);
      if (!deletedFile.exists()) {
        LOG.info("Comparator says " + deleted + " was deleted, but there was no file initially");
        continue;
      }
      if (!FileUtil.delete(deletedFile)) {
        LOG.info("Cannot delete file " + deleted);
      }
      changesCount++;
    }

    for (String created : diff.getCreatedFiles()) {
      File createdFile = new File(actualDirectoryFile, created);
      if (createdFile.exists()) {
        LOG.info("Comparator says " + created + " was created, but there was a file already");
        continue;
      }

      FileUtil.createIfDoesntExist(createdFile);
      try {
        String relativePath = FileUtil.toSystemIndependentName(created).replaceFirst("\\./", "");
        copyFromZip(zipFilePath, actualDirectoryFile, relativePath);
      }
      catch (IOException e) {
        LOG.info("IOException while decompressing " + created, e);
      }
      changesCount++;
    }

    for (String changed : diff.getChangedFiles()) {
      File changedFile = new File(actualDirectoryFile, changed);
      if (!changedFile.exists()) {
        LOG.info("Comparator says " + changed + " was changed, but there was no file initially");
        continue;
      }
      if (!FileUtil.delete(changedFile)) {
        LOG.info("Cannot delete file " + changed);
      }

      FileUtil.createIfDoesntExist(changedFile);
      try {
        String relativePath = FileUtil.toSystemIndependentName(changed).replaceFirst("\\./", "");
        copyFromZip(zipFilePath, actualDirectoryFile, relativePath);
      }
      catch (IOException e) {
        LOG.info("IOException while decompressing " + changed, e);
      }
      changesCount++;
    }

    long finishApply = System.currentTimeMillis();
    logTimeConsumed("Applying changes: ", (finishApply - startApply));
    LOG.info("Applied " + changesCount + " changes");

    return true;
  }
}
