package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * @author Sergey Serebryakov
 */

// TODO: guarantee system-independent strings?

public class ProjectHashedFileTreeImpl implements ProjectHashedFileTree {
  private static final String ROOT_DIRECTORY = ".";
  private static final String ROOT_PARENT_DIRECTORY = "..";
  private static final String INITIAL_DIRECTORY_HASH = "initial_directory_hash";
  private int nodesCount = 0;
  private int directoriesCount = 0;

  //private Map<String, String> nodes = new HashMap<String, String>(); // maps (path) to (parent path)
  //private Map<String, String> hashes = new HashMap<String, String>(); // maps (path) to (hash)
  //private Map<String, SortedSet<String>> tree = new HashMap<String, SortedSet<String>>(); // maps (directory path) to (sorted set of children paths)
  private PathToPathMapping nodes;
  private PathToStringMapping hashes;
  private PathToChildrenMapping tree;

  public ProjectHashedFileTreeImpl(@NotNull File dataStorageDirectory) throws IOException {
    nodes = new PathToPathMapping(new File(dataStorageDirectory, "nodes"));
    hashes = new PathToStringMapping(new File(dataStorageDirectory, "hashes"));
    tree = new PathToChildrenMapping(new File(dataStorageDirectory, "tree"));

    nodes.put(ROOT_DIRECTORY, ROOT_PARENT_DIRECTORY);
    hashes.put(ROOT_DIRECTORY, INITIAL_DIRECTORY_HASH);
    tree.put(ROOT_DIRECTORY, new TreeSet<String>());
    nodesCount = 1;
    directoriesCount = 1;
  }

  @NotNull
  private Collection<String> getChildrenSet(String dirPath) {
    if (Debug.DEBUG && !hasDirectory(dirPath)) {
      throw new RuntimeException("Cannot get children of a non-existent directory " + dirPath);
    }
    return tree.get(dirPath);
  }

  @NotNull
  private String getParent(String path) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot get parent of a non-existent path " + path);
    }
    return nodes.get(path);
  }

  private void addChild(String parent, String child) {
    if (Debug.DEBUG && !hasDirectory(parent)) {
      throw new RuntimeException("Cannot add child to a non-existent directory " + parent);
    }
    tree.appendChild(parent, child);
    //tree.get(parent).add(child);
  }

  private void removeChild(String parent, String child) {
    if (Debug.DEBUG && !hasDirectory(parent)) {
      throw new RuntimeException("Cannot remove child from a non-existent directory " + parent);
    }
    tree.removeChild(parent, child);
    //tree.get(parent).remove(child);
  }

  private boolean hasNode(String path) {
    return nodes.containsKey(path);
  }

  @Override
  public boolean hasDirectory(String path) {
    return hasNode(path) && tree.containsKey(path);
  }

  @Override
  public boolean hasFile(String path) {
    return hasNode(path) && !tree.containsKey(path);
  }

  private void addNode(String path, String parentPath, String hash) {
    if (Debug.DEBUG && hasNode(path)) {
      throw new RuntimeException("The path " + path + " is already present in the tree");
    }
    nodes.put(path, parentPath);
    addChild(parentPath, path);
    //System.err.println("Created hash for path " + path + " (" + hash + ")");
    hashes.put(path, hash);
    nodesCount++;
  }

  private void removeNode(String path) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot remove non-existent path " + path);
    }
    String parent = getParent(path);
    nodes.remove(path);
    removeChild(parent, path);
    hashes.remove(path);
    nodesCount--;
  }

  @Override
  public void addDirectory(String path, String parentPath, String hash) {
    addNode(path, parentPath, hash);
    tree.put(path, new TreeSet<String>());
    directoriesCount++;
  }

  @Override
  public void addDirectoryWithoutHash(String path, String parentPath) {
    addDirectory(path, parentPath, INITIAL_DIRECTORY_HASH);
  }

  @Override
  public void addFile(String path, String parentPath, String hash) {
    addNode(path, parentPath, hash);
  }

  @Override
  public void removeSubtree(String path) {
    if (hasDirectory(path)) {
      Collection<String> children = getSortedCopyOfChildrenPaths(path); // can't iterate over the changing set
      for (String childPath : children) {
        removeSubtree(childPath);
      }
      tree.remove(path);
      directoriesCount--;
    }
    removeNode(path);
  }

  @NotNull
  @Override
  public Collection<String> getSortedCopyOfChildrenPaths(String dirPath) {
    return new TreeSet<String>(getChildrenSet(dirPath));
  }

  private void accumulateSubtree(String path, Collection<String> result) {
    result.add(path);
    if (hasDirectory(path)) {
      for (String childPath : getSortedCopyOfChildrenPaths(path)) {
        accumulateSubtree(childPath, result);
      }
    }
  }

  @NotNull
  @Override
  public Collection<String> listSubtree(String path) {
    Collection<String> result = new LinkedList<String>();
    accumulateSubtree(path, result);
    return result;
  }

  @NotNull
  @Override
  public String getHash(String path) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot get hash for non-existent path " + path);
    }
    String hash = hashes.get(path);
    if (Debug.DEBUG && hash.equals(INITIAL_DIRECTORY_HASH)) {
      throw new RuntimeException("Cannot get uninitialized hash for directory " + path);
    }
    return hash;
  }

  @Override
  public void updateHash(String path, String hash) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot update hash for non-existent path " + path);
    }
    //System.err.println("Updated hash for path " + path + " (" + hash + ")");
    hashes.put(path, hash);
  }

  @Override
  public int nodesCount() {
    return nodesCount;
  }

  @Override
  public int directoriesCount() {
    return directoriesCount;
  }
}
