package com.skazizul.generator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class GenerateStructureAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private PsiClass getSelectedClass(AnActionEvent event) {
        PsiElement context = event.getData(CommonDataKeys.PSI_ELEMENT);
        if (context instanceof PsiClass psiClass) return psiClass;
        if (context != null) {
            PsiClass parent = com.intellij.psi.util.PsiTreeUtil.getParentOfType(context, PsiClass.class);
            if (parent != null) return parent;
        }
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);

        if (!(file instanceof PsiJavaFile javaFile)) {
            return null;
        }

        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            return null;
        }

        int offset = event.getData(CommonDataKeys.CARET) != null
                ? event.getData(CommonDataKeys.CARET).getOffset() : -1;
        if (offset >= 0) {
            PsiElement atCaret = javaFile.findElementAt(offset);
            PsiClass atCaretClass = atCaret == null ? null
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(atCaret, PsiClass.class);
            if (atCaretClass != null) return atCaretClass;
        }
        return classes[0];
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(getSelectedClass(event) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {

        Project project = event.getProject();

        if (project == null) {
            return;
        }

        PsiClass selectedClass = getSelectedClass(event);

        if (selectedClass == null) {
            Messages.showWarningDialog(
                    project,
                    "No Java class found.",
                    "Spring Boot Generator"
            );
            return;
        }

        PsiFile containingFile = selectedClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile javaFile)) return;
        // Put generated packages under the Java source root, alongside entity/.
        var sourceRoot = ProjectRootManager.getInstance(project).getFileIndex()
                .getSourceRootForFile(javaFile.getVirtualFile());
        if (sourceRoot == null) {
            Messages.showWarningDialog(project,
                    "The selected Java file is not inside a configured source root (for example src/main/java).",
                    "Spring Boot Generator");
            return;
        }
        PsiDirectory base = com.intellij.psi.PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (base == null) return;
        String entity = selectedClass.getName();
        String lower = entity.substring(0, 1).toLowerCase(Locale.ROOT) + entity.substring(1);

        WriteCommandAction.runWriteCommandAction(project, "Generate Spring Boot Structure", null, () -> {
            try {
                createJava(project, base, "controller", entity + "Controller",
                        "import org.springframework.web.bind.annotation.*;\n\n@RestController\n@RequestMapping(\"/" + lower + "s\")\npublic class " + entity + "Controller {\n}\n");
                createJava(project, base, "service", entity + "Service",
                        "import org.springframework.stereotype.Service;\n\n@Service\npublic class " + entity + "Service {\n}\n");
                createJava(project, base, "repository", entity + "Repository",
                        "import org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface " + entity + "Repository extends JpaRepository<" + entity + ", Long> {\n}\n");
                createJava(project, base, "dto", entity + "Dto",
                        "public class " + entity + "Dto {\n}\n");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        Messages.showInfoMessage(project, "Generated Controller, Service, Repository and DTO for " + entity,
                "Spring Boot Generator");
    }

    private void createJava(Project project, PsiDirectory base, String subpackage,
                            String className, String body) {
        PsiDirectory target = base.findSubdirectory(subpackage);
        if (target == null) target = base.createSubdirectory(subpackage);
        if (target.findFile(className + ".java") != null) return;
        String packageName = subpackage;
        String source = (packageName.isEmpty() ? "" : "package " + packageName + ";\n\n") + body;
        PsiFile generated = PsiFileFactory.getInstance(project)
                .createFileFromText(className + ".java", com.intellij.lang.java.JavaLanguage.INSTANCE, source);
        target.add(generated);
    }
}
