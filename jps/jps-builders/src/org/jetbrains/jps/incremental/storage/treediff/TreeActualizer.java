package org.jetbrains.jps.incremental.storage.treediff;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.incremental.storage.treediff.digest.HashProvider;
import org.jetbrains.jps.incremental.storage.treediff.digest.SHA1HashProvider;

import java.io.*;
import java.security.MessageDigest;

/**
 * @author Sergey Serebryakov
 */
public class TreeActualizer {
  private HashProvider myHashProvider;
  private byte[] buffer = new byte[1024 * 50];

  public TreeActualizer() {
    this(new SHA1HashProvider());
    //this(new MD5HashProvider());
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

  private String hashFileContent(File f) throws IOException {
    InputStream fis = new FileInputStream(f);

    try {
      int read;
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

  private String hashFile(final File file) throws IOException {
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
  private String hashDirectory(final ProjectHashedFileTree tree, final File dir, final String path) throws IOException {
    if (Debug.DEBUG && !dir.isDirectory()) {
      throw new RuntimeException("Cannot hash a file " + dir + " as a directory");
    }

    StringBuilder content = new StringBuilder();
    for (String childName : tree.getSortedCopyOfChildrenNames(path)) {
      String childPath = tree.getPathByName(path, childName);
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
   * The given hashed filetree is to be updated.
   *
   * @return Whether the corresponding node was updated.
   */
  public boolean actualize(final File projectRoot, final ProjectHashedFileTree tree, final String path, final String parentPath)
    throws IOException {
    File file = new File(projectRoot, path);

    if (!file.exists()) {
      return true;
    }

    if (file.isDirectory()) {
      boolean rehashingNeeded = false;

      if (tree.hasDirectory(path)) {
        for (String childName : tree.getSortedCopyOfChildrenNames(path)) {
          String childPath = tree.getPathByName(path, childName);
          File child = new File(projectRoot, childPath);
          if (!child.exists()) {
            tree.removeSubtree(childPath);
            rehashingNeeded = true;
          }
        }
      }
      else {
        if (tree.hasFile(path)) { // get rid of a file/directory mismatch
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
        String childPath = FileUtil.getRelativePath(projectRoot.getAbsolutePath(), child.getAbsolutePath(), File.separatorChar);
        if (Debug.DEBUG && childPath == null) {
          throw new RuntimeException("Cannot get relative path for child " + child);
        }
        rehashingNeeded |= actualize(projectRoot, tree, childPath, path);
      }

      if (rehashingNeeded) {
        // The directory was just added or some of its children were removed or actualized.
        // We assume here that the subtree is actualized, i.e. all actual children are present in the tree and have updated hashes.
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

  private void actualizeSinglePath(File projectRoot, ProjectHashedFileTree tree, String pathToUpdate, String currentPath, String parentPath)
    throws IOException {
    File file = new File(projectRoot, currentPath);

    if (pathToUpdate.isEmpty()) {
      // now currentPath is the path to the initial file
      actualize(projectRoot, tree, currentPath, parentPath);
      return;
    }

    // We chop off the first directory name (prefix up to the first slash) from pathToUpdate and append it to currentPath to get nextPath.
    int slashPosition = pathToUpdate.indexOf("/");
    String nextName = null;
    if (slashPosition == -1) {
      nextName = pathToUpdate;
      pathToUpdate = "";
    }
    else {
      nextName = pathToUpdate.substring(0, slashPosition);
      pathToUpdate = pathToUpdate.substring(slashPosition+1);
    }
    String nextPath = FileUtil.toSystemIndependentName(new File(currentPath, nextName).getPath());

    if (!file.exists()) {
      return;
    }

    if (file.isDirectory()) {
      if (!tree.hasDirectory(currentPath)) {
        if (tree.hasFile(currentPath)) {
          tree.removeSubtree(currentPath);
        }
        tree.addDirectoryWithoutHash(currentPath, parentPath);
      }

      actualizeSinglePath(projectRoot, tree, pathToUpdate, nextPath, currentPath);
      String actualHash = hashDirectory(tree, file, currentPath);
      tree.updateHash(currentPath, actualHash);
    }
    else {
      // If currentPath points to an existing file which isn't directory, the pathToUpdate is empty and we have caught this before.
      // Therefore, this branch is unreachable.
      throw new RuntimeException("Reached unreachable branch");
    }
  }

  public void actualizeWhenSingleFileGenerated(File projectRoot, ProjectHashedFileTree tree, String pathToUpdate) throws IOException {
    pathToUpdate = FileUtil.toCanonicalPath(FileUtil.toSystemIndependentName(pathToUpdate));
    actualizeSinglePath(projectRoot, tree, pathToUpdate, ".", ".");
  }

  public void actualizeWhenSingleFileDeleted(File projectRoot, ProjectHashedFileTree tree, String pathToDelete) throws IOException {
    pathToDelete = FileUtil.toCanonicalPath(FileUtil.toSystemIndependentName(pathToDelete));

    // To register that a file has been deleted, we should just update the record for its parent directory.
    File parentFile = FileUtil.getParentFile(new File(pathToDelete));
    String pathToUpdate = ""; // if parentFile is null, pathToUpdate must be empty to trigger actualization of the root directory.
    if (parentFile != null) {
      pathToUpdate = FileUtil.toSystemIndependentName(parentFile.getPath());
    }
    actualizeSinglePath(projectRoot, tree, pathToUpdate, ".", ".");
  }
}
