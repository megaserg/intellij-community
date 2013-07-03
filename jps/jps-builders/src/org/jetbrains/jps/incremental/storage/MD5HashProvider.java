package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Sergey Serebryakov
 */
public class MD5HashProvider implements HashProvider {
  @Override
  public MessageDigest getMessageDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("No MD5 implementation? Really?");
    }
  }
}
