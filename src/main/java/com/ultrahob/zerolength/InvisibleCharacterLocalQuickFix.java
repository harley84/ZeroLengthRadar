package com.ultrahob.zerolength;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class InvisibleCharacterLocalQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final @NotNull Collection<InvisibleCharacterInspection.InvisibleCharacterDescriptor> removeDescriptors;

    public InvisibleCharacterLocalQuickFix(@Nullable PsiElement element, @NotNull Collection<InvisibleCharacterInspection.InvisibleCharacterDescriptor> removeDescriptors) {
        super(element);
        this.removeDescriptors = removeDescriptors;
    }

    public InvisibleCharacterLocalQuickFix(@Nullable PsiElement startElement, @Nullable PsiElement endElement, @NotNull Collection<InvisibleCharacterInspection.InvisibleCharacterDescriptor> removeDescriptors) {
        super(startElement, endElement);
        this.removeDescriptors = removeDescriptors;
    }

    protected String unicodeCharacterList() {
        String list = "";

        for (InvisibleCharacterInspection.InvisibleCharacterDescriptor descriptor : removeDescriptors) {
            if (!list.isEmpty()) list += ", ";
            list += descriptor.getForbiddenCharacterString();
        }
        return uncleanText(list);
    }

    protected String cleanText(@NotNull String text) {
        String cleanedText = text;

        for (InvisibleCharacterInspection.InvisibleCharacterDescriptor descriptor : removeDescriptors) {
            cleanedText = cleanedText.replace(descriptor.getForbiddenCharacterString(), descriptor.getForbiddenCharacterReplacement());
        }
        return cleanedText;
    }

    protected String uncleanText(@NotNull String text) {
        String cleanedText = text;

        for (InvisibleCharacterInspection.InvisibleCharacterDescriptor descriptor : removeDescriptors) {
            cleanedText = cleanedText.replace(descriptor.getForbiddenCharacterString(), "\\u" + String.format("%04X", descriptor.getForbiddenCharacterString().codePointAt(0)));
        }
        return cleanedText;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @Nullable("is null when called from inspection") Editor editor, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
        Document document = editor != null ? editor.getDocument() : null;
        if (document != null) {
            PsiElement element = startElement;
            do {
                int startIndex = element.getTextRange().getStartOffset();
                int endIndex = element.getTextRange().getEndOffset();
                String replacedText = cleanText(document.getText(element.getTextRange()));
                document.replaceString(startIndex, endIndex, replacedText);
            } while (element != endElement && ((element = element.getNextSibling()) != null && element != endElement));
        }
    }

    @NotNull
    @Override
    public String getText() {
        return "Remove: " + unicodeCharacterList();
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Zero width character";
    }
}
