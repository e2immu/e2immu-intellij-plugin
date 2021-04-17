/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.intellij.highlighter.java;

/**
 * NOTE: if the setters are not present, IntelliJ will not save the preferences
 * So we can make it eventually immutable
 */
public class ConfigData {
    // presence needed for Xml Serialization
    public ConfigData() {
        this(true, true, true,
                "http://localhost:8281", "default");
    }

    public ConfigData(boolean isHighlightDeclarations,
                      boolean isHighlightUnknownTypes,
                      boolean isHighlightStatements,
                      String annotationServerUrl,
                      String annotationProject) {
        this.isHighlightDeclarations = isHighlightDeclarations;
        this.isHighlightStatements = isHighlightStatements;
        this.isHighlightUnknownTypes = isHighlightUnknownTypes;
        this.annotationServerUrl = annotationServerUrl;
        this.annotationProject = annotationProject;
    }

    private boolean isHighlightDeclarations;
    private boolean isHighlightUnknownTypes;
    private boolean isHighlightStatements;
    private String annotationServerUrl;
    private String annotationProject;

    public String getAnnotationProject() {
        return annotationProject;
    }

    public String getAnnotationServerUrl() {
        return annotationServerUrl;
    }

    public boolean isHighlightDeclarations() {
        return isHighlightDeclarations;
    }

    public boolean isHighlightStatements() {
        return isHighlightStatements;
    }

    public boolean isHighlightUnknownTypes() {
        return isHighlightUnknownTypes;
    }

    public void setAnnotationProject(String annotationProject) {
        this.annotationProject = annotationProject;
    }

    public void setAnnotationServerUrl(String annotationServerUrl) {
        this.annotationServerUrl = annotationServerUrl;
    }

    public void setHighlightDeclarations(boolean highlightDeclarations) {
        isHighlightDeclarations = highlightDeclarations;
    }

    public void setHighlightStatements(boolean highlightStatements) {
        isHighlightStatements = highlightStatements;
    }

    public void setHighlightUnknownTypes(boolean highlightUnknownTypes) {
        isHighlightUnknownTypes = highlightUnknownTypes;
    }
}
