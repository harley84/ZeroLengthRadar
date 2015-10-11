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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Victor Rosenberg, 17.04.2014 18:10
 * @author Vladimir Schneider, 08.10.2015 15:37, add left to right and right to left marks, add quick fix, add combine tooltips, one per element with all notifications
 */
public class InvisibleCharacterInspection extends LocalInspectionTool {
    private final InvisibleCharacterDescriptor zeroWidthSpaceDescriptor = new InvisibleCharacterDescriptor("200B", "", "Zero width space", "reportZeroWidthSpace");
    private final InvisibleCharacterDescriptor zeroWidthNonJoinerDescriptor = new InvisibleCharacterDescriptor("200C", "", "Zero width non-joiner", "reportZeroWidthNonJoiner");
    private final InvisibleCharacterDescriptor zeroWidthJoinerDescriptor = new InvisibleCharacterDescriptor("200D", "", "Zero width joiner", "reportZeroWidthJoiner");
    private final InvisibleCharacterDescriptor leftToRightMarkDescriptor = new InvisibleCharacterDescriptor("200E", "", "Left to right mark", "reportLeftToRightMark");
    private final InvisibleCharacterDescriptor rightToLeftMarkDescriptor = new InvisibleCharacterDescriptor("200F", "", "Right to left mark", "reportRightToLeftMark");
    private final InvisibleCharacterDescriptor zeroWidthNoBreakDescriptor = new InvisibleCharacterDescriptor("FEFF", "", "Zero width no-break space", "reportZeroWidthNoBreak");
    private final InvisibleCharacterDescriptor endOfTextDescriptor = new InvisibleCharacterDescriptor("0003", "", "End of Text character", "reportEndOfText");
    private final InvisibleCharacterDescriptor noBreakSpaceDescriptor = new InvisibleCharacterDescriptor("00A0", " ", "No-Break space character", "reportNoBreakSpace");

    public boolean reportZeroWidthSpace = true;
    public boolean reportZeroWidthNonJoiner = true;
    public boolean reportZeroWidthJoiner = true;
    public boolean reportLeftToRightMark = true;
    public boolean reportRightToLeftMark = true;
    public boolean reportZeroWidthNoBreak = true;
    public boolean reportEndOfText = true;
    public boolean reportNoBreakSpace = true;

    private List<InvisibleCharacterDescriptor> getDescriptors() {
        return Arrays.asList(
                zeroWidthSpaceDescriptor,
                zeroWidthNonJoinerDescriptor,
                zeroWidthJoinerDescriptor,
                zeroWidthNoBreakDescriptor,
                leftToRightMarkDescriptor,
                rightToLeftMarkDescriptor,
                endOfTextDescriptor,
                noBreakSpaceDescriptor
        );
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
        HashMap<PsiElement, HashSet<InvisibleCharacterDescriptor>> badElements = null;

        // vsch: accumulate characters per element and produce a single inspection for all found characters. Otherwise creates huge tooltips one for every occurence and every character
        for (InvisibleCharacterDescriptor descriptor : getDescriptors()) {
            if (descriptor.isEnabled()) {
                Matcher matcher = descriptor.getPattern().matcher(file.getText());
                while (matcher.find()) {
                    PsiElement badElement = file.findElementAt(matcher.start());
                    if (badElement != null) {
                        if (badElements == null) {
                            badElements = new HashMap<PsiElement, HashSet<InvisibleCharacterDescriptor>>();
                        }

                        // add whole file so we can fix it
                        if (!badElements.containsKey(file)) {
                            badElements.put(file, new HashSet<InvisibleCharacterDescriptor>(1));
                        }
                        badElements.get(file).add(descriptor);

                        if (!badElements.containsKey(badElement)) {
                            badElements.put(badElement, new HashSet<InvisibleCharacterDescriptor>(1));
                        } else {
                            if (badElements.get(badElement).contains(descriptor)) continue;
                        }

                        badElements.get(badElement).add(descriptor);
                    }
                }
            }
        }

        if (badElements != null && badElements.size() > 0) {
            problems = new ArrayList<ProblemDescriptor>();
            for (PsiElement badElement : badElements.keySet()) {
                String description = "";
                for (InvisibleCharacterDescriptor descriptor : badElements.get(badElement)) {
                    if (!description.isEmpty()) description += ", ";
                    description += descriptor.description;
                }

                InvisibleCharacterLocalQuickFix quickFix = new InvisibleCharacterLocalQuickFix(badElement, badElements.get(badElement));

                if (badElement instanceof PsiFile) {
                    problems.add(manager.createProblemDescriptor(badElement, "1. " + description + "(s) found in file",
                            (LocalQuickFix) quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));

                    if (badElements.get(badElement).size() > 1) {
                        // add one per descriptor to file fixes
                        int index = 2;
                        for (InvisibleCharacterDescriptor descriptor : badElements.get(badElement)) {
                            ArrayList<InvisibleCharacterDescriptor> singleDescriptor = new ArrayList<InvisibleCharacterDescriptor>(1);
                            singleDescriptor.add(descriptor);

                            quickFix = new InvisibleCharacterLocalQuickFix(badElement, singleDescriptor);
                            problems.add(manager.createProblemDescriptor(badElement, (index++) + ". " + descriptor.description + "(s) found in file",
                                    (LocalQuickFix) quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
                        }
                    }
                } else {
                    String replacementText = quickFix.uncleanText(badElement.getText());
                    problems.add(manager.createProblemDescriptor(badElement, description + "(s) found, resulting in: " + replacementText,
                            (LocalQuickFix) quickFix, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
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

    class InvisibleCharacterDescriptor {
        private final String forbiddenCharacter;
        private final String replacementCharacter;
        private final String description;
        private final String propertyName;
        private final Pattern pattern;

        public InvisibleCharacterDescriptor(String forbiddenCharacter, String replacementCharacter, String description, String propertyName) {
            this.forbiddenCharacter = forbiddenCharacter;
            this.replacementCharacter = replacementCharacter;
            this.description = description;
            this.propertyName = propertyName;
            this.pattern = Pattern.compile("\\u" + forbiddenCharacter);
        }

        public String getForbiddenCharacter() {
            return forbiddenCharacter;
        }

        public String getForbiddenCharacterString() {
            int codePoint = Integer.parseInt(forbiddenCharacter, 16);
            return new String(Character.toChars(codePoint));
        }

        public String getForbiddenCharacterReplacement() {
            return replacementCharacter;
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
            } else if ("reportEndOfText".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportEndOfText;
            } else if ("reportLeftToRightMark".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportLeftToRightMark;
            } else if ("reportRightToLeftMark".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportRightToLeftMark;
            } else if ("reportNoBreakSpace".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportNoBreakSpace;
            } else {
                // vsch: should really assert fail here because we forgot to add a case
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
