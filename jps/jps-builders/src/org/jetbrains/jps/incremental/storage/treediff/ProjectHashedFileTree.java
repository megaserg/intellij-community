package org.jetbrains.jps.incremental.storage.treediff;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Sergey Serebryakov
 */
public abstract class ProjectHashedFileTree {
  private static final String HASHES_FILE_EXTENSION = ".hashes";
  private static final String TREE_FILE_EXTENSION = ".tree";

  public static String getHashesFileName(String prefix) {
    return prefix + HASHES_FILE_EXTENSION;
  }

  public static String getTreeFileName(String prefix) {
    return prefix + TREE_FILE_EXTENSION;
  }

  /**
   * Whether the given node is a (possibly empty) directory.
   */
  public abstract boolean hasDirectory(String path);

  /**
   * Whether the given node is a file.
   */
  public abstract boolean hasFile(String path);

  public abstract void addDirectoryWithoutHash(String path, String parentPath);

  public abstract void addDirectory(String path, String parentPath, String hash);

  public abstract void addFile(String path, String parentPath, String hash);

  /**
   * Removes the whole subtree of the given node, including the given node itself.
   * Applicable both to directory and file node.
   */
  public abstract void removeSubtree(String path);

  public abstract String getPathByName(String parentPath, String name);

  /**
   * Returns names of the immediate children of the given node.
   * The given node is assumed to be a directory.
   */
  public abstract Collection<String> getSortedCopyOfChildrenNames(String dirPath);

  /**
   * Returns paths of all *file* nodes in the subtree of the given node, including the given node itself.
   * Applicable both to directory and file node, in the latter case the result contains only one record.
   */
  public abstract Collection<String> listSubtree(String path);

  public abstract String getHash(String path);

  public abstract void updateHash(String path, String hash);

  /**
   * @return The total number of nodes in the tree.
   */
  public abstract int nodesCount();

  /**
   * @return The total number of directories in the tree.
   */
  public abstract int directoriesCount();

  public abstract void save() throws IOException;

  public abstract void load() throws IOException;
}
