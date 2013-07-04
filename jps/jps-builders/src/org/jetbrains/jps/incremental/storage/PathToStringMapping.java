package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Serebryakov
 */
public class PathToStringMapping extends AbstractStateStorage<String, String> {

  public PathToStringMapping(@NonNls File storePath) throws IOException {
    super(storePath, new PathStringDescriptor(), new StringExternalizer());
  }

  public void put(String keyPath, String value) {
    try {
      update(FileUtil.toSystemIndependentName(keyPath), value);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot put value to the persistent map");
    }
  }

  public String get(String path) {
    try {
      return getState(FileUtil.toSystemIndependentName(path));
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
  public void remove(String path) {
    try {
      super.remove(FileUtil.toSystemIndependentName(path));
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot remove value from the persistent map");
    }
  }

  protected static class StringExternalizer implements DataExternalizer<String> {

    @Override
    public void save(DataOutput out, String value) throws IOException {
      IOUtil.writeString(value, out);
    }

    @Override
    public String read(DataInput in) throws IOException {
      return IOUtil.readString(in);
    }
  }
}
