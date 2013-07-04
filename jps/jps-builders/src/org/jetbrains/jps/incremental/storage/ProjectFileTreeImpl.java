package org.jetbrains.jps.incremental.storage;

import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author Sergey Serebryakov
 */
public class ProjectFileTreeImpl implements ProjectFileTree {
  private static final String ROOT_DIRECTORY = ".";

  private Map<String, String> nodes = new HashMap<String, String>(); // maps (path) to (parent path)
  private Map<String, SortedSet<String>> tree = new HashMap<String, SortedSet<String>>(); // maps (directory path) to (set of children paths)

  public ProjectFileTreeImpl() {
    nodes.put(ROOT_DIRECTORY, null);
    tree.put(ROOT_DIRECTORY, new TreeSet<String>());
  }

  private SortedSet<String> getChildrenSet(String key) {
    if (Debug.DEBUG && !isDirectory(key)) {
      throw new RuntimeException("Cannot get children of a file");
    }
    return tree.get(key);
  }

  private String getParent(String key) {
    return nodes.get(key);
  }

  @Override
  public boolean hasNode(String path) {
    return nodes.containsKey(path);
  }

  @Override
  public boolean isDirectory(String path) {
    return tree.containsKey(path);
  }

  @Override
  public boolean isFile(String path) {
    return !isDirectory(path);
  }

  @Override
  public void addDirectory(String path, String parentPath) {
    nodes.put(path, parentPath);
    getChildrenSet(parentPath).add(path);
    tree.put(path, new TreeSet<String>());
  }

  @Override
  public void addFile(String path, String parentPath) {
    nodes.put(path, parentPath);
    getChildrenSet(parentPath).add(path);
  }

  @Override
  public void removeSubtree(String path) {
    if (isDirectory(path)) {
      Collection<String> children = getSortedCopyOfChildrenPaths(path); // can't iterate over the changing set
      for (String childPath : children) {
        removeSubtree(childPath);
      }
    }
    getChildrenSet(getParent(path)).remove(path);
    nodes.remove(path);
  }

  @Override
  public Collection<String> getSortedCopyOfChildrenPaths(String dirPath) {
    return new LinkedList<String>(getChildrenSet(dirPath));
  }

  public void accumulateSubtree(String path, Collection<String> result) {
    result.add(path);
    if (isDirectory(path)) {
      for (String childPath : getChildrenSet(path)) {
        accumulateSubtree(childPath, result);
      }
    }
  }

  @Override
  public Collection<String> listSubtree(String path) {
    Collection<String> result = new LinkedList<String>();
    accumulateSubtree(path, result);
    return result;
  }

  @Override
  public int size() {
    return nodes.size();
  }
}
