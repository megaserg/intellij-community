package org.jetbrains.jps.incremental.storage;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Sergey Serebryakov
 */
public class ProjectHashTreeImpl implements ProjectHashTree {
  private Map<String, String> hashes;

  public ProjectHashTreeImpl() {
    hashes = new HashMap<String, String>();
  }

  @Nullable
  @Override
  public String getHash(String path) {
    return hashes.get(path);
  }

  @Override
  public void putHash(String path, String hash) {
    //System.err.println("Updated hash for file " + path + " (" + hash + ")");
    hashes.put(path, hash);
  }

  @Override
  public void removeHash(String path) {
    hashes.remove(path);
  }

  @Override
  public int size() {
    return hashes.size();
  }
}
