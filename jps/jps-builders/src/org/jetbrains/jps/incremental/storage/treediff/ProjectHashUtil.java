package org.jetbrains.jps.incremental.storage.treediff;

import com.intellij.openapi.diagnostic.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

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
      LOG.debug(
        "Hashtree storage file is missing and will be created at saving (" + storageFilesPrefix + " in " + storageDirectoryFile + ")");
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



  /*public static boolean compareAndUpdate(File oldStorageDirectoryFile,
                                         String oldStorageFilesPrefix,
                                         File newStorageDirectoryFile,
                                         String newStorageFilesPrefix,
                                         String archiveFilePath,
                                         File actualDirectoryFile) {
    TreeDifferenceCollector collector = new TreeDifferenceCollector();

    long startCompare = System.currentTimeMillis();
    if (!compare(oldStorageDirectoryFile, oldStorageFilesPrefix, newStorageDirectoryFile, newStorageFilesPrefix, collector)) {
      return false;
    }
    long finishCompare = System.currentTimeMillis();
    logTimeConsumed("Comparing hashtrees: ", (finishCompare - startCompare));

    long startApply = System.currentTimeMillis();
    if (!apply(archiveFilePath, actualDirectoryFile, collector)) {
      return false;
    }
    long finishApply = System.currentTimeMillis();
    logTimeConsumed("Applying changes: ", (finishApply - startApply));

    return true;
  }*/

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
      LOG.debug("Hashtree storage file is missing and will be created at saving (" +
                oldStorageFilesPrefix +
                " in " +
                oldStorageDirectoryFile +
                ")");
    }
    catch (IOException e) {
      LOG.error("IOException while loading a hashtree", e);
      return false;
    }

    try {
      newTree.load();
    }
    catch (FileNotFoundException ignored) {
      LOG.debug("Hashtree storage file is missing and will be created at saving (" +
                newStorageFilesPrefix +
                " in " +
                newStorageDirectoryFile +
                ")");
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
}
