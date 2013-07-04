package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Sergey Serebryakov
 */
public class PathToChildrenMapping extends AbstractStateStorage<String, Collection<String>> {

  public PathToChildrenMapping(File storePath) throws IOException {
    super(storePath, new PathStringDescriptor(), new PathCollectionExternalizer());
  }

  private static Collection<String> normalizePaths(Collection<String> outputs) {
    Collection<String> normalized = new ArrayList<String>(outputs.size());
    for (String out : outputs) {
      normalized.add(FileUtil.toSystemIndependentName(out));
    }
    return normalized;
  }

  public void put(String path, Collection<String> children) {
    try {
      super.update(FileUtil.toSystemIndependentName(path), normalizePaths(children));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot put value to the persistent map");
    }
  }

  public Collection<String> get(String path) {
    try {
      return super.getState(FileUtil.toSystemIndependentName(path));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot get value from the persistent map");
    }
  }

  public boolean containsKey(String path) {
    try {
      return super.containsKey(FileUtil.toSystemIndependentName(path));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot look for key in the persistent map");
    }
  }

  @Override
  public void remove(@NotNull String path) {
    try {
      super.remove(FileUtil.toSystemIndependentName(path));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot remove key from the persistent map");
    }
  }

  public void appendChild(String parent, String child) {
    parent = FileUtil.toSystemIndependentName(parent);
    child = FileUtil.toSystemIndependentName(child);
    try {
      super.appendData(parent, Collections.singleton(child));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot append value to the persistent map");
    }
  }

  public void removeChild(String parent, String child) {
    parent = FileUtil.toSystemIndependentName(parent);
    child = FileUtil.toSystemIndependentName(child);
    try {
      final Collection<String> children = super.getState(parent);
      if (children != null) {
        final boolean removed = children.remove(child);
        if (removed) {
          super.update(parent, children);
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot remove value from the persistent map");
    }
  }


  private static class PathCollectionExternalizer implements DataExternalizer<Collection<String>> {
    public void save(DataOutput out, Collection<String> value) throws IOException {
      for (String str : value) {
        IOUtil.writeString(str, out);
      }
    }

    public Collection<String> read(DataInput in) throws IOException {
      final Set<String> result = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String str = IOUtil.readString(stream);
        result.add(str);
      }
      return result;
    }
  }
}
