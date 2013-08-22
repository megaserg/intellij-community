/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.remote;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.Deque;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Sergey Serebryakov
 */
public class DirectoryCompressor {
  private static byte[] buffer = new byte[1024];

  private static void copyFileToStream(File file, OutputStream out) throws IOException {
    InputStream in = new FileInputStream(file);
    try {
      int length;
      while ((length = in.read(buffer)) > 0) {
        out.write(buffer, 0, length);
      }
    }
    finally {
      in.close();
    }
  }

  public static void compressDirectory(String zipFilePath, File directory) throws IOException {
    Deque<File> queue = new LinkedList<File>();
    queue.push(directory);

    File base = directory;

    OutputStream out = new FileOutputStream(zipFilePath);
    ZipOutputStream zout = new ZipOutputStream(out);
    try {
      zout.setMethod(ZipOutputStream.DEFLATED);
      zout.setLevel(0); // no actual compressing

      while (!queue.isEmpty()) {
        File dir = queue.pop();
        //File[] children = dir.listFiles();

        for (File child : dir.listFiles()) {
          if (child.isDirectory()) {
            queue.push(child);
          }
          else {
            String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(base, child));
            zout.putNextEntry(new ZipEntry(relativePath));
            copyFileToStream(child, zout);
            zout.closeEntry();
          }
        }
      }
    }
    finally {
      zout.close();
    }
  }
}
