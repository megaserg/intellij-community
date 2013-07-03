package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;

/**
 * @author Sergey Serebryakov
 */
public interface HashProvider {
  MessageDigest getMessageDigest();
}
