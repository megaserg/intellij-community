package com.intellij.compiler.remote;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Sergey Serebryakov
 */
public class DirectoryDecompressor {
  private static byte[] buffer = new byte[1024];

  public static void decompressDirectory(String zipFilePath, File destinationDirectory) throws IOException {
    InputStream in = new FileInputStream(zipFilePath);
    ZipInputStream zin = new ZipInputStream(in);

    try {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        String fileName = entry.getName();
        File destinationFile = new File(destinationDirectory, fileName);

        destinationFile.getParentFile().mkdirs();

        OutputStream fos = new FileOutputStream(destinationFile);
        try {
          int length;
          while ((length = zin.read(buffer)) > 0) {
            fos.write(buffer, 0, length);
          }
        }
        finally {
          fos.close();
        }

        zin.closeEntry();
      }
    }
    finally {
      zin.close();
    }
  }
}
