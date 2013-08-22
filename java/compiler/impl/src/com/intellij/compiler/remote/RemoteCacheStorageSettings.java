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

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Serebryakov
 */
@State(
  name = "RemoteCacheStorageSettings",
  storages = {@Storage(
    file = StoragePathMacros.APP_CONFIG + "/other.xml"
  )}
)
public class RemoteCacheStorageSettings implements PersistentStateComponent<RemoteCacheStorageSettings> {
  private String myServerAddress = null;

  private String mySFTPUsername = null;
  private String mySFTPPassword = null;
  private String mySFTPPath = null;

  private String myFTPUsername = null;
  private String myFTPPassword = null;
  private String myFTPPath = null;

  public static RemoteCacheStorageSettings getInstance() {
    return ServiceManager.getService(RemoteCacheStorageSettings.class);
  }

  @Nullable
  @Override
  public RemoteCacheStorageSettings getState() {
    return this;
  }

  @Override
  public void loadState(RemoteCacheStorageSettings state) {
    myServerAddress = state.getServerAddress();

    mySFTPUsername = state.getSFTPUsername();
    mySFTPPassword = state.getSFTPPassword();
    mySFTPPath = state.getSFTPPath();

    myFTPUsername = state.getFTPUsername();
    myFTPPassword = state.getFTPPassword();
    myFTPPath = state.getFTPPath();
  }

  public String getServerAddress() {
    return myServerAddress;
  }

  public void setServerAddress(String serverAddress) {
    myServerAddress = serverAddress;
  }

  public String getSFTPUsername() {
    return mySFTPUsername;
  }

  public void setSFTPUsername(String SFTPUsername) {
    mySFTPUsername = SFTPUsername;
  }

  public String getSFTPPassword() {
    return mySFTPPassword;
  }

  public void setSFTPPassword(String SFTPPassword) {
    mySFTPPassword = SFTPPassword;
  }

  public String getSFTPPath() {
    return mySFTPPath;
  }

  public void setSFTPPath(String SFTPPath) {
    mySFTPPath = SFTPPath;
  }

  public String getFTPUsername() {
    return myFTPUsername;
  }

  public void setFTPUsername(String FTPUsername) {
    myFTPUsername = FTPUsername;
  }

  public String getFTPPassword() {
    return myFTPPassword;
  }

  public void setFTPPassword(String FTPPassword) {
    myFTPPassword = FTPPassword;
  }

  public String getFTPPath() {
    return myFTPPath;
  }

  public void setFTPPath(String FTPPath) {
    myFTPPath = FTPPath;
  }
}
