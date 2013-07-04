package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.security.MessageDigest;

/**
 * @author Sergey Serebryakov
 */
public class TreeActualizer {
  private HashProvider myHashProvider;

  public TreeActualizer() {
    this(new MD5HashProvider());
  }

  public TreeActualizer(HashProvider provider) {
    myHashProvider = provider;
  }

  private String hashString(String s) {
    MessageDigest md = myHashProvider.getMessageDigest();
    try {
      md.update(s.getBytes("UTF-8"));
    }
    catch (UnsupportedEncodingException e) {
      throw new RuntimeException("No UTF-8 encoding? Really?");
    }
    return StringUtil.toHexString(md.digest());
  }

  private String hashFileContent(@NotNull File f) throws IOException {
    InputStream fis = new FileInputStream(f);

    try {
      int read;
      byte[] buffer = new byte[1024];
      MessageDigest md = myHashProvider.getMessageDigest();
      while ((read = fis.read(buffer)) > 0) {
        md.update(buffer, 0, read);
      }
      return StringUtil.toHexString(md.digest());
    }
    finally {
      fis.close();
    }
  }

  private String hashFile(@NotNull final File file) throws IOException {
    if (Debug.DEBUG && file.isDirectory()) {
      throw new RuntimeException("Cannot hash a directory " + file + " as a file");
    }

    String hashedName = hashString(file.getName());
    String hashedContent = hashFileContent(file);

    return hashedName + hashedContent;
  }

  /**
   * Recalculates the hash of the given directory using hashes from the hash tree.
   * Assumes the given path to be relative to the project root.
   */
  private String hashDirectory(@NotNull final ProjectHashedFileTree tree,
                               @NotNull final File dir,
                               @NotNull final String path) throws IOException {
    if (Debug.DEBUG && !dir.isDirectory()) {
      throw new RuntimeException("Cannot hash a file " + dir + " as a directory");
    }

    StringBuilder content = new StringBuilder();
    for (String childPath : tree.getSortedCopyOfChildrenPaths(path)) {
      String childHash = tree.getHash(childPath);
      content.append(childHash);
    }

    String hashedName = hashString(dir.getName());
    String hashedContent = hashString(content.toString());

    return hashedName + hashedContent;
  }

  /**
   * We want to actualize the trees with the actual version of the given path and its subtree.
   * The given path is assumed to be present in the current actual disk storage.
   * The given file tree and hash tree are to be updated.
   *
   * @return Whether the corresponding node was updated.
   */
  public boolean actualize(@NotNull final File projectRoot,
                           @NotNull final ProjectHashedFileTree tree,
                           @NotNull final String path,
                           @NotNull final String parentPath) throws IOException {
    File file = new File(projectRoot, path);

    if (file.isDirectory()) {
      boolean rehashingNeeded = false;

      if (tree.hasDirectory(path)) {
        for (String childPath : tree.getSortedCopyOfChildrenPaths(path)) {
          File child = new File(projectRoot, childPath);
          if (!child.exists()) {
            tree.removeSubtree(childPath);
            rehashingNeeded = true;
          }
        }
      }
      else {
        if (tree.hasFile(path)) {// get rid of a file/directory mismatch
          tree.removeSubtree(path);
        }
        tree.addDirectoryWithoutHash(path, parentPath);
        rehashingNeeded = true;
      }

      File[] children = file.listFiles(); // list actual children
      if (Debug.DEBUG && children == null) {
        throw new RuntimeException("Cannot list files for directory " + file);
      }
      for (File child : children) {
        String childPath = FileUtil.getRelativePath(projectRoot, child);
        if (Debug.DEBUG && childPath == null) {
          throw new RuntimeException("Cannot get relative path for child " + child);
        }
        rehashingNeeded |= actualize(projectRoot, tree, childPath, path);
      }

      if (rehashingNeeded) {
        // The directory was just added or some of its children were removed or actualized.
        // We assume here that all actual children are actualized, i.e. are present in both trees and have updated hashes.
        String actualHash = hashDirectory(tree, file, path);
        tree.updateHash(path, actualHash);
        return true;
      }

      return false; // no update needed for this directory
    }
    else {
      String actualHash = hashFile(file);
      if (tree.hasFile(path)) {
        String knownHash = tree.getHash(path);
        if (!knownHash.equals(actualHash)) {
          tree.updateHash(path, actualHash);
          return true;
        }
        return false; // no update needed for this file
      }
      else {
        if (tree.hasDirectory(path)) { // get rid of a file/directory mismatch
          tree.removeSubtree(path);
        }
        tree.addFile(path, parentPath, actualHash);
        return true;
      }
    }
  }
}
