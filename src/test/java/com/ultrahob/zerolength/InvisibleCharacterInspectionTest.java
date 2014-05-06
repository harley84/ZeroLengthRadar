package com.ultrahob.zerolength;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class InvisibleCharacterInspectionTest
        extends LightCodeInsightFixtureTestCase {
    @Override
    protected String getTestDataPath() {
        String jarPathForClass = PathManager.getJarPathForClass(InvisibleCharacterInspectionTest.class);
        File sourceRoot = new File(jarPathForClass, "testData");
        return sourceRoot.getPath();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(InvisibleCharacterInspection.class);
    }

    private static class MyDescriptor extends DefaultLightProjectDescriptor {
        @Override
        public Sdk getSdk() {
            String jarPathForClass = PathManager.getJarPathForClass(InvisibleCharacterInspectionTest.class);
            File sourceRoot = new File(jarPathForClass, "mockJDK-1.7");

            return JavaSdk.getInstance().createJdk("1.7", sourceRoot.getPath(), false);
        }
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return new MyDescriptor();
    }

    public void testHighlight() throws Exception {
        String testName = getTestName(false) + ".java";
        myFixture.configureByFile(testName);
        myFixture.testHighlighting(true, false, false, testName);
    }
}
