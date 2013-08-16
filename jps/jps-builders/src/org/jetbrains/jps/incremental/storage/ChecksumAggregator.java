package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Relativator;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * @author Sergey Serebryakov
 */
public class ChecksumAggregator extends AbstractAggregator {
  ProjectChecksums myChecksums;

  public ChecksumAggregator(Relativator relativator) throws IOException {
    super();
    myChecksums = new ProjectChecksums(myDataStorageRoot, myTargetsState, relativator);
  }

  @Override
  public void handle(File file, BuildTarget<?> target) throws IOException {
    String currentFileChecksum = FileUtil.computeChecksum(file);
    myChecksums.getStorage().saveChecksum(file, target, currentFileChecksum);
  }

  public void close() {
    myChecksums.close();
  }
}
