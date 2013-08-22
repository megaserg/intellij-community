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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Sergey Serebryakov
 */
public class RemoteCacheStorageConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "preferences.RemoteCacheStorage";
  public static final String NAME = "Remote Cache Storage";

  private final RemoteCacheStorageSettings mySettings;
  private RemoteCacheStoragePanel myPanel;

  public RemoteCacheStorageConfigurable(RemoteCacheStorageSettings settings) {
    mySettings = settings;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return NAME;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myPanel = new RemoteCacheStoragePanel();
    return myPanel.myWholePanel;
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified(mySettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply(mySettings);
  }

  @Override
  public void reset() {
    myPanel.reset(mySettings);
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  public static class RemoteCacheStoragePanel {
    private JPanel myWholePanel;

    private JTextField myServerAddressField;

    private JTextField mySFTPUsernameField;
    private JPasswordField mySFTPPasswordField;
    private JTextField mySFTPPathField;

    private JTextField myFTPUsernameField;
    private JPasswordField myFTPPasswordField;
    private JTextField myFTPPathField;

    public RemoteCacheStoragePanel() {
    }

    private static void updateField(JTextField field, final String value) {
      if (value != null) {
        field.setText(value);
      }
    }

    private void reset(RemoteCacheStorageSettings settings) {
      updateField(myServerAddressField, settings.getServerAddress());

      updateField(mySFTPUsernameField, settings.getSFTPUsername());
      updateField(mySFTPPasswordField, settings.getSFTPPassword());
      updateField(mySFTPPathField, settings.getSFTPPath());

      updateField(myFTPUsernameField, settings.getFTPUsername());
      updateField(myFTPPasswordField, settings.getFTPPassword());
      updateField(myFTPPathField, settings.getFTPPath());
    }

    private void apply(RemoteCacheStorageSettings settings) {
      settings.setServerAddress(myServerAddressField.getText().trim());

      settings.setSFTPUsername(mySFTPUsernameField.getText().trim());
      settings.setSFTPPassword(new String(mySFTPPasswordField.getPassword()).trim());
      settings.setSFTPPath(mySFTPPathField.getText().trim());

      settings.setFTPUsername(myFTPUsernameField.getText().trim());
      settings.setFTPPassword(new String(myFTPPasswordField.getPassword()).trim());
      settings.setFTPPath(myFTPPathField.getText().trim());
    }

    private static boolean isFieldModified(JTextField field, String expected) {
      return !Comparing.equal(field.getText().trim(), expected != null ? expected.trim() : null);
    }

    private boolean isModified(RemoteCacheStorageSettings settings) {
      if (isFieldModified(myServerAddressField, settings.getServerAddress())) return true;

      if (isFieldModified(mySFTPUsernameField, settings.getSFTPUsername())) return true;
      if (isFieldModified(mySFTPPasswordField, settings.getSFTPPassword())) return true;
      if (isFieldModified(mySFTPPathField, settings.getSFTPPath())) return true;

      if (isFieldModified(myFTPUsernameField, settings.getFTPUsername())) return true;
      if (isFieldModified(myFTPPasswordField, settings.getFTPPassword())) return true;
      if (isFieldModified(myFTPPathField, settings.getFTPPath())) return true;

      return false;
    }

    private void createUIComponents() {
      // Place custom component creation code here
    }
  }
}
