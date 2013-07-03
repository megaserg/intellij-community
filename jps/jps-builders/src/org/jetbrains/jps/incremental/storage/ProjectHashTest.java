package org.jetbrains.jps.incremental.storage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

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
    p.write(""+new Random().nextInt());
    p.close();

    TreeActualizer a = new TreeActualizer();

    ProjectFileTree ft = new ProjectFileTreeImpl();
    ProjectHashTree ht = new ProjectHashTreeImpl();
    System.err.println("Actualizing 1...");
    a.actualize(ourProjectRoot, ft, ht, ".", ".");
    System.out.println(ft.listSubtree("."));

    createdFile.createNewFile();
    deletedFile.delete();
    PrintWriter q = new PrintWriter(changedFile);
    q.write(""+new Random().nextInt());
    q.close();

    System.err.println("Actualizing 2...");
    a.actualize(ourProjectRoot, ft, ht, ".", ".");
    System.out.println(ft.listSubtree("."));
    System.out.println("File tree size: " + ft.size());
    System.out.println("Hash tree size: " + ht.size());

    if (createdFile.exists()) createdFile.delete();
    if (!deletedFile.exists()) deletedFile.createNewFile();
    p = new PrintWriter(changedFile);
    p.write(""+new Random().nextInt());
    p.close();
  }

  public static void testCompare() throws IOException {
    File createdFile = new File(ourProjectRoot, "y/y/x");
    if (createdFile.exists()) createdFile.delete();
    File deletedFile = new File(ourProjectRoot, "bbb/bb.in");
    if (!deletedFile.exists()) deletedFile.createNewFile();
    File changedFile = new File(ourProjectRoot, "t/txt.txt.");
    PrintWriter p = new PrintWriter(changedFile);
    p.write(""+new Random().nextInt());
    p.close();

    TreeActualizer a = new TreeActualizer();

    ProjectFileTree ft1 = new ProjectFileTreeImpl();
    ProjectHashTree ht1 = new ProjectHashTreeImpl();
    System.err.println("Actualizing 1...");
    a.actualize(ourProjectRoot, ft1, ht1, ".", ".");
    System.out.println(ft1.listSubtree("."));

    createdFile.createNewFile();
    deletedFile.delete();
    PrintWriter q = new PrintWriter(changedFile);
    q.write(""+new Random().nextInt());
    q.close();

    ProjectFileTree ft2 = new ProjectFileTreeImpl();
    ProjectHashTree ht2 = new ProjectHashTreeImpl();
    System.err.println("Actualizing 2...");
    a.actualize(ourProjectRoot, ft2, ht2, ".", ".");
    System.out.println(ft2.listSubtree("."));

    TreeDifferenceCollector c = new TreeDifferenceCollector();
    TreeComparator.compare(ft2, ft1, ht2, ht1, c, ".");
    System.out.println(c);

    if (createdFile.exists()) createdFile.delete();
    if (!deletedFile.exists()) deletedFile.createNewFile();
    p = new PrintWriter(changedFile);
    p.write(""+new Random().nextInt());
    p.close();
  }

  public static void main(String[] args) throws IOException {
    //testActualize();
    //testCompare();
    stressTestActualize();
  }

  private static void stressTestActualize() throws IOException {
    TreeActualizer a = new TreeActualizer();
    ProjectFileTree ft = new ProjectFileTreeImpl();
    ProjectHashTree ht = new ProjectHashTreeImpl();
    System.err.println("Actualizing...");

    long start = System.currentTimeMillis();
    a.actualize(ideaProjectRoot, ft, ht, ".", ".");
    long finish = System.currentTimeMillis();

    System.err.println("File tree size: " + ft.size());
    System.err.println("Hash tree size: " + ht.size());
    //System.out.println(ft.listSubtree("."));
    System.err.println("Time consumed: " + (finish - start)/1000.0);
  }
}
