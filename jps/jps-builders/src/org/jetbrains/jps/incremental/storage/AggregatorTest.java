package org.jetbrains.jps.incremental.storage;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public class AggregatorTest {
  public static void main(String[] args) throws IOException {
    AbstractAggregator aggregator = new TimestampAggregator();
    //AbstractAggregator aggregator = new ChecksumAggregator();

    //aggregator.traverse(new File("C:\\Work\\TwinedDeps\\src"));

    long start = System.currentTimeMillis();
    aggregator.traverse(new File("C:\\Work\\intellij-community-13"));
    long finish = System.currentTimeMillis();

    System.out.println("Counted: " + aggregator.filesCounted);
    System.out.println("Time: " + (finish - start) / 1000.0);

    aggregator.close();
  }
}
