/*
 * Copyright 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.workbench.common.screens.socialscreen.model;

import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.jboss.errai.common.client.api.annotations.Portable;

@Portable
public class Overview<T> {

    private String preview;
    private Metadata metadata;
    private String projectName;
    private T model;

    public String getPreview() {
        return preview;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public T getModel() {
        return model;
    }

    public void setModel(T model) {
        this.model = model;
    }
}