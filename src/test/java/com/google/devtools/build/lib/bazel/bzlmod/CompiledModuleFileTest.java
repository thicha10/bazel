package com.google.devtools.build.lib.bazel.bzlmod;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.bazel.bzlmod.CompiledModuleFile.ModuleImportStatement;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.StarlarkFile;
import net.starlark.java.syntax.SyntaxError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompiledModuleFileTest {

  private static ImmutableList<ModuleImportStatement> checkSyntax(String str) throws Exception {
    return CompiledModuleFile.checkModuleFileSyntax(
        StarlarkFile.parse(ParserInput.fromString(str, "test file")));
  }

  @Test
  public void checkSyntax_good() throws Exception {
    String program = """
        abc()
        module_import("hullo")
        foo = bar
        """;
    assertThat(checkSyntax(program))
        .containsExactly(
            new ModuleImportStatement("hullo", Location.fromFileLineColumn("test file", 2, 1)));
  }

  @Test
  public void checkSyntax_good_multiple() throws Exception {
    String program =
        """
        abc()
        module_import("hullo")
        foo = bar
        module_import('world')
        """;
    assertThat(checkSyntax(program))
        .containsExactly(
            new ModuleImportStatement("hullo", Location.fromFileLineColumn("test file", 2, 1)),
            new ModuleImportStatement("world", Location.fromFileLineColumn("test file", 4, 1)));
  }

  @Test
  public void checkSyntax_good_multilineLiteral() throws Exception {
    String program =
        """
        abc()
        # Ludicrous as this may be, it's still valid syntax. Your funeral, etc...
        module_import(\"""hullo
        world\""")
        """;
    assertThat(checkSyntax(program))
        .containsExactly(
            new ModuleImportStatement(
                "hullo\nworld", Location.fromFileLineColumn("test file", 3, 1)));
  }

  @Test
  public void checkSyntax_bad_if() throws Exception {
    String program = """
        abc()
        if d > 3:
          pass
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage()).contains("`if` statements are not allowed in MODULE.bazel files");
  }

  @Test
  public void checkSyntax_bad_assignModuleImportResult() throws Exception {
    String program = """
        foo = module_import('hello')
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called directly at the top-level");
  }

  @Test
  public void checkSyntax_bad_assignModuleImportIdentifier() throws Exception {
    String program = """
        foo = module_import
        foo('hello')
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called directly at the top-level");
  }

  @Test
  public void checkSyntax_bad_moduleImportIdentifierReassigned() throws Exception {
    String program = """
        module_import = print
        module_import('hello')
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called directly at the top-level");
  }

  @Test
  public void checkSyntax_bad_multipleArgumentsToModuleImport() throws Exception {
    String program = """
        module_import('hello', 'world')
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called with exactly one positional");
  }

  @Test
  public void checkSyntax_bad_keywordArgumentToModuleImport() throws Exception {
    String program = """
        module_import(label='hello')
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called with exactly one positional");
  }

  @Test
  public void checkSyntax_bad_nonLiteralArgumentToModuleImport() throws Exception {
    String program = """
        foo = 'hello'
        module_import(foo)
        """;
    var ex = assertThrows(SyntaxError.Exception.class, () -> checkSyntax(program));
    assertThat(ex.getMessage())
        .contains("the `module_import` directive MUST be called with exactly one positional");
  }
}
