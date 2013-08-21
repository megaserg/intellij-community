package com.intellij.compiler.treediff;

import com.intellij.openapi.util.text.StringUtil;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Sergey Serebryakov
 */
public class TreeComparator {
  private static <T> T safeNext(Iterator<T> i) {
    return i.hasNext() ? i.next() : null;
  }

  /**
   * Given two file trees and two corresponding hash trees, computes the difference between the directory structures and file contents.
   * myTree is considered to be "new", yourTree is considered to be "old" (i.e. base revision).
   * On every call, it is assumed that the given node is present in both file trees.
   */
  public static void compare(final ProjectHashedFileTree myTree,
                             final ProjectHashedFileTree yourTree,
                             final TreeDifferenceCollector collector,
                             final String path) {
    String myHash = myTree.getHash(path);
    String yourHash = yourTree.getHash(path);

    if (!myHash.equals(yourHash)) {
      if (Debug.DEBUG) {
        System.err.println("Different hashes: " + path);
      }
      if (myTree.hasFile(path) && yourTree.hasFile(path)) {
        collector.addChangedFile(path);
      }
      else if (myTree.hasDirectory(path) && yourTree.hasDirectory(path)) {
        Collection<String> myChildrenNames = myTree.getSortedCopyOfChildrenNames(path);
        Collection<String> yourChildrenNames = yourTree.getSortedCopyOfChildrenNames(path);

        Iterator<String> myIterator = myChildrenNames.iterator();
        Iterator<String> yourIterator = yourChildrenNames.iterator();
        String myChildName = safeNext(myIterator);
        String yourChildName = safeNext(yourIterator);

        while (myChildName != null || yourChildName != null) {
          int compare = StringUtil.compare(myChildName, yourChildName, /*ignoreCase = */ false);
          // Arguments can be null, it's OK because in this case we don't use the result of the comparison.

          if (yourChildName == null || myChildName != null && compare < 0) {
            collector.addCreatedFiles(myTree.listSubtree(myTree.getPathByName(path, myChildName)));
            myChildName = safeNext(myIterator);
          }
          else if (myChildName == null || yourChildName != null && compare > 0) { // kept for symmetry sake
            collector.addDeletedFiles(yourTree.listSubtree(yourTree.getPathByName(path, yourChildName)));
            yourChildName = safeNext(yourIterator);
          }
          else if (compare == 0) {
            compare(myTree, yourTree, collector, myTree.getPathByName(path, myChildName));
            myChildName = safeNext(myIterator);
            yourChildName = safeNext(yourIterator);
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