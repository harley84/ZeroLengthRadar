package com.ultrahob.zerolength;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Victor Rosenberg, 17.04.2014 18:10
 */
public class InvisibleCharacterInspection extends LocalInspectionTool {

    private final InvisibleCharacterDescriptor zeroWidthSpaceDescriptor = new InvisibleCharacterDescriptor("200B", "Zero width space", "reportZeroWidthSpace");
    private final InvisibleCharacterDescriptor zeroWidthNonJoinerDescriptor = new InvisibleCharacterDescriptor("200C", "Zero width non-joiner", "reportZeroWidthNonJoiner");
    private final InvisibleCharacterDescriptor zeroWidthJoinerDescriptor = new InvisibleCharacterDescriptor("200D", "Zero width joiner", "reportZeroWidthJoiner");
    private final InvisibleCharacterDescriptor zeroWidthNoBreakDescriptor = new InvisibleCharacterDescriptor("FEFF", "Zero width no-break space", "reportZeroWidthNoBreak");

    public boolean reportZeroWidthSpace = true;
    public boolean reportZeroWidthNonJoiner = true;
    public boolean reportZeroWidthJoiner = true;
    public boolean reportZeroWidthNoBreak = true;

    private List<InvisibleCharacterDescriptor> getDescriptors() {
        return Arrays.asList(zeroWidthSpaceDescriptor, zeroWidthNonJoinerDescriptor, zeroWidthJoinerDescriptor, zeroWidthNoBreakDescriptor);
    }

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "Zero Width Unicode Character";
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {

        if (file.getFileType().isBinary() || !file.isValid()) {
            return null;
        }

        List<ProblemDescriptor> problems = null;

        for (InvisibleCharacterDescriptor descriptor : getDescriptors()) {
            if (descriptor.isEnabled()) {

                Matcher matcher = descriptor.getPattern().matcher(file.getText());
                while (matcher.find()) {
                    if (problems == null) {
                        problems = new ArrayList<ProblemDescriptor>();
                    }
                    int indexOf = matcher.start();

                    PsiElement badElement = file.findElementAt(indexOf);
                    if (badElement != null) {
                        String replacementText = buildReplacementText(descriptor, badElement);
                        problems.add(manager.createProblemDescriptor(badElement, descriptor.description + " character found, resulting in: " + replacementText,
                                (LocalQuickFix) null, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
                    }
                }
            }
        }
        return problems == null ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    private String buildReplacementText(InvisibleCharacterDescriptor descriptor, PsiElement badElement) {
        StringBuffer sb = new StringBuffer();
        Matcher m = descriptor.getPattern().matcher(badElement.getText());
        while (m.find()) {
            m.appendReplacement(sb, "\\u" + descriptor.getForbiddenCharacter());
        }
        m.appendTail(sb);
//        return "lalala";//sb.toString().trim();
        return sb.toString().trim().replace("\"", "\\\"");
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        for (InvisibleCharacterDescriptor descriptor : getDescriptors()) {

            optionsPanel.addCheckbox(
                    String.format("Report %s (%s)", descriptor.getForbiddenCharacter(), descriptor.getDescription()),
                    descriptor.getPropertyName());
        }

        return optionsPanel;
    }

    private class InvisibleCharacterDescriptor {
        private final String forbiddenCharacter;
        private final String description;
        private final String propertyName;
        private final Pattern pattern;

        public InvisibleCharacterDescriptor(String forbiddenCharacter, String description, String propertyName) {

            this.forbiddenCharacter = forbiddenCharacter;
            this.description = description;
            this.propertyName = propertyName;
            this.pattern = Pattern.compile("\\u" + forbiddenCharacter);
        }

        public String getForbiddenCharacter() {
            return forbiddenCharacter;
        }

        public String getDescription() {
            return description;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public boolean isEnabled() {

            if ("reportZeroWidthJoiner".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportZeroWidthJoiner;
            } else if ("reportZeroWidthNonJoiner".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportZeroWidthNonJoiner;
            } else if ("reportZeroWidthSpace".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportZeroWidthSpace;
            } else if ("reportZeroWidthNoBreak".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportZeroWidthNoBreak;
            } else {
                return false;
            }
//            try {
//                return InvisibleCharacterInspection.class.getField(getPropertyName()).getBoolean(InvisibleCharacterInspection.this);
//            } catch (Exception e) {
//                return false;
//            }
        }
    }
}
