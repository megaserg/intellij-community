package org.jetbrains.jps.incremental.storage.treediff;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.storage.treediff.mapstorage.PathToChildrenMapping;
import org.jetbrains.jps.incremental.storage.treediff.mapstorage.PathToHashMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * @author Sergey Serebryakov
 */

// TODO: ensure system-independent strings
// TODO: Ideas for optimization:
// - Use byte array representation of hash, instead of string (=> 75% less memory/disk space => less time spent reading/instantiating strings + no need for toHexString)
// - Profiler shows toLowerCase() takes 5% of the time (because of getRelativePath()) => carry relative path as a recursion param
// - Implement my own getNameByPath/getPathByName/getParent (although it might be OK to continue using new File())
// - Reuse the same MessageDigest instance (as a singleton) - NB: always reset

public class ProjectHashedFileTreeImpl extends ProjectHashedFileTree {
  private static final String ROOT_DIRECTORY = ".";
  private static final String INITIAL_DIRECTORY_HASH = "initial_directory_hash";
  private int nodesCount = 0;
  private int directoriesCount = 0;
  //private Map<String, String> hashes = new HashMap<String, String>(); // maps (path) to (hash)
  //private Map<String, SortedSet<String>> tree = new HashMap<String, SortedSet<String>>(); // maps (directory path) to (sorted set of children names)
  private PathToHashMapping hashes;
  private PathToChildrenMapping tree;

  public ProjectHashedFileTreeImpl(File dataStorageDirectory, String prefix) {
    hashes = new PathToHashMapping(new File(dataStorageDirectory, getHashesFileName(prefix)));
    tree = new PathToChildrenMapping(new File(dataStorageDirectory, getTreeFileName(prefix)));

    hashes.put(ROOT_DIRECTORY, INITIAL_DIRECTORY_HASH);
    tree.put(ROOT_DIRECTORY, new TreeSet<String>());
    nodesCount = 1;
    directoriesCount = 1;
  }

  private static String getNameByPath(String path) {
    return new File(path).getName();
  }

  public void load() throws IOException {
    hashes.load();
    tree.load();
    nodesCount = hashes.size();
    directoriesCount = tree.size();
  }

  public void save() throws IOException {
    hashes.save();
    tree.save();
  }

  private Collection<String> getChildrenSet(String dirPath) {
    if (Debug.DEBUG && !hasDirectory(dirPath)) {
      throw new RuntimeException("Cannot get children of a non-existent directory " + dirPath);
    }
    return tree.get(dirPath);
  }

  private String getParent(String path) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot get parent of a non-existent path " + path);
    }
    //return nodes.get(path);
    return FileUtil.toSystemIndependentName(new File(path).getParent());
  }

  private void addChild(String parent, String child) {
    if (Debug.DEBUG && !hasDirectory(parent)) {
      throw new RuntimeException("Cannot add child to a non-existent directory " + parent);
    }
    //tree.appendChild(parent, child);
    tree.get(parent).add(child);
  }

  private void removeChild(String parent, String child) {
    if (Debug.DEBUG && !hasDirectory(parent)) {
      throw new RuntimeException("Cannot remove child from a non-existent directory " + parent);
    }
    //tree.removeChild(parent, child);
    tree.get(parent).remove(child);
  }

  private boolean hasNode(String path) {
    return hashes.containsKey(path);
  }

  @Override
  public boolean hasDirectory(String path) {
    path = FileUtil.toSystemIndependentName(path);
    return /*hasNode(path) && */tree.containsKey(path);
  }

  @Override
  public boolean hasFile(String path) {
    path = FileUtil.toSystemIndependentName(path);
    return hasNode(path) && !tree.containsKey(path);
  }

  public String getPathByName(String parentPath, String name) {
    return FileUtil.toSystemIndependentName(new File(parentPath, name).getPath());
  }

  private void addNode(String path, String parentPath, String hash) {
    if (Debug.DEBUG && hasNode(path)) {
      throw new RuntimeException("The path " + path + " is already present in the tree");
    }
    String name = getNameByPath(path);
    addChild(parentPath, name);
    //System.err.println("Created hash for path " + path + " (" + hash + ")");
    hashes.put(path, hash);
    nodesCount++;
  }

  private void removeNode(String path) {
    if (Debug.DEBUG && !hasNode(path)) {
      throw new RuntimeException("Cannot remove non-existent path " + path);
    }
    String parent = getParent(path);
    String name = getNameByPath(path);
    removeChild(parent, name);
    hashes.remove(path);
    nodesCount--;
  }

  @Override
  public void addDirectory(String path, String parentPath, String hash) {
    path = FileUtil.toSystemIndependentName(path);
    parentPath = FileUtil.toSystemIndependentName(parentPath);
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
    path = FileUtil.toSystemIndependentName(path);
    parentPath = FileUtil.toSystemIndependentName(parentPath);
    addNode(path, parentPath, hash);
  }

  @Override
  public void removeSubtree(String path) {
    path = FileUtil.toSystemIndependentName(path);
    if (hasDirectory(path)) {
      Collection<String> childrenNames = getSortedCopyOfChildrenNames(path); // can't iterate over the changing set
      for (String childName : childrenNames) {
        String childPath = getPathByName(path, childName);
        removeSubtree(childPath);
      }
      tree.remove(path);
      directoriesCount--;
    }
    removeNode(path);
  }

  @Override
  public Collection<String> getSortedCopyOfChildrenNames(String dirPath) {
    dirPath = FileUtil.toSystemIndependentName(dirPath);
    return new TreeSet<String>(getChildrenSet(dirPath));
  }

  private void accumulateSubtree(String path, Collection<String> result) {
    if (hasDirectory(path)) {
      for (String childName : getSortedCopyOfChildrenNames(path)) {
        String childPath = getPathByName(path, childName);
        accumulateSubtree(childPath, result);
      }
    }
    else {
      result.add(path);
    }
  }

  @Override
  public Collection<String> listSubtree(String path) {
    path = FileUtil.toSystemIndependentName(path);
    Collection<String> result = new LinkedList<String>();
    accumulateSubtree(path, result);
    return result;
  }

  @Override
  public String getHash(String path) {
    path = FileUtil.toSystemIndependentName(path);
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
    path = FileUtil.toSystemIndependentName(path);
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
