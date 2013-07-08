package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
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
    super(storePath, new EnumeratorStringDescriptor(), new PathCollectionExternalizer());
  }

  public void put(String path, Collection<String> children) {
    try {
      super.update(FileUtil.toSystemIndependentName(path), children);
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

  @Override
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

  public void appendChild(String parent, String childName) {
    try {
      super.appendData(FileUtil.toSystemIndependentName(parent), Collections.singleton(childName));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot append value to the persistent map");
    }
  }

  public void removeChild(String parent, String childName) {
    parent = FileUtil.toSystemIndependentName(parent);
    try {
      final Collection<String> children = super.getState(parent);
      if (children != null) {
        final boolean removed = children.remove(childName);
        if (removed) {
          // The collection might be empty now, but it's OK.
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
        /*IOUtil.writeString(str, out);*/
        out.writeInt(str.length());
        out.writeChars(str);
      }
    }

    public Collection<String> read(DataInput in) throws IOException {
      final Set<String> result = new HashSet<String>(); //THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        /*final String str = IOUtil.readString(stream);*/
        int length = in.readInt();
        char[] chars = new char[length];
        for (int j = 0; j < length; j++) {
          chars[j] = in.readChar();
        }
        result.add(new String(chars));
      }
      return result;
    }
  }
}
