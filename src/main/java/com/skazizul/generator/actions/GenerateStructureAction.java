package com.skazizul.generator.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GenerateStructureAction extends AnAction {

    private static final String[] COMPONENTS = {
            "Repository",
            "Service Interface",
            "Service Implementation",
            "Controller",
            "Request DTO",
            "Update Request DTO",
            "Response DTO",
            "Mapper",
            "Not Found Exception"
    };

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        PsiClass entity = getSelectedClass(event);

        if (entity == null || !isEntity(entity)) {
            Messages.showWarningDialog(
                    project,
                    "No JPA Entity class was found.\nPlace the caret inside an @Entity class or right-click its Java file.",
                    "Spring Boot Generator"
            );
            return;
        }

        String entityName = entity.getName();
        if (entityName == null) return;

        JCheckBox[] checkBoxes = new JCheckBox[COMPONENTS.length];
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));

        for (int i = 0; i < COMPONENTS.length; i++) {
            checkBoxes[i] = new JCheckBox(COMPONENTS[i], true);
            panel.add(checkBoxes[i]);
        }

        GenerateDialog dialog = new GenerateDialog(
                project, panel, "Generate Components - " + entityName
        );

        if (!dialog.showAndGet()) return;

        List<String> selected = new ArrayList<>();

        for (int i = 0; i < checkBoxes.length; i++) {
            if (checkBoxes[i].isSelected()) {
                selected.add(COMPONENTS[i]);
            }
        }

        if (selected.isEmpty()) {
            Messages.showWarningDialog(
                    project,
                    "Select at least one component.",
                    "Spring Boot Generator"
            );
            return;
        }

        WriteCommandAction.runWriteCommandAction(
                project,
                "Generate Spring Boot Components",
                null,
                () -> generateComponents(project, entity, selected)
        );

        Messages.showInfoMessage(
                project,
                "Generation completed for " + entityName
                        + ". Existing files were not overwritten.",
                "Spring Boot Generator"
        );
    }

    // -------------------------------------------------------------------------
    // Dialog
    // -------------------------------------------------------------------------

    private static class GenerateDialog extends DialogWrapper {

        private final JPanel panel;

        protected GenerateDialog(
                Project project,
                JPanel panel,
                String title
        ) {
            super(project);
            this.panel = panel;
            setTitle(title);
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            return panel;
        }

        @Override
        protected Action[] createActions() {
            return new Action[]{
                    getOKAction(),
                    getCancelAction()
            };
        }
    }

    // -------------------------------------------------------------------------
    // Entity Selection
    // -------------------------------------------------------------------------

    private PsiClass getSelectedClass(AnActionEvent event) {

        PsiElement contextElement =
                event.getData(CommonDataKeys.PSI_ELEMENT);

        PsiClass classFromElement =
                findContainingClass(contextElement);

        if (classFromElement != null) {
            return classFromElement;
        }

        PsiFile psiFile =
                event.getData(CommonDataKeys.PSI_FILE);

        if (psiFile == null) {

            var editor = event.getData(CommonDataKeys.EDITOR);
            Project project = event.getProject();

            if (editor != null && project != null) {

                psiFile = PsiDocumentManager.getInstance(project)
                        .getPsiFile(editor.getDocument());

                if (psiFile != null) {

                    int offset =
                            editor.getCaretModel().getOffset();

                    PsiElement caretElement =
                            psiFile.findElementAt(offset);

                    PsiClass classAtCaret =
                            findContainingClass(caretElement);

                    if (classAtCaret != null) {
                        return classAtCaret;
                    }
                }
            }
        }

        if (psiFile instanceof PsiJavaFile javaFile) {

            for (PsiClass candidate : javaFile.getClasses()) {

                if (isEntity(candidate)) {
                    return candidate;
                }
            }

            PsiClass[] classes = javaFile.getClasses();

            if (classes.length == 1) {
                return classes[0];
            }
        }

        var editor = event.getData(CommonDataKeys.EDITOR);
        Project project = event.getProject();

        if (editor != null && project != null) {

            PsiFile file = PsiDocumentManager.getInstance(project)
                    .getPsiFile(editor.getDocument());

            if (file != null) {

                PsiElement caretElement = file.findElementAt(
                        editor.getCaretModel().getOffset()
                );

                return findContainingClass(caretElement);
            }
        }

        return null;
    }

    private PsiClass findContainingClass(PsiElement element) {

        if (element == null) {
            return null;
        }

        if (element instanceof PsiClass psiClass) {
            return psiClass;
        }

        return PsiTreeUtil.getParentOfType(
                element,
                PsiClass.class,
                false
        );
    }

    // -------------------------------------------------------------------------
    // Entity Detection
    // -------------------------------------------------------------------------

    private boolean isEntity(PsiClass psiClass) {

        if (psiClass == null
                || psiClass.getModifierList() == null) {
            return false;
        }

        for (PsiAnnotation annotation :
                psiClass.getModifierList().getAnnotations()) {

            String qualifiedName =
                    annotation.getQualifiedName();

            if ("jakarta.persistence.Entity".equals(qualifiedName)
                    || "javax.persistence.Entity".equals(qualifiedName)) {
                return true;
            }

            PsiJavaFile javaFile =
                    psiClass.getContainingFile() instanceof PsiJavaFile
                            ? (PsiJavaFile) psiClass.getContainingFile()
                            : null;

            if (javaFile == null
                    || javaFile.getImportList() == null) {
                continue;
            }

            PsiJavaCodeReferenceElement reference =
                    annotation.getNameReferenceElement();

            String shortName = reference == null
                    ? null
                    : reference.getReferenceName();

            if (!"Entity".equals(shortName)) {
                continue;
            }

            for (PsiImportStatement importStatement :
                    javaFile.getImportList().getImportStatements()) {

                String importName =
                        importStatement.getQualifiedName();

                if ("jakarta.persistence.Entity".equals(importName)
                        || "javax.persistence.Entity".equals(importName)
                        || "jakarta.persistence.*".equals(importName)
                        || "javax.persistence.*".equals(importName)) {
                    return true;
                }
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Generation Coordinator
    // -------------------------------------------------------------------------

    private void generateComponents(
            Project project,
            PsiClass entity,
            List<String> selected
    ) {

        String entityName = entity.getName();
        String entityQualifiedName = entity.getQualifiedName();

        if (entityName == null || entityQualifiedName == null) {

            Messages.showErrorDialog(
                    project,
                    "Could not resolve the Entity class name/package.",
                    "Generator Error"
            );

            return;
        }

        PsiDirectory sourceDirectory =
                getSourceDirectory(project, entity);

        if (sourceDirectory == null) {

            Messages.showErrorDialog(
                    project,
                    "Could not find the Java source root. Make sure the Entity is under a marked source folder such as src/main/java.",
                    "Generator Error"
            );

            return;
        }

        String entityPackage =
                ((PsiJavaFile) entity.getContainingFile()).getPackageName();

        String rootPackage =
                getRootPackage(entityPackage);

        String repositoryPackage =
                subPackage(rootPackage, "repository");

        String servicePackage =
                subPackage(rootPackage, "service");

        String implPackage =
                subPackage(rootPackage, "service.impl");

        String controllerPackage =
                subPackage(rootPackage, "controller");

        String requestPackage =
                subPackage(rootPackage, "dto.request");

        String responsePackage =
                subPackage(rootPackage, "dto.response");

        String mapperPackage =
                subPackage(rootPackage, "mapper");

        String exceptionPackage =
                subPackage(rootPackage, "exception");

        String idType = getIdType(entity);

        boolean needsId =
                selected.contains("Repository")
                        || selected.contains("Service Interface")
                        || selected.contains("Service Implementation")
                        || selected.contains("Controller");

        if (needsId && idType == null) {

            Messages.showErrorDialog(
                    project,
                    "No @Id or @EmbeddedId field was found in "
                            + entityName + ".",
                    "Generator Error"
            );

            return;
        }

        if (selected.contains("Repository")) {

            createClass(
                    project,
                    sourceDirectory,
                    repositoryPackage,
                    entityName + "Repository",
                    repositoryCode(
                            repositoryPackage,
                            entityQualifiedName,
                            entityName,
                            idType
                    )
            );
        }

        if (selected.contains("Service Interface")) {

            createClass(
                    project,
                    sourceDirectory,
                    servicePackage,
                    entityName + "Service",
                    serviceCode(
                            servicePackage,
                            entityQualifiedName,
                            entityName,
                            idType
                    )
            );
        }

        if (selected.contains("Service Implementation")) {

            createClass(
                    project,
                    sourceDirectory,
                    implPackage,
                    entityName + "ServiceImpl",
                    serviceImplCode(
                            implPackage,
                            entityQualifiedName,
                            entityName,
                            repositoryPackage,
                            servicePackage,
                            exceptionPackage,
                            idType
                    )
            );
        }

        if (selected.contains("Controller")) {

            createClass(
                    project,
                    sourceDirectory,
                    controllerPackage,
                    entityName + "Controller",
                    controllerCode(
                            controllerPackage,
                            entityQualifiedName,
                            entityName,
                            servicePackage,
                            idType
                    )
            );
        }

        if (selected.contains("Request DTO")) {

            createClass(
                    project,
                    sourceDirectory,
                    requestPackage,
                    entityName + "Request",
                    dtoCode(
                            requestPackage,
                            entityName + "Request",
                            entity,
                            true
                    )
            );
        }

        if (selected.contains("Update Request DTO")) {

            createClass(
                    project,
                    sourceDirectory,
                    requestPackage,
                    entityName + "UpdateRequest",
                    dtoCode(
                            requestPackage,
                            entityName + "UpdateRequest",
                            entity,
                            true
                    )
            );
        }

        if (selected.contains("Response DTO")) {

            createClass(
                    project,
                    sourceDirectory,
                    responsePackage,
                    entityName + "Response",
                    dtoCode(
                            responsePackage,
                            entityName + "Response",
                            entity,
                            false
                    )
            );
        }

        if (selected.contains("Mapper")) {

            createClass(
                    project,
                    sourceDirectory,
                    mapperPackage,
                    entityName + "Mapper",
                    mapperCode(
                            mapperPackage,
                            entityQualifiedName,
                            requestPackage,
                            responsePackage,
                            entityName,
                            entity
                    )
            );
        }

        if (selected.contains("Not Found Exception")) {

            createClass(
                    project,
                    sourceDirectory,
                    exceptionPackage,
                    entityName + "NotFoundException",
                    exceptionCode(
                            exceptionPackage,
                            entityName
                    )
            );
        }
    }

    // -------------------------------------------------------------------------
    // Package / Source Root Helpers
    // -------------------------------------------------------------------------

    private PsiDirectory getSourceDirectory(
            Project project,
            PsiClass entity
    ) {

        PsiFile containingFile = entity.getContainingFile();

        if (containingFile == null
                || containingFile.getVirtualFile() == null) {
            return null;
        }

        VirtualFile sourceRoot =
                ProjectFileIndex.getInstance(project)
                        .getSourceRootForFile(
                                containingFile.getVirtualFile()
                        );

        if (sourceRoot == null) {
            return null;
        }

        return PsiManager.getInstance(project)
                .findDirectory(sourceRoot);
    }

    private String getRootPackage(String entityPackage) {

        if (entityPackage == null || entityPackage.isBlank()) {
            return "";
        }

        if (entityPackage.endsWith(".entity")) {

            return entityPackage.substring(
                    0,
                    entityPackage.length() - ".entity".length()
            );
        }

        return entityPackage;
    }

    private String subPackage(
            String rootPackage,
            String child
    ) {

        if (rootPackage == null || rootPackage.isBlank()) {
            return child;
        }

        return rootPackage + "." + child;
    }

    private String packageDeclaration(String packageName) {

        return packageName == null || packageName.isBlank()
                ? ""
                : "package " + packageName + ";\n\n";
    }

    private PsiDirectory createPackage(
            PsiDirectory sourceDirectory,
            String packageName
    ) {

        if (packageName == null || packageName.isBlank()) {
            return sourceDirectory;
        }

        PsiDirectory current = sourceDirectory;

        for (String part : packageName.split("\\.")) {

            if (part.isBlank()) {
                continue;
            }

            PsiDirectory child =
                    current.findSubdirectory(part);

            current = child != null
                    ? child
                    : current.createSubdirectory(part);
        }

        return current;
    }

    private void createClass(
            Project project,
            PsiDirectory sourceDirectory,
            String packageName,
            String className,
            String code
    ) {

        PsiDirectory directory =
                createPackage(sourceDirectory, packageName);

        if (directory.findFile(className + ".java") != null) {
            return;
        }

        PsiFile file =
                PsiFileFactory.getInstance(project)
                        .createFileFromText(
                                className + ".java",
                                JavaFileType.INSTANCE,
                                code
                        );

        directory.add(file);
    }

    // -------------------------------------------------------------------------
    // Entity Metadata
    // -------------------------------------------------------------------------

    private String getIdType(PsiClass entity) {

        for (PsiField field : entity.getAllFields()) {

            if (hasAnnotation(field, "jakarta.persistence.Id")
                    || hasAnnotation(field, "javax.persistence.Id")
                    || hasAnnotation(field, "jakarta.persistence.EmbeddedId")
                    || hasAnnotation(field, "javax.persistence.EmbeddedId")) {

                return field.getType().getCanonicalText();
            }
        }

        return null;
    }

    private boolean isIdField(PsiField field) {

        return hasAnnotation(field, "jakarta.persistence.Id")
                || hasAnnotation(field, "javax.persistence.Id")
                || hasAnnotation(field, "jakarta.persistence.EmbeddedId")
                || hasAnnotation(field, "javax.persistence.EmbeddedId");
    }

    private boolean hasAnnotation(
            PsiModifierListOwner owner,
            String qualifiedName
    ) {

        PsiModifierList modifierList =
                owner.getModifierList();

        return modifierList != null
                && modifierList.findAnnotation(qualifiedName) != null;
    }

    private List<PsiField> getEntityFields(PsiClass entity) {

        List<PsiField> fields = new ArrayList<>();

        for (PsiField field : entity.getAllFields()) {

            if (field instanceof PsiEnumConstant) {
                continue;
            }

            PsiModifierList modifiers =
                    field.getModifierList();

            if (modifiers == null) {
                continue;
            }

            if (modifiers.hasModifierProperty(PsiModifier.STATIC)
                    || modifiers.hasModifierProperty(PsiModifier.TRANSIENT)) {
                continue;
            }

            fields.add(field);
        }

        return fields;
    }

    // -------------------------------------------------------------------------
    // Repository
    // -------------------------------------------------------------------------

    private String repositoryCode(
            String pkg,
            String entityQualifiedName,
            String entityName,
            String idType
    ) {

        return packageDeclaration(pkg) +
                """
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;
                import %s;

                @Repository
                public interface %sRepository extends JpaRepository<%s, %s> {
                }
                """.formatted(
                        entityQualifiedName,
                        entityName,
                        entityName,
                        idType
                );
    }

    // -------------------------------------------------------------------------
    // Service Interface
    // -------------------------------------------------------------------------

    private String serviceCode(
            String pkg,
            String entityQualifiedName,
            String entityName,
            String idType
    ) {

        return packageDeclaration(pkg) +
                """
                import java.util.List;
                import %s;

                public interface %sService {

                    %s create(%s entity);

                    %s getById(%s id);

                    List<%s> getAll();

                    %s update(%s id, %s entity);

                    void delete(%s id);
                }
                """.formatted(
                        entityQualifiedName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        idType
                );
    }

    // -------------------------------------------------------------------------
    // Service Implementation
    // -------------------------------------------------------------------------

    private String serviceImplCode(
            String pkg,
            String entityQualifiedName,
            String entityName,
            String repositoryPackage,
            String servicePackage,
            String exceptionPackage,
            String idType
    ) {

        String repositoryQualifiedName =
                subPackage(repositoryPackage, entityName + "Repository");

        String serviceQualifiedName =
                subPackage(servicePackage, entityName + "Service");

        String exceptionQualifiedName =
                subPackage(exceptionPackage, entityName + "NotFoundException");

        return packageDeclaration(pkg) +
                """
                import java.util.List;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                import %s;
                import %s;
                import %s;
                import %s;

                @Service
                @Transactional
                public class %sServiceImpl implements %sService {

                    private final %sRepository repository;

                    public %sServiceImpl(%sRepository repository) {
                        this.repository = repository;
                    }

                    @Override
                    public %s create(%s entity) {
                        return repository.save(entity);
                    }

                    @Override
                    @Transactional(readOnly = true)
                    public %s getById(%s id) {
                        return repository.findById(id)
                                .orElseThrow(() -> new %sNotFoundException(id));
                    }

                    @Override
                    @Transactional(readOnly = true)
                    public List<%s> getAll() {
                        return repository.findAll();
                    }

                    @Override
                    public %s update(%s id, %s entity) {
                        %s existing = getById(id);
                        // Copying fields into the existing entity is application-specific.
                        // This basic generator returns the supplied entity after saving it.
                        return repository.save(entity);
                    }

                    @Override
                    public void delete(%s id) {
                        %s existing = getById(id);
                        repository.delete(existing);
                    }
                }
                """.formatted(
                        entityQualifiedName,
                        repositoryQualifiedName,
                        serviceQualifiedName,
                        exceptionQualifiedName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        entityName,
                        idType,
                        entityName
                );
    }

    // -------------------------------------------------------------------------
    // Controller
    // -------------------------------------------------------------------------

    private String controllerCode(
            String pkg,
            String entityQualifiedName,
            String entityName,
            String servicePackage,
            String idType
    ) {

        String serviceQualifiedName =
                subPackage(servicePackage, entityName + "Service");

        return packageDeclaration(pkg) +
                """
                import java.util.List;

                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                import %s;
                import %s;

                @RestController
                @RequestMapping("/api/%s")
                public class %sController {

                    private final %sService service;

                    public %sController(%sService service) {
                        this.service = service;
                    }

                    @PostMapping
                    public ResponseEntity<%s> create(@RequestBody %s entity) {
                        return ResponseEntity.ok(service.create(entity));
                    }

                    @GetMapping("/{id}")
                    public ResponseEntity<%s> getById(@PathVariable %s id) {
                        return ResponseEntity.ok(service.getById(id));
                    }

                    @GetMapping
                    public ResponseEntity<List<%s>> getAll() {
                        return ResponseEntity.ok(service.getAll());
                    }

                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(
                            @PathVariable %s id,
                            @RequestBody %s entity
                    ) {
                        return ResponseEntity.ok(service.update(id, entity));
                    }

                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable %s id) {
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                }
                """.formatted(
                        entityQualifiedName,
                        serviceQualifiedName,
                        entityName.toLowerCase(),
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        entityName,
                        idType,
                        entityName,
                        idType
                );
    }

    // -------------------------------------------------------------------------
    // DTO
    // -------------------------------------------------------------------------

    private String dtoCode(
            String pkg,
            String dtoName,
            PsiClass entity,
            boolean excludeId
    ) {

        StringBuilder code =
                new StringBuilder(packageDeclaration(pkg));

        code.append("public class ")
                .append(dtoName)
                .append(" {\n\n");

        List<PsiField> fields =
                getEntityFields(entity);

        for (PsiField field : fields) {

            if (excludeId && isIdField(field)) {
                continue;
            }

            code.append("    private ")
                    .append(field.getType().getCanonicalText())
                    .append(" ")
                    .append(field.getName())
                    .append(";\n");
        }

        code.append("\n");

        for (PsiField field : fields) {

            if (excludeId && isIdField(field)) {
                continue;
            }

            String type =
                    field.getType().getCanonicalText();

            String name = field.getName();

            String capitalized =
                    capitalize(name);

            code.append("    public ")
                    .append(type)
                    .append(" get")
                    .append(capitalized)
                    .append("() {\n")
                    .append("        return ")
                    .append(name)
                    .append(";\n")
                    .append("    }\n\n");

            code.append("    public void set")
                    .append(capitalized)
                    .append("(")
                    .append(type)
                    .append(" ")
                    .append(name)
                    .append(") {\n")
                    .append("        this.")
                    .append(name)
                    .append(" = ")
                    .append(name)
                    .append(";\n")
                    .append("    }\n\n");
        }

        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private String mapperCode(
            String pkg,
            String entityQualifiedName,
            String requestPackage,
            String responsePackage,
            String entityName,
            PsiClass entity
    ) {

        String requestQualifiedName =
                subPackage(requestPackage, entityName + "Request");

        String responseQualifiedName =
                subPackage(responsePackage, entityName + "Response");

        StringBuilder code =
                new StringBuilder(packageDeclaration(pkg));

        code.append("import ")
                .append(entityQualifiedName)
                .append(";\n");

        code.append("import ")
                .append(requestQualifiedName)
                .append(";\n");

        code.append("import ")
                .append(responseQualifiedName)
                .append(";\n");

        code.append("import org.springframework.stereotype.Component;\n\n");

        code.append("@Component\n");

        code.append("public class ")
                .append(entityName)
                .append("Mapper {\n\n");

        code.append("    public ")
                .append(entityName)
                .append(" toEntity(")
                .append(entityName)
                .append("Request request) {\n");

        code.append("        if (request == null) return null;\n");

        code.append("        ")
                .append(entityName)
                .append(" entity = new ")
                .append(entityName)
                .append("();\n");

        for (PsiField field : getEntityFields(entity)) {

            if (isIdField(field)) {
                continue;
            }

            String name = field.getName();

            code.append("        entity.set")
                    .append(capitalize(name))
                    .append("(request.get")
                    .append(capitalize(name))
                    .append("());\n");
        }

        code.append("        return entity;\n");
        code.append("    }\n\n");

        code.append("    public ")
                .append(entityName)
                .append("Response toResponse(")
                .append(entityName)
                .append(" entity) {\n");

        code.append("        if (entity == null) return null;\n");

        code.append("        ")
                .append(entityName)
                .append("Response response = new ")
                .append(entityName)
                .append("Response();\n");

        for (PsiField field : getEntityFields(entity)) {

            String name = field.getName();

            code.append("        response.set")
                    .append(capitalize(name))
                    .append("(entity.get")
                    .append(capitalize(name))
                    .append("());\n");
        }

        code.append("        return response;\n");
        code.append("    }\n");

        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Not Found Exception
    // -------------------------------------------------------------------------

    private String exceptionCode(
            String pkg,
            String entityName
    ) {

        return packageDeclaration(pkg) +
                """
                public class %sNotFoundException extends RuntimeException {

                    public %sNotFoundException(Object id) {
                        super("%s not found with id: " + id);
                    }
                }
                """.formatted(
                        entityName,
                        entityName,
                        entityName
                );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String capitalize(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        return Character.toUpperCase(value.charAt(0))
                + value.substring(1);
    }
}