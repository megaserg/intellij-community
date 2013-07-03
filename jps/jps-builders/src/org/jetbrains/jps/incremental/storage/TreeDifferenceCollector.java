package org.jetbrains.jps.incremental.storage;

import com.intellij.util.containers.HashSet;

import java.util.Collection;

/**
 * @author Sergey Serebryakov
 */
public class TreeDifferenceCollector {
  private Collection<String> createdFiles;
  private Collection<String> deletedFiles;
  private Collection<String> changedFiles;

  public TreeDifferenceCollector() {
    createdFiles = new HashSet<String>();
    deletedFiles = new HashSet<String>();
    changedFiles = new HashSet<String>();
  }

  public void addCreatedFile(String path) {
    createdFiles.add(path);
  }

  public void addCreatedFiles(Collection<String> paths) {
    createdFiles.addAll(paths);
  }

  public void addDeletedFile(String path) {
    deletedFiles.add(path);
  }

  public void addDeletedFiles(Collection<String> paths) {
    deletedFiles.addAll(paths);
  }

  public void addChangedFile(String path) {
    changedFiles.add(path);
  }

  @Override
  public String toString() {
    return "Created: " + createdFiles + "\n" + "Deleted: " + deletedFiles + "\n" + "Changed: " + changedFiles + "\n";
  }
}
