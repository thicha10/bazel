package com.google.devtools.build.lib.bazel.bzlmod;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.ExtendedEventHandler;
import com.google.devtools.build.lib.packages.BazelStarlarkEnvironment;
import com.google.devtools.build.lib.packages.DotBazelFileSyntaxChecker;
import com.google.devtools.build.lib.server.FailureDetails.ExternalDeps.Code;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.Argument;
import net.starlark.java.syntax.CallExpression;
import net.starlark.java.syntax.ExpressionStatement;
import net.starlark.java.syntax.Identifier;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.Program;
import net.starlark.java.syntax.StarlarkFile;
import net.starlark.java.syntax.StringLiteral;
import net.starlark.java.syntax.SyntaxError;

/**
 * Represents a compiled MODULE.bazel file, ready to be executed on a {@link StarlarkThread}. It's
 * been successfully checked for syntax errors.
 *
 * <p>Use the {@link #parseAndCompile} factory method instead of directly instantiating this record.
 */
public record CompiledModuleFile(
    ModuleFile moduleFile,
    Program program,
    Module predeclaredEnv,
    ImmutableList<ModuleImportStatement> importStatements) {

  record ModuleImportStatement(String importLabel, Location location) {}

  /** Parses and compiles a given module file, checking it for syntax errors. */
  public static CompiledModuleFile parseAndCompile(
      ModuleFile moduleFile,
      ModuleKey moduleKey,
      StarlarkSemantics starlarkSemantics,
      BazelStarlarkEnvironment starlarkEnv,
      ExtendedEventHandler eventHandler)
      throws ExternalDepsException {
    StarlarkFile starlarkFile =
        StarlarkFile.parse(ParserInput.fromUTF8(moduleFile.getContent(), moduleFile.getLocation()));
    if (!starlarkFile.ok()) {
      Event.replayEventsOn(eventHandler, starlarkFile.errors());
      throw ExternalDepsException.withMessage(
          Code.BAD_MODULE, "error parsing MODULE.bazel file for %s", moduleKey);
    }
    try {
      ImmutableList<ModuleImportStatement> importStatements = checkModuleFileSyntax(starlarkFile);
      Module predeclaredEnv =
          Module.withPredeclared(
              starlarkSemantics, starlarkEnv.getStarlarkGlobals().getModuleToplevels());
      Program program = Program.compileFile(starlarkFile, predeclaredEnv);
      return new CompiledModuleFile(moduleFile, program, predeclaredEnv, importStatements);
    } catch (SyntaxError.Exception e) {
      Event.replayEventsOn(eventHandler, e.errors());
      throw ExternalDepsException.withMessage(
          Code.BAD_MODULE, "syntax error in MODULE.bazel file for %s", moduleKey);
    }
  }

  @VisibleForTesting
  static ImmutableList<ModuleImportStatement> checkModuleFileSyntax(StarlarkFile starlarkFile)
      throws SyntaxError.Exception {
    var importStatements = ImmutableList.<ModuleImportStatement>builder();
    new DotBazelFileSyntaxChecker("MODULE.bazel files", /* canLoadBzl= */ false) {
      @Override
      public void visit(ExpressionStatement node) {
        // We can assume this statement isn't nested in any block, since we don't allow
        // `if`/`def`/`for` in MODULE.bazel.
        if (node.getExpression() instanceof CallExpression call
            && call.getFunction() instanceof Identifier id
            && id.getName().equals("module_import")) {
          // Found a top-level call to module_import!
          if (call.getArguments().size() == 1
              && call.getArguments().getFirst() instanceof Argument.Positional pos
              && pos.getValue() instanceof StringLiteral str) {
            importStatements.add(
                new ModuleImportStatement(str.getValue(), call.getStartLocation()));
            // We can stop going down this rabbit hole now.
            return;
          }
          error(
              node.getStartLocation(),
              "the `module_import` directive MUST be called with exactly one positional "
                  + "argument that is a string literal");
          return;
        }
        super.visit(node);
      }

      @Override
      public void visit(Identifier node) {
        if (node.getName().equals("module_import")) {
          // If we somehow reach the `module_import` identifier but NOT as part of a top-level call
          // expression, cry foul.
          error(
              node.getStartLocation(),
              "the `module_import` directive MUST be called directly at the top-level");
        }
        super.visit(node);
      }
    }.check(starlarkFile);
    return importStatements.build();
  }

  public void runOnThread(StarlarkThread thread) throws EvalException, InterruptedException {
    Starlark.execFileProgram(program, predeclaredEnv, thread);
  }
}
