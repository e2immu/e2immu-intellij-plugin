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

import com.google.common.collect.ImmutableList;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;

import static org.e2immu.intellij.highlighter.Constants.*;

import org.e2immu.intellij.highlighter.ElementName;
import org.e2immu.intellij.highlighter.ElementType;
import org.e2immu.intellij.highlighter.store.AnnotationStore;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.e2immu.intellij.highlighter.ElementType.*;

public class JavaAnnotator implements Annotator {
    private static final Logger LOGGER = Logger.getInstance(JavaAnnotator.class);

    private final JavaConfig config = JavaConfig.INSTANCE;
    private final AnnotationStore annotationStore = AnnotationStore.INSTANCE;

    private final Map<String, TextAttributes> textAttributesMap =
            TAK_MAP.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> EditorColorsManager.getInstance().getGlobalScheme().getAttributes(e.getValue())));

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        ConfigData configData = config.getState();
        if (element instanceof PsiIdentifier) {
            PsiIdentifier identifier = (PsiIdentifier) element;
            PsiElement parent = identifier.getParent();

            // for fields, highlight both the types and the field itself (@NotModified)
            if (configData.isHighlightDeclarations() && parent instanceof PsiField) {
                PsiField psiField = (PsiField) parent;

                // highlight the field
                if (psiField.getParent() instanceof PsiClass) {
                    PsiClass classOfField = (PsiClass) psiField.getParent();
                    ElementName fieldName = ElementName.fromField(classOfField, identifier);
                    LOGGER.debug("Marking field " + fieldName);
                    mark(holder, identifier, fieldName);

                    // highlight the type and type parameters
                    recursivelyFindTypeElements(psiField.getTypeElement(), typeElement ->
                            handleTypeElement(holder, typeElement, fieldName));

                    // and handle annotations
                    handleAnnotations(holder, psiField.getAnnotations(), FIELD);
                }
            }

            // only look at the types of local variables
            if (configData.isHighlightStatements() && parent instanceof PsiLocalVariable) {
                PsiLocalVariable localVariable = (PsiLocalVariable) parent;
                LOGGER.debug("Marking local variable " + identifier.getText());

                recursivelyFindTypeElements(localVariable.getTypeElement(), typeElement ->
                        handleTypeElement(holder, typeElement, null));
            }

            // in statements, we may look at method calls
            if (configData.isHighlightStatements()
                    && parent instanceof PsiReferenceExpression
                    && parent.getParent() instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) parent.getParent();
                PsiMethod method = methodCallExpression.resolveMethod();
                if (method != null) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null) {
                        ElementName methodName = ElementName.fromMethod(containingClass, method);
                        LOGGER.debug("Marking method " + methodName);
                        mark(holder, identifier, methodName);
                    }
                }
            }
            if (configData.isHighlightDeclarations() && parent instanceof PsiMethod) {
                PsiMethod psiMethod = (PsiMethod) parent;
                if (psiMethod.getParent() instanceof PsiClass) {
                    PsiClass classOfMethod = (PsiClass) psiMethod.getParent();
                    ElementName methodName = ElementName.fromMethod(classOfMethod, psiMethod);
                    LOGGER.debug("Marking method " + methodName);

                    mark(holder, identifier, methodName);

                    // now also do the parameters
                    int index = 0;
                    for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {

                        // first, the types of the parameters
                        recursivelyFindTypeElements(parameter.getTypeElement(),
                                typeElement -> handleTypeElement(holder, typeElement, null));

                        // then, the parameter itself
                        ElementName parameterName = ElementName.parameter(methodName, index);
                        mark(holder, parameter.getIdentifyingElement(), parameterName);

                        // then, annotations on the parameter
                        handleAnnotations(holder, parameter.getAnnotations(), PARAM);
                        index++;
                    }

                    // look at the return type
                    PsiTypeElement returnTypeElement = psiMethod.getReturnTypeElement();
                    if (returnTypeElement != null) {
                        recursivelyFindTypeElements(returnTypeElement, typeElement ->
                                handleTypeElement(holder, typeElement, methodName));
                    }

                    // and check the annotations
                    handleAnnotations(holder, psiMethod.getAnnotations(), METHOD);
                }
            }
            if (configData.isHighlightDeclarations() && parent instanceof PsiClass) {
                PsiClass psiClass = (PsiClass) parent;
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName != null) {
                    mark(holder, identifier, ElementName.type(qualifiedName));
                }
                handleAnnotations(holder, psiClass.getAnnotations(), TYPE);
            }
        }
    }

    private void handleAnnotations(AnnotationHolder holder, PsiAnnotation[] annotations, ElementType context) {
        if (annotations == null || annotations.length == 0) return;
        for (PsiAnnotation annotation : annotations) {
            ElementName annotationName = ElementName.fromAnnotation(annotation, context);
            mark(holder, annotation.getNameReferenceElement(), annotationName);
        }
    }

    private void handleTypeElement(AnnotationHolder holder, PsiTypeElement typeElement, ElementName methodOrFieldContext) {
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType) type;
            PsiClass resolved = classType.resolve();
            if (resolved != null) {
                String qualifiedName = resolved.getQualifiedName();
                if (qualifiedName != null) {
                    PsiElement first = firstChild(typeElement);
                    ElementName staticType = ElementName.type(qualifiedName);
                    if (methodOrFieldContext != null) {
                        ElementName dynamicType = ElementName.dynamicTypeAnnotation(qualifiedName, methodOrFieldContext);
                        mark(holder, first, ImmutableList.of(dynamicType, staticType));
                    } else {
                        mark(holder, first, staticType);
                    }
                }
            }
        }
    }

    public static String typeOfParameter(PsiParameter psiParameter) {
        PsiType type = psiParameter.getType();
        if (type instanceof PsiPrimitiveType) {
            return ((PsiPrimitiveType) type).getName();
        }
        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();
            if (resolved == null) {
                return type.getCanonicalText();
            }
            if (resolved instanceof PsiTypeParameter) {
                PsiTypeParameter typeParameter = (PsiTypeParameter) resolved;
                String owner = (typeParameter.getOwner() instanceof PsiClass) ? "T" : "M";
                return owner + typeParameter.getIndex();
            }
            return resolved.getQualifiedName();
        }
        return type.getCanonicalText();
    }

    private static void recursivelyFindTypeElements(PsiElement element, Consumer<PsiTypeElement> whenTypeElement) {
        if (element instanceof PsiTypeElement) whenTypeElement.accept((PsiTypeElement) element);
        for (PsiElement child : element.getChildren()) {
            recursivelyFindTypeElements(child, whenTypeElement);
        }
    }

    private static PsiElement firstChild(PsiElement element) {
        if (element == null) return null;
        if (element.getFirstChild() != null) return firstChild(element.getFirstChild());
        return element;
    }

    private void mark(AnnotationHolder holder, PsiElement element, ElementName elementName) {
        mark(holder, element, ImmutableList.of(elementName));
    }

    private void mark(AnnotationHolder holder, PsiElement element, List<ElementName> elementNames) {
        annotationStore.mapAndMark(elementNames, annotationName -> {
            TextAttributes textAttributes = textAttributesMap.get(annotationName);
            if (textAttributes != null && (config.getState().isHighlightUnknownTypes() || isValidAnnotation(annotationName))) {
                holder.newAnnotation(HighlightSeverity.INFORMATION, annotationName).range(element).enforcedTextAttributes(textAttributes).create();
            }
        });
    }

    private static boolean isValidAnnotation(String annotationName) {
        return !NOT_ANNOTATED_FIELD.equals(annotationName) &&
                !NOT_ANNOTATED_METHOD.equals(annotationName) &&
                !NOT_ANNOTATED_PARAM.equals(annotationName) &&
                !NOT_ANNOTATED_TYPE.equals(annotationName);
    }

}
