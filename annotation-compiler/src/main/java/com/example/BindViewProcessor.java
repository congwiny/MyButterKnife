package com.example;

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.squareup.javapoet.ClassName.bestGuess;

/**
 * Created by congwiny on 2017/8/17.
 */

/**
 * 使用@AutoService 注解processor 类，并对其生成 META-INF 的配置信息。
 * 这就辅助我们更容易地创建注解处理类，以便被apt识别出来(被ServiceLoader加载到)
 * BindViewProcessor被ServiceLoader加载到了后，就能处理自定义的注解了。
 */
@AutoService(Processor.class)
// Define the supported Java source code version
//@SupportedSourceVersion(SourceVersion.RELEASE_7)
// Define which annotation you want to process
//@SupportedAnnotationTypes("com.example.BindView")
public class BindViewProcessor extends AbstractProcessor {

    //处理节点的工具类
    private Elements elementUtils;
    private Types typeUtils;
    //生成Java文件辅助类
    private Filer filer;
    private static final ClassName VIEW_BINDER = ClassName.get("com.congwiny.inject", "ViewBinder");

    private static final String BINDING_CLASS_SUFFIX = "$$ViewBinder";//生成类的后缀 以后会用反射去取


    private ProcessingEnvironment processingEnv;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }


    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportedAnnoTypes = new LinkedHashSet<>();
        supportedAnnoTypes.add(BindView.class.getCanonicalName());
        return supportedAnnoTypes;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        processingEnv = processingEnvironment;
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        filer = processingEnvironment.getFiler();
    }

    //这个函数在javac编译时自动被调用。所有添加了@BindView注解的元素都会传递到这个方法里
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        /**
         Messager messager = processingEnv.getMessager();
         //获取工程中标记了@BindView注解的所有Element
         for (Element element:roundEnvironment.getElementsAnnotatedWith(BindView.class)){
         // 打印
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing toString(): " + element.toString());
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing getSimpleName: " + element.getSimpleName());
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing getEnclosingElement" + element.getEnclosingElement().toString());
         }

         for (TypeElement annotation:annotations){
         // 打印
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing set toString(): " + annotation.getQualifiedName());
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing set getSimpleName: " + annotation.getSimpleName());
         messager.printMessage(Diagnostic.Kind.NOTE, "Printing set getEnclosingElement" + annotation.getEnclosingElement().toString());
         }**/

        Map<TypeElement, List<FieldViewBinding>> targetClassMap = new LinkedHashMap<>();
        for (Element element : roundEnvironment.getElementsAnnotatedWith(BindView.class)) {
            if (!SuperficialValidation.validateElement(element))
                continue;
            // Start by verifying common generated code restrictions.
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            boolean hasError = isInaccessibleViaGeneratedCode(BindView.class, "fields", element)
                    || isBindingInWrongPackage(BindView.class, element);
            // Verify that the target type extends from View.
            TypeMirror elementType = element.asType();
            if (elementType.getKind() == TypeKind.TYPEVAR) {
                TypeVariable typeVariable = (TypeVariable) elementType;
                elementType = typeVariable.getUpperBound();
            }
            if (!isSubtypeOfType(elementType, "android.view.View") && !isInterface(elementType)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s fields must extend from View or be an interface. (%s.%s)",
                        BindView.class.getSimpleName(), enclosingElement.getQualifiedName(),
                        element.getSimpleName()));
                hasError = true;
            }

            if (hasError) {
                continue;
            }

            // Assemble information on the field.


            List<FieldViewBinding> fieldViewBindingList = targetClassMap.get(enclosingElement);

            if (fieldViewBindingList == null) {
                fieldViewBindingList = new ArrayList<>();
                targetClassMap.put(enclosingElement, fieldViewBindingList);
            }


            String packageName = getPackageName(enclosingElement);
            TypeName targetType = TypeName.get(enclosingElement.asType());
            int id = element.getAnnotation(BindView.class).value();
            String fieldName = element.getSimpleName().toString();
            TypeMirror fieldType = element.asType();

            FieldViewBinding fieldViewBinding = new FieldViewBinding(fieldType, fieldName, id);
            fieldViewBindingList.add(fieldViewBinding);
        }

        for (Map.Entry<TypeElement, List<FieldViewBinding>> item : targetClassMap.entrySet()) {
            List<FieldViewBinding> list = item.getValue();
            if (list == null || list.size() == 0) {
                continue;
            }

            TypeElement enclosingElement = item.getKey();
            String packageName = getPackageName(enclosingElement);
            ClassName typeClassName = bestGuess(getClassName(enclosingElement, packageName));
            TypeSpec.Builder result = TypeSpec.classBuilder(getClassName(enclosingElement, packageName) + BINDING_CLASS_SUFFIX)
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(TypeVariableName.get("T", typeClassName))
                    .addSuperinterface(ParameterizedTypeName.get(VIEW_BINDER, typeClassName));
            result.addMethod(createBindMethod(list, typeClassName));
            try {
                JavaFile.builder(packageName, result.build())
                        .addFileComment(" This codes are generated automatically. Do not modify!")
                        .build().writeTo(filer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private MethodSpec createBindMethod(List<FieldViewBinding> list, ClassName typeClassName) {
        MethodSpec.Builder result = MethodSpec.methodBuilder("bind")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addAnnotation(Override.class)
                .addParameter(typeClassName, "target", Modifier.FINAL);

        for (int i = 0; i < list.size(); i++) {
            FieldViewBinding fieldViewBinding = list.get(i);

            String packageString = fieldViewBinding.getType().toString();
            //String className = fieldViewBinding.getType().getClass().getSimpleName();
            ClassName viewClass = bestGuess(packageString);
            result.addStatement("target.$L=($T)target.findViewById($L)", fieldViewBinding.getName(), viewClass, fieldViewBinding.getResId());
        }
        return result.build();
    }


    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass,
                                                   String targetThing, Element element) {
        boolean hasError = false;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify method modifiers.
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s must not be private or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName()));
            hasError = true;
        }

        // Verify containing type.
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName()));
            hasError = true;
        }

        // Verify containing class visibility is not private.
        if (enclosingElement.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may not be contained in private classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName()));
            hasError = true;
        }

        return hasError;
    }

    private boolean isBindingInWrongPackage(Class<? extends Annotation> annotationClass,
                                            Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }

        return false;
    }

    private boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (otherType.equals(typeMirror.toString())) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterface(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType
                && ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
    }

    private String getPackageName(TypeElement type) {
        return elementUtils.getPackageOf(type).getQualifiedName().toString();
    }

    private static String getClassName(TypeElement type, String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }

}
