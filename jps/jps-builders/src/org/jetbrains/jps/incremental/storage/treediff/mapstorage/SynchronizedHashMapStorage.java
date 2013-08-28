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
package org.jetbrains.jps.incremental.storage.treediff.mapstorage;

import java.io.*;
import java.util.HashMap;

/**
 * @author Sergey Serebryakov
 */
public abstract class SynchronizedHashMapStorage<Key, Value> {
  protected final Object myDataLock = new Object();
  private final File myStorePath;
  private HashMap<Key, Value> myMap;

  public SynchronizedHashMapStorage(File storePath) {
    myStorePath = storePath;
    synchronized (myDataLock) {
      myMap = new HashMap<Key, Value>();
    }
  }

  public void put(Key key, Value value) {
    synchronized (myDataLock) {
      myMap.put(key, value);
    }
  }

  public Value get(Key key) {
    synchronized (myDataLock) {
      return myMap.get(key);
    }
  }

  public boolean containsKey(Key key) {
    synchronized (myDataLock) {
      return myMap.containsKey(key);
    }
  }

  public void remove(Key key) {
    synchronized (myDataLock) {
      myMap.remove(key);
    }
  }

  public int size() {
    synchronized (myDataLock) {
      return myMap.size();
    }
  }

  public void save() throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(myStorePath));
    try {
      synchronized (myDataLock) {
        oos.writeObject(myMap);
      }
    }
    finally {
      oos.close();
    }
  }

  public void load() throws IOException {
    ObjectInputStream in = new ObjectInputStream(new FileInputStream(myStorePath));
    try {
      synchronized (myDataLock) {
        myMap = (HashMap<Key, Value>)in.readObject();
      }
    }
    catch (ClassNotFoundException e) {
      System.err.println("No HashMap? Really?");
    }
    finally {
      in.close();
    }
  }
}