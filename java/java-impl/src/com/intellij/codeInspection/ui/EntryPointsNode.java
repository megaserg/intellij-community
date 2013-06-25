/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.deadCode.DummyEntryPointsTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.CommonInspectionToolWrapper;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class EntryPointsNode extends InspectionNode {
  public EntryPointsNode(@NotNull UnusedDeclarationInspection tool, @NotNull GlobalInspectionContextImpl context) {
    super(createDummyWrapper(tool, context));
  }

  private static CommonInspectionToolWrapper createDummyWrapper(UnusedDeclarationInspection tool, GlobalInspectionContextImpl context) {
    CommonInspectionToolWrapper wrapper = new CommonInspectionToolWrapper(new DummyEntryPointsTool());
    wrapper.initialize(context);
    return wrapper;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.EntryPoints;
  }
}
