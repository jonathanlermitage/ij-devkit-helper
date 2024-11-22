package lermitage.intellij.devkithelper;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiUsageProcessor;
import org.jetbrains.idea.devkit.inspections.missingApi.SinceUntilRange;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

// IMPORTANT adapted from org.jetbrains.idea.devkit.inspections.missingApi.MissingRecentApiInspection
public class MyMissingRecentApiInspection extends LocalInspectionTool {

    private static final @NonNls Logger LOGGER = Logger.getInstance(MyMissingRecentApiInspection.class);

    @Override
    public @NotNull PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
        var project = holder.getProject();
        var virtualFile = holder.getFile().getVirtualFile();
        if (MyIntelliJProjectUtil.isIntelliJPlatformProject(project) ||
            (virtualFile != null && TestSourcesFilter.isTestSources(virtualFile, project))) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Module module = ModuleUtil.findModuleForPsiElement(holder.getFile());
        if (module == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        List<SinceUntilRange> targetedSinceUntilRanges = getTargetedSinceUntilRanges(module);
        if (targetedSinceUntilRanges.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return ApiUsageUastVisitor.createPsiElementVisitor(
            new MissingRecentApiUsageProcessor(holder, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, targetedSinceUntilRanges)
        );
    }

    private Properties loadProperties(String filePath) {
        Properties properties = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            properties.load(fileInputStream);
        } catch (IOException e) {
            LOGGER.warn("Failed to load gradle.properties from '" + filePath + "'", e);
            return null;
        }
        return properties;
    }

    private List<SinceUntilRange> getTargetedSinceUntilRanges(Module module) {
        String gradlePropertiesPath = module.getProject().getBasePath() + "/gradle.properties";
        Properties gradlePropertiesFile = loadProperties(gradlePropertiesPath);
        if (gradlePropertiesFile == null) {
            LOGGER.warn("Failed to load " + gradlePropertiesPath);
            return List.of(new SinceUntilRange(null, null));
        }
        String sinceBuild = gradlePropertiesFile.getProperty("pluginSinceBuild", null);
        String untilBuild = gradlePropertiesFile.getProperty("pluginUntilBuild", null);

        if (sinceBuild == null && untilBuild == null) {
            return Collections.emptyList();
        }
        return List.of(new SinceUntilRange(BuildNumber.fromString(sinceBuild), BuildNumber.fromString(untilBuild)));
    }
}
