package com.intellij.compiler.treediff.digest;

import java.security.MessageDigest;

/**
 * @author Sergey Serebryakov
 */
public interface HashProvider {
  MessageDigest getMessageDigest();
}
