package org.jetbrains.jps.incremental.storage;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public interface Checksums {
  void saveChecksum(File file, BuildTarget<?> buildTarget, String checksum) throws IOException;

  String getChecksum(File file, BuildTarget<?> target) throws IOException;

  void removeChecksum(File file, BuildTarget<?> buildTarget) throws IOException;

  void clean() throws IOException;

  void force();
}
