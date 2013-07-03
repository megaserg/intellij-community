package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;

public class TreeComparator {
  @Nullable
  private static <T> T safeNext(Iterator<T> i) {
    return i.hasNext() ? i.next() : null;
  }

  /**
   * Given two file trees and two corresponding hash trees, computes the difference between the directory structures and file contents.
   * On every call, it is assumed that the given node is present in both file trees.
   */
  public static void compare(final ProjectFileTree myTree,
                             final ProjectFileTree yourTree,
                             final ProjectHashTree myHashes,
                             final ProjectHashTree yourHashes,
                             final TreeDifferenceCollector collector,
                             final String path) {
    String myHash = myHashes.getHash(path);
    String yourHash = yourHashes.getHash(path);

    if (myHash == null || yourHash == null) {
      throw new RuntimeException("Cannot find hash for an added file " + path + ": myHash = " + myHash + ", yourHash = " + yourHash);
    }

    if (!myHash.equals(yourHash)) {
      System.err.println("Different hashes: " + path);
      if (!myTree.isDirectory(path) && !yourTree.isDirectory(path)) {
        collector.addChangedFile(path);
      }
      else if (myTree.isDirectory(path) && yourTree.isDirectory(path)) {
        Collection<String> myChildrenPaths = myTree.getSortedCopyOfChildrenPaths(path);
        Collection<String> yourChildrenPaths = yourTree.getSortedCopyOfChildrenPaths(path);

        Iterator<String> myIterator = myChildrenPaths.iterator();
        Iterator<String> yourIterator = yourChildrenPaths.iterator();
        String myChildPath = safeNext(myIterator);
        String yourChildPath = safeNext(yourIterator);

        while (myChildPath != null || yourChildPath != null) {
          int compare = StringUtil.compare(myChildPath, yourChildPath, /*ignoreCase = */ false);
          // Arguments can be null, it's OK as long as we don't use the result.

          if (yourChildPath == null || myChildPath != null && compare == -1) {
            collector.addCreatedFiles(myTree.listSubtree(myChildPath));
            myChildPath = safeNext(myIterator);
          }
          else if (myChildPath == null || yourChildPath != null && compare == 1) {
            collector.addDeletedFiles(yourTree.listSubtree(yourChildPath));
            yourChildPath = safeNext(yourIterator);
          }
          else if (compare == 0) {
            compare(myTree, yourTree, myHashes, yourHashes, collector, myChildPath);
            myChildPath = safeNext(myIterator);
            yourChildPath = safeNext(yourIterator);
          }
        }
      }
      else {
        // File/directory mismatch.
        collector.addCreatedFiles(myTree.listSubtree(path));
        collector.addDeletedFiles(yourTree.listSubtree(path));
      }
    }
  }
}