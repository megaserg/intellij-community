package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Serebryakov
 */
public interface ProjectHashTree {
  @Nullable
  String getHash(String path);

  void putHash(String path, String hash);

  void removeHash(String path);

  int size();
}
