// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumMinificationKotlinTest extends KotlinTestBase {
  private static final String FOLDER = "minify_enum";
  private static final String MAIN_CLASS_NAME = "minify_enum.MainKt";
  private static final String ENUM_CLASS_NAME = "minify_enum.MinifyEnum";

  private final TestParameters parameters;
  private final boolean minify;

  @Parameterized.Parameters(name = "{0}, target: {1}, kotlinc: {2}, minify: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers(),
        BooleanUtils.values());
  }

  public EnumMinificationKotlinTest(
      TestParameters parameters,
      KotlinTargetVersion targetVersion,
      KotlinCompiler kotlinc,
      boolean minify) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
    this.minify = minify;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void b121221542() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepClassRulesWithAllowObfuscation(ENUM_CLASS_NAME)
            .addDontWarnJetBrainsAnnotations()
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .inspector();
    ClassSubject enumClass = inspector.clazz(ENUM_CLASS_NAME);
    assertThat(enumClass, isPresent());
    assertEquals(minify, enumClass.isRenamed());
    assertThat(enumClass.clinit(), isAbsent());
  }
}
