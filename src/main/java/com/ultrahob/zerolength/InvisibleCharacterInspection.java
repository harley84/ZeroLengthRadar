package com.ultrahob.zerolength;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final InvisibleCharacterDescriptor lineSeparator = new InvisibleCharacterDescriptor("2028", "", "Line separator", "reportLineSeparator");
    private final InvisibleCharacterDescriptor startOfHeading = new InvisibleCharacterDescriptor("0001", "", "Start of Heading", "reportStartOfHeading");

    public boolean reportZeroWidthSpace = true;
    public boolean reportZeroWidthNonJoiner = true;
    public boolean reportZeroWidthJoiner = true;
    public boolean reportLeftToRightMark = true;
    public boolean reportRightToLeftMark = true;
    public boolean reportZeroWidthNoBreak = true;
    public boolean reportEndOfText = true;
    public boolean reportNoBreakSpace = true;
    public boolean reportLineSeparator = true;
    public boolean startOfHeading = true;

    private List<InvisibleCharacterDescriptor> getDescriptors() {
        return Arrays.asList(
                zeroWidthSpaceDescriptor,
                zeroWidthNonJoinerDescriptor,
                zeroWidthJoinerDescriptor,
                zeroWidthNoBreakDescriptor,
                leftToRightMarkDescriptor,
                rightToLeftMarkDescriptor,
                endOfTextDescriptor,
                noBreakSpaceDescriptor,
                lineSeparator,
                startOfHeading
        );
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {

        if (file.getFileType().isBinary() || !file.isValid()) {
            return null;
        }

        List<ProblemDescriptor> problems = null;
        Map<PsiElement, HashSet<InvisibleCharacterDescriptor>> badElements = new HashMap<>();

        // vsch: accumulate characters per element and produce a single inspection for all found characters.
        // Otherwise creates huge tooltips one for every occurrence and every character
        for (InvisibleCharacterDescriptor descriptor : getDescriptors()) {
            if (descriptor.isEnabled()) {
                Matcher matcher = descriptor.getPattern().matcher(file.getText());
                while (matcher.find()) {
                    PsiElement badElement = file.findElementAt(matcher.start());
                    if (badElement != null) {
                        // add whole file so we can fix it
                        badElements
                            .computeIfAbsent(file, k-> new HashSet<>(1))
                            .add(descriptor);

                        badElements
                            .computeIfAbsent(badElement, k-> new HashSet<>(1))
                            .add(descriptor);
                    }
                }
            }
        }

        if (!badElements.isEmpty()) {
            problems = new ArrayList<>();

            for (Map.Entry<PsiElement, HashSet<InvisibleCharacterDescriptor>> psiElementHashSetEntry : badElements.entrySet()) {

                String description = psiElementHashSetEntry.getValue().stream()
                        .map(InvisibleCharacterDescriptor::getDescription)
                        .collect(Collectors.joining(", "));

                InvisibleCharacterLocalQuickFix quickFix = new InvisibleCharacterLocalQuickFix(psiElementHashSetEntry.getKey(), psiElementHashSetEntry.getValue());

                if (psiElementHashSetEntry.getKey() instanceof PsiFile) {
                    problems.add(manager.createProblemDescriptor(psiElementHashSetEntry.getKey(), "1. " + description + "(s) found in file",
                            quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));

                    if (psiElementHashSetEntry.getValue().size() > 1) {
                        // add one per descriptor to file fixes
                        int index = 2;
                        for (InvisibleCharacterDescriptor descriptor : psiElementHashSetEntry.getValue()) {
                            ArrayList<InvisibleCharacterDescriptor> singleDescriptor = new ArrayList<>(1);
                            singleDescriptor.add(descriptor);

                            quickFix = new InvisibleCharacterLocalQuickFix(psiElementHashSetEntry.getKey(), singleDescriptor);
                            problems.add(manager.createProblemDescriptor(psiElementHashSetEntry.getKey(), (index++) + ". " + descriptor.description + "(s) found in file",
                                    quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
                        }
                    }
                } else {
                    String replacementText = quickFix.uncleanText(psiElementHashSetEntry.getKey().getText());
                    problems.add(manager.createProblemDescriptor(psiElementHashSetEntry.getKey(), description + "(s) found, resulting in: " + replacementText,
                            quickFix, ProblemHighlightType.GENERIC_ERROR, isOnTheFly));
                }
            }
        }

        return problems == null ? null : problems.toArray(new ProblemDescriptor[0]);
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
            this.pattern = Pattern.compile(String.format("\\u%s", forbiddenCharacter));
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
            } else if ("reportLineSeparator".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportLineSeparator;
            }  else if ("startOfHeading".equals(propertyName)) {
                return InvisibleCharacterInspection.this.reportstartOfHeading;
            } else {
                // vsch: should really assert fail here because we forgot to add a case
                return false;
            }
        }
    }
}
