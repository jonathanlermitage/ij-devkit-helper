//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package lermitage.intellij.devkithelper;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

// IMPORTANT duplicated from com.intellij.openapi.project.IntelliJProjectUtil
@Internal
public final class MyIntelliJProjectUtil {
    private static final Key<Boolean> IDEA_PROJECT = Key.create("idea.internal.inspections.enabled");
    private static final List<String> IDEA_PROJECT_MARKER_MODULE_NAMES = List.of("intellij.idea.community.main", "intellij.platform.commercial", "intellij.android.studio.integration");

    public MyIntelliJProjectUtil() {
    }

    public static boolean isIntelliJPlatformProject(@Nullable Project project) {
        if (project == null) {
            return false;
        } else {
            Boolean flag = (Boolean) project.getUserData(IDEA_PROJECT);
            if (flag == null) {
                flag = false;
                ModuleManager moduleManager = ModuleManager.getInstance(project);

                for (String moduleName : IDEA_PROJECT_MARKER_MODULE_NAMES) {
                    if (moduleManager.findModuleByName(moduleName) != null) {
                        flag = true;
                        break;
                    }
                }

                project.putUserData(IDEA_PROJECT, flag);
            }

            return flag;
        }
    }

    @TestOnly
    public static void markAsIntelliJPlatformProject(@NotNull Project project, Boolean value) {
        project.putUserData(IDEA_PROJECT, value);
    }
}
