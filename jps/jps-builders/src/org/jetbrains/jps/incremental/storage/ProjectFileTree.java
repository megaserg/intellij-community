package org.jetbrains.jps.incremental.storage;

import java.util.Collection;

/**
 * @author Sergey Serebryakov
 */

/**
 * The interface describes a tree representing a directory tree.
 * Files and empty directories are leaf nodes. Non-empty directories are inner nodes.
 * The paths stored are relative to the project/target root directory.
 */
public interface ProjectFileTree {
  /**
   * Checks that the node is present.
   */
  boolean hasNode(String path);

  /**
   * Whether the given node is a (possibly empty) directory.
   */
  boolean isDirectory(String path);

  /**
   * Whether the given node is a file.
   */
  boolean isFile(String path);

  void addDirectory(String path, String parentPath);

  void addFile(String path, String parentPath);

  void removeSubtree(String path);

  /**
   * Returns paths of the immediate children of the given node.
   * The given node is assumed to be a directory, i.e. isDirectory(dirPath) == true.
   */
  Collection<String> getSortedCopyOfChildrenPaths(String dirPath);

  /**
   * Returns paths of all nodes in the subtree of the given node, including the given node itself.
   * Applicable both to directory and file node, in the latter case the result contains only one record.
   */
  Collection<String> listSubtree(String path);

  int size();
}
