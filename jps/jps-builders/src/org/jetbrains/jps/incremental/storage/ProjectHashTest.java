package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Sergey Serebryakov
 */
public class ProjectHashTest {

  private static File ourProjectRoot = new File("C:\\Work\\hashtest\\test1\\");
  private static File ideaProjectRoot = new File("C:\\Work\\intellij-community-13\\");

  public static void testActualize() throws IOException {
    File createdFile = new File(ourProjectRoot, "y/y/x");
    if (createdFile.exists()) createdFile.delete();
    File deletedFile = new File(ourProjectRoot, "bbb/bb.in");
    if (!deletedFile.exists()) deletedFile.createNewFile();
    File changedFile = new File(ourProjectRoot, "t/txt.txt.");
    PrintWriter p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();

    TreeActualizer a = new TreeActualizer();

    File dataStorageDirectory = FileUtil.createTempDirectory("actualizer-test-", null);
    ProjectHashedFileTree tree = new ProjectHashedFileTreeImpl(dataStorageDirectory);
    System.err.println("Actualizing 1...");
    a.actualize(ourProjectRoot, tree, ".", ".");
    System.out.println(tree.listSubtree("."));

    createdFile.createNewFile();
    deletedFile.delete();
    PrintWriter q = new PrintWriter(changedFile);
    q.write("FILE CONTENT NUMBER TWO TWO TWO");
    q.close();

    System.err.println("Actualizing 2...");
    a.actualize(ourProjectRoot, tree, ".", ".");
    System.out.println(tree.listSubtree("."));
    System.out.println("Tree size: " + tree.nodesCount());

    if (createdFile.exists()) createdFile.delete();
    if (!deletedFile.exists()) deletedFile.createNewFile();
    p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();
  }

  public static void testCompare() throws IOException {
    File createdFile = new File(ourProjectRoot, "y/y/x");
    if (createdFile.exists()) createdFile.delete();
    File deletedFile = new File(ourProjectRoot, "bbb/bb.in");
    if (!deletedFile.exists()) deletedFile.createNewFile();
    File changedFile = new File(ourProjectRoot, "t/txt.txt.");
    PrintWriter p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();

    TreeActualizer a = new TreeActualizer();

    File dataStorageDirectory1 = FileUtil.createTempDirectory("comparer-test1-", null);
    ProjectHashedFileTree tree1 = new ProjectHashedFileTreeImpl(dataStorageDirectory1);
    System.err.println("Actualizing 1...");
    a.actualize(ourProjectRoot, tree1, ".", ".");
    System.out.println(tree1.listSubtree("."));

    createdFile.createNewFile();
    deletedFile.delete();
    PrintWriter q = new PrintWriter(changedFile);
    q.write("FILE CONTENT NUMBER TWO TWO TWO");
    q.close();

    File dataStorageDirectory2 = FileUtil.createTempDirectory("comparer-test2-", null);
    ProjectHashedFileTree tree2 = new ProjectHashedFileTreeImpl(dataStorageDirectory2);
    System.err.println("Actualizing 2...");
    a.actualize(ourProjectRoot, tree2, ".", ".");
    System.out.println(tree2.listSubtree("."));

    TreeDifferenceCollector c = new TreeDifferenceCollector();
    TreeComparator.compare(tree1, tree2, c, ".");
    System.out.println(c);

    if (createdFile.exists()) createdFile.delete();
    if (!deletedFile.exists()) deletedFile.createNewFile();
    p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();
  }

  public static void main(String[] args) throws IOException {
    //testActualize();
    //testCompare();
    /*try {
      Thread.sleep(10000);
    }
    catch (InterruptedException e) {
      System.err.println("Interrupted");
    }*/
    ideaTestActualize();
    //ideaTestCompare();
  }

  private static void ideaTestActualize() throws IOException {
    File changedFile = new File(ideaProjectRoot, "jps\\jps-builders\\src\\org\\jetbrains\\jps\\incremental\\storage\\Something.java");
    PrintWriter p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();

    TreeActualizer a = new TreeActualizer();
    File dataStorageDirectory = FileUtil.createTempDirectory("idea-actualizer-test-", null);
    ProjectHashedFileTree tree = new ProjectHashedFileTreeImpl(dataStorageDirectory);
    System.err.println("Actualizing 1...");

    long start1 = System.currentTimeMillis();
    a.actualize(ideaProjectRoot, tree, ".", ".");
    long finish1 = System.currentTimeMillis();

    System.err.println("Tree size: " + tree.nodesCount());
    System.err.println("Time consumed: " + (finish1 - start1)/1000.0);

    PrintWriter q = new PrintWriter(changedFile);
    q.write("FILE CONTENT NUMBER TWO TWO TWO");
    q.close();

    System.err.println("Actualizing 2...");

    long start2 = System.currentTimeMillis();
    a.actualize(ideaProjectRoot, tree, ".", ".");
    long finish2 = System.currentTimeMillis();

    System.err.println("Tree size: " + tree.nodesCount());
    System.err.println("Time consumed: " + (finish2 - start2)/1000.0);
  }

  private static void ideaTestCompare() throws IOException {
    File changedFile = new File(ideaProjectRoot, "jps\\jps-builders\\src\\org\\jetbrains\\jps\\incremental\\storage\\Something.java");
    PrintWriter p = new PrintWriter(changedFile);
    p.write("FILE CONTENT NUMBER ONE ONE ONE");
    p.close();

    File dataStorageDirectory1 = FileUtil.createTempDirectory("idea-comparator-test1-", null);
    ProjectHashedFileTree tree1 = new ProjectHashedFileTreeImpl(dataStorageDirectory1);
    System.err.println("Actualizing 1...");

    long start1 = System.currentTimeMillis();
    new TreeActualizer().actualize(ideaProjectRoot, tree1, ".", ".");
    long finish1 = System.currentTimeMillis();
    System.err.println("Time consumed 1: " + (finish1 - start1)/1000.0);

    PrintWriter q = new PrintWriter(changedFile);
    q.write("FILE CONTENT NUMBER TWO TWO TWO");
    q.close();

    File dataStorageDirectory2 = FileUtil.createTempDirectory("idea-comparator-test2-", null);
    ProjectHashedFileTree tree2 = new ProjectHashedFileTreeImpl(dataStorageDirectory2);
    System.err.println("Actualizing 2...");
    long start2 = System.currentTimeMillis();
    new TreeActualizer().actualize(ideaProjectRoot, tree2, ".", ".");
    long finish2 = System.currentTimeMillis();
    System.err.println("Time consumed 2: " + (finish2 - start2)/1000.0);

    TreeDifferenceCollector c = new TreeDifferenceCollector();
    long start3 = System.currentTimeMillis();
    TreeComparator.compare(tree1, tree2, c, ".");
    long finish3 = System.currentTimeMillis();
    System.err.println("Time consumed 3: " + (finish3 - start3)/1000.0);
    System.err.println(c);
  }
}
