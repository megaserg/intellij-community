package com.intellij.compiler.treediff;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Sergey Serebryakov
 */
public abstract class ProjectHashedFileTree {
  private static String HASHES_FILE_EXTENSION = "hashes";
  private static String TREE_FILE_EXTENSION = "tree";

  public static String getHashesFileName(String prefix) {
    return prefix + "." + HASHES_FILE_EXTENSION;
  }

  public static String getTreeFileName(String prefix) {
    return prefix + "." + TREE_FILE_EXTENSION;
  }

  /**
   * Whether the given node is a (possibly empty) directory.
   */
  abstract boolean hasDirectory(String path);

  /**
   * Whether the given node is a file.
   */
  abstract boolean hasFile(String path);

  abstract void addDirectoryWithoutHash(String path, String parentPath);

  abstract void addDirectory(String path, String parentPath, String hash);

  abstract void addFile(String path, String parentPath, String hash);

  /**
   * Removes the whole subtree of the given node, including the given node itself.
   * Applicable both to directory and file node.
   */
  abstract void removeSubtree(String path);

  abstract String getPathByName(String parentPath, String name);

  /**
   * Returns names of the immediate children of the given node.
   * The given node is assumed to be a directory.
   */
  abstract Collection<String> getSortedCopyOfChildrenNames(String dirPath);

  /**
   * Returns paths of all nodes in the subtree of the given node, including the given node itself.
   * Applicable both to directory and file node, in the latter case the result contains only one record.
   */
  abstract Collection<String> listSubtree(String path);

  abstract String getHash(String path);

  abstract void updateHash(String path, String hash);

  /**
   * @return The total number of nodes in the tree.
   */
  abstract int nodesCount();

  /**
   * @return The total number of directories in the tree.
   */
  abstract int directoriesCount();

  abstract void save() throws IOException;

  abstract void load() throws IOException;
}
