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
package com.intellij.tasks.jira.model;

/**
 * @author Mikhail Golubev
 */
public class JiraIssueType {

  private String id;
  private String self;
  private String name;
  private String description;
  private String iconUrl;
  private boolean subtask;

  @Override
  public String toString() {
    return String.format("JiraIssueType(name=%s)", name);
  }

  public String getId() {
    return id;
  }

  public String getIssueTypeUrl() {
    return self;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getIconUrl() {
    return iconUrl;
  }

  public boolean isSubtask() {
    return subtask;
  }
}
