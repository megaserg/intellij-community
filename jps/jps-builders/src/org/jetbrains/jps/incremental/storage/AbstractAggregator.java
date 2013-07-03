package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
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
public abstract class AbstractAggregator {
  protected JpsModel myModel;
  protected File myDataStorageRoot;
  protected BuildTargetsState myTargetsState;
  protected BuildRootIndexImpl myBuildRootIndex;
  public int filesCounted;

  public AbstractAggregator() throws IOException {
    myModel = JpsElementFactory.getInstance().createModel();
    myDataStorageRoot = FileUtil.createTempDirectory("compile-server-aggregator-", null);
    BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(myModel);
    ModuleExcludeIndex index = new ModuleExcludeIndexImpl(myModel);
    IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(myModel);
    BuildDataPaths dataPaths = new BuildDataPathsImpl(myDataStorageRoot);
    myBuildRootIndex = new BuildRootIndexImpl(targetIndex, myModel, index, dataPaths, ignoredFileIndex);
    myTargetsState = new BuildTargetsState(dataPaths, myModel, myBuildRootIndex);
    filesCounted = 0;
  }

  public void traverse(File file) throws IOException {
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        traverse(child);
      }
    }
    else {
      BuildTargetType<BuildTarget<BuildRootDescriptor>> targetType = new BuildTargetType<BuildTarget<BuildRootDescriptor>>("" + new Random().nextLong()) {
        @NotNull
        @Override
        public List<BuildTarget<BuildRootDescriptor>> computeAllTargets(@NotNull JpsModel model) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @NotNull
        @Override
        public BuildTargetLoader<BuildTarget<BuildRootDescriptor>> createLoader(@NotNull JpsModel model) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
      };

      BuildTarget<BuildRootDescriptor> target = new BuildTarget<BuildRootDescriptor>(targetType) {
        @Override
        public String getId() {
          return "" + new Random().nextLong();  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @NotNull
        @Override
        public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                                ModuleExcludeIndex index,
                                                                IgnoredFileIndex ignoredFileIndex,
                                                                BuildDataPaths dataPaths) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Nullable
        @Override
        public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @NotNull
        @Override
        public String getPresentableName() {
          return "name" + getId();  //To change body of implemented methods use File | Settings | File Templates.
        }

        @NotNull
        @Override
        public Collection<File> getOutputRoots(CompileContext context) {
          return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
      };

      handle(file, target);
      filesCounted++;
    }
  }

  public abstract void handle(File file, BuildTarget<?> target) throws IOException;

  public abstract void close();
}
