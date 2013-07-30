package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.tooling.GradleConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

/**
 * We store not gradle config file but its parent dir path instead. That is implied by gradle design
 * ({@link GradleConnector#forProjectDirectory(File)}).
 * <p/>
 * That's why we need to provide special code which maps that directory to exact config file.
 * 
 * @author Denis Zhdanov
 * @since 7/16/13 3:43 PM
 */
public class GradleConfigLocator implements ExternalSystemConfigLocator {

  @NotNull
  @Override
  public ProjectSystemId getTargetExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Nullable
  @Override
  public VirtualFile adjust(@NotNull VirtualFile configPath) {
    if (!configPath.isDirectory()) {
      return configPath;
    }

    VirtualFile result = configPath.findChild(GradleConstants.DEFAULT_SCRIPT_NAME);
    if (result != null) {
      return result;
    }
    
    for (VirtualFile child : configPath.getChildren()) {
      String name = child.getName();
      if (!name.endsWith(GradleConstants.EXTENSION)) {
        continue;
      }
      if (!GradleConstants.SETTINGS_FILE_NAME.equals(name) && !child.isDirectory()) {
        return child;
      }
    }
    return null;
  }
}
