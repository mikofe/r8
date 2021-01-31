// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.RetraceUtils.firstNonWhiteSpaceCharacterFromIndex;
import static com.android.tools.r8.utils.ExceptionUtils.failWithFakeEntry;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.Version;
import com.android.tools.r8.retrace.RetraceCommand.Builder;
import com.android.tools.r8.retrace.internal.PlainStackTraceVisitor;
import com.android.tools.r8.retrace.internal.RetraceAbortException;
import com.android.tools.r8.retrace.internal.RetraceRegularExpression;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A retrace tool for obfuscated stack traces.
 *
 * <p>This is the interface for getting de-obfuscating stack traces, similar to the proguard retrace
 * tool.
 */
@Keep
public class Retrace {

  // This is a slight modification of the default regular expression shown for proguard retrace
  // that allow for retracing classes in the form <class>: lorem ipsum...
  // Seems like Proguard retrace is expecting the form "Caused by: <class>".
  public static final String DEFAULT_REGULAR_EXPRESSION =
      "(?:.*?\\bat\\s+%c\\.%m\\s*\\(%s(?::%l)?\\)\\s*(?:~\\[.*\\])?)"
          + "|(?:(?:(?:%c|.*)?[:\"]\\s+)?%c(?::.*)?)";

  public static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: retrace <proguard-map> [stack-trace-file] "
              + "[--regex <regexp>, --verbose, --info, --quiet]",
          "  where <proguard-map> is an r8 generated mapping file.");

  private static Builder parseArguments(String[] args, DiagnosticsHandler diagnosticsHandler) {
    ParseContext context = new ParseContext(args);
    Builder builder = RetraceCommand.builder(diagnosticsHandler);
    boolean hasSetProguardMap = false;
    boolean hasSetStackTrace = false;
    boolean hasSetRegularExpression = false;
    boolean hasSetQuiet = false;
    while (context.head() != null) {
      Boolean help = OptionsParsing.tryParseBoolean(context, "--help");
      if (help != null) {
        return null;
      }
      Boolean version = OptionsParsing.tryParseBoolean(context, "--version");
      if (version != null) {
        return null;
      }
      Boolean info = OptionsParsing.tryParseBoolean(context, "--info");
      if (info != null) {
        // This is already set in the diagnostics handler.
        continue;
      }
      Boolean verbose = OptionsParsing.tryParseBoolean(context, "--verbose");
      if (verbose != null) {
        builder.setVerbose(true);
        continue;
      }
      Boolean quiet = OptionsParsing.tryParseBoolean(context, "--quiet");
      if (quiet != null) {
        hasSetQuiet = true;
        continue;
      }
      String regex = OptionsParsing.tryParseSingle(context, "--regex", "r");
      if (regex != null && !regex.isEmpty()) {
        builder.setRegularExpression(regex);
        hasSetRegularExpression = true;
        continue;
      }
      if (!hasSetProguardMap) {
        builder.setProguardMapProducer(getMappingSupplier(context.head(), diagnosticsHandler));
        context.next();
        hasSetProguardMap = true;
      } else if (!hasSetStackTrace) {
        builder.setStackTrace(getStackTraceFromFile(context.head(), diagnosticsHandler));
        context.next();
        hasSetStackTrace = true;
      } else {
        diagnosticsHandler.error(
            new StringDiagnostic(
                String.format("Too many arguments specified for builder at '%s'", context.head())));
        diagnosticsHandler.error(new StringDiagnostic(USAGE_MESSAGE));
        throw new RetraceAbortException();
      }
    }
    if (!hasSetProguardMap) {
      diagnosticsHandler.error(new StringDiagnostic("Mapping file not specified"));
      throw new RetraceAbortException();
    }
    if (!hasSetStackTrace) {
      builder.setStackTrace(getStackTraceFromStandardInput(hasSetQuiet));
    }
    if (!hasSetRegularExpression) {
      builder.setRegularExpression(DEFAULT_REGULAR_EXPRESSION);
    }
    return builder;
  }

  private static ProguardMapProducer getMappingSupplier(
      String mappingPath, DiagnosticsHandler diagnosticsHandler) {
    Path path = Paths.get(mappingPath);
    if (!Files.exists(path)) {
      diagnosticsHandler.error(
          new StringDiagnostic(String.format("Could not find mapping file '%s'.", mappingPath)));
      throw new RetraceAbortException();
    }
    return () -> {
      try {
        return new String(Files.readAllBytes(path));
      } catch (IOException e) {
        diagnosticsHandler.error(
            new StringDiagnostic(String.format("Could not open mapping file '%s'.", mappingPath)));
        throw new RuntimeException(e);
      }
    };
  }

  private static List<String> getStackTraceFromFile(
      String stackTracePath, DiagnosticsHandler diagnostics) {
    try {
      return Files.readAllLines(Paths.get(stackTracePath), Charsets.UTF_8);
    } catch (IOException e) {
      diagnostics.error(new StringDiagnostic("Could not find stack trace file: " + stackTracePath));
      throw new RetraceAbortException();
    }
  }

  /**
   * The main entry point for running retrace.
   *
   * @param command The command that describes the desired behavior of this retrace invocation.
   */
  public static void run(RetraceCommand command) {
    try {
      Timing timing = Timing.create("R8 retrace", command.printMemory());
      timing.begin("Read proguard map");
      Retracer retracer =
          Retracer.createDefault(command.proguardMapProducer, command.diagnosticsHandler);
      timing.end();
      timing.begin("Parse and Retrace");
      StackTraceVisitor<StackTraceElementStringProxy> stackTraceVisitor =
          command.regularExpression != null
              ? new RetraceRegularExpression(command.stackTrace, command.regularExpression)
              : new PlainStackTraceVisitor(command.stackTrace, command.diagnosticsHandler);
      timing.end();
      timing.begin("Report result");
      command.retracedStackTraceConsumer.accept(
          runInternal(stackTraceVisitor, retracer, command.isVerbose));
      timing.end();
      if (command.printTimes()) {
        timing.report();
      }
    } catch (InvalidMappingFileException e) {
      command.diagnosticsHandler.error(new ExceptionDiagnostic(e));
      throw e;
    }
  }

  /**
   * Entry point for running retrace with default parsing on a stack trace
   *
   * @param retracer the retracer used to parse each stack trace frame.
   * @param stackTrace the stack trace to be parsed.
   * @param isVerbose if the output should embed verbose information.
   * @return the retraced strings in flat format
   */
  public static List<String> run(Retracer retracer, List<String> stackTrace, boolean isVerbose) {
    return runInternal(
        new RetraceRegularExpression(stackTrace, DEFAULT_REGULAR_EXPRESSION), retracer, isVerbose);
  }

  private static List<String> runInternal(
      StackTraceVisitor<StackTraceElementStringProxy> stackTraceVisitor,
      Retracer retracer,
      boolean isVerbose) {
    List<String> retracedStrings = new ArrayList<>();
    Box<StackTraceElementStringProxy> lastReportedFrame = new Box<>();
    Set<String> seenSetForLastReportedFrame = new HashSet<>();
    run(
        stackTraceVisitor,
        StackTraceElementProxyRetracer.createDefault(retracer),
        (stackTraceElement, frames) -> {
          frames.forEach(
              frame -> {
                StackTraceElementStringProxy originalItem = frame.getOriginalItem();
                boolean newFrame =
                    lastReportedFrame.getAndSet(stackTraceElement) != stackTraceElement;
                if (newFrame) {
                  seenSetForLastReportedFrame.clear();
                }
                String retracedString = originalItem.toRetracedItem(frame, isVerbose);
                if (seenSetForLastReportedFrame.add(retracedString)) {
                  if (frame.isAmbiguous() && !newFrame) {
                    int firstCharIndex = firstNonWhiteSpaceCharacterFromIndex(retracedString, 0);
                    retracedString =
                        retracedString.substring(0, firstCharIndex)
                            + "<OR> "
                            + retracedString.substring(firstCharIndex);
                  }
                  retracedStrings.add(retracedString);
                }
              });
        });
    return retracedStrings;
  }

  /**
   * @param stackTraceVisitor the stack trace visitor.
   * @param proxyRetracer the retracer used to parse each stack trace frame.
   * @param resultConsumer consumer to accept each parsed stack trace frame. The stack-trace element
   *     is guaranteed to be unique per line.
   */
  public static <T extends StackTraceElementProxy<?>> void run(
      StackTraceVisitor<T> stackTraceVisitor,
      StackTraceElementProxyRetracer<T> proxyRetracer,
      BiConsumer<T, List<RetraceStackTraceProxy<T>>> resultConsumer) {
    stackTraceVisitor.forEach(
        stackTraceElement -> {
          Box<List<RetraceStackTraceProxy<T>>> currentList = new Box<>();
          Map<RetraceStackTraceProxy<T>, List<RetraceStackTraceProxy<T>>> ambiguousBlocks =
              new HashMap<>();
          proxyRetracer
              .retrace(stackTraceElement)
              .forEach(
                  retracedElement -> {
                    if (retracedElement.isTopFrame() || !retracedElement.hasRetracedClass()) {
                      List<RetraceStackTraceProxy<T>> block = new ArrayList<>();
                      ambiguousBlocks.put(retracedElement, block);
                      currentList.set(block);
                    }
                    currentList.get().add(retracedElement);
                  });
          ambiguousBlocks.keySet().stream()
              .sorted()
              .forEach(
                  topFrame ->
                      resultConsumer.accept(stackTraceElement, ambiguousBlocks.get(topFrame)));
        });
  }

  public static void run(String[] args) throws RetraceFailedException {
    // To be compatible with standard retrace and remapper, we translate -arg into --arg.
    String[] mappedArgs = new String[args.length];
    boolean printInfo = false;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg == null || arg.length() < 2) {
        mappedArgs[i] = arg;
        continue;
      }
      if (arg.charAt(0) == '-' && arg.charAt(1) != '-') {
        mappedArgs[i] = "-" + arg;
      } else {
        mappedArgs[i] = arg;
      }
      if (mappedArgs[i].equals("--info")) {
        printInfo = true;
      }
    }
    RetraceDiagnosticsHandler retraceDiagnosticsHandler =
        new RetraceDiagnosticsHandler(new DiagnosticsHandler() {}, printInfo);
    try {
      run(mappedArgs, retraceDiagnosticsHandler);
    } catch (Throwable t) {
      throw failWithFakeEntry(
          retraceDiagnosticsHandler, t, RetraceFailedException::new, RetraceAbortException.class);
    }
  }

  private static void run(String[] args, DiagnosticsHandler diagnosticsHandler) {
    Builder builder = parseArguments(args, diagnosticsHandler);
    if (builder == null) {
      // --help or --version was an argument to list
      if (Arrays.asList(args).contains("--version")) {
        System.out.println("Retrace " + Version.getVersionString());
        return;
      }
      assert Arrays.asList(args).contains("--help");
      System.out.println("Retrace " + Version.getVersionString());
      System.out.print(USAGE_MESSAGE);
      return;
    }
    builder.setRetracedStackTraceConsumer(
        retraced -> {
          try (PrintStream printStream = new PrintStream(System.out, true, Charsets.UTF_8.name())) {
            for (String line : retraced) {
              printStream.println(line);
            }
          } catch (UnsupportedEncodingException e) {
            diagnosticsHandler.error(new StringDiagnostic(e.getMessage()));
          }
        });
    run(builder.build());
  }

  /**
   * The main entry point for running a legacy compatible retrace from the command line.
   *
   * @param args The argument that describes this command.
   */
  public static void main(String... args) {
    withMainProgramHandler(() -> run(args));
  }

  private static List<String> getStackTraceFromStandardInput(boolean printWaitingMessage) {
    if (!printWaitingMessage) {
      System.out.println("Waiting for stack-trace input...");
    }
    Scanner sc = new Scanner(new InputStreamReader(System.in, Charsets.UTF_8));
    List<String> readLines = new ArrayList<>();
    while (sc.hasNext()) {
      readLines.add(sc.nextLine());
    }
    return readLines;
  }

  private interface MainAction {
    void run() throws RetraceFailedException;
  }

  private static void withMainProgramHandler(MainAction action) {
    try {
      action.run();
    } catch (RetraceFailedException | RetraceAbortException e) {
      // Detail of the errors were already reported
      throw new RuntimeException("Retrace failed", e);
    } catch (Throwable t) {
      throw new RuntimeException("Retrace failed with an internal error.", t);
    }
  }

  private static class RetraceDiagnosticsHandler implements DiagnosticsHandler {

    private final DiagnosticsHandler diagnosticsHandler;
    private final boolean printInfo;

    public RetraceDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler, boolean printInfo) {
      this.diagnosticsHandler = diagnosticsHandler;
      this.printInfo = printInfo;
      assert diagnosticsHandler != null;
    }

    @Override
    public void error(Diagnostic error) {
      diagnosticsHandler.error(error);
    }

    @Override
    public void warning(Diagnostic warning) {
      diagnosticsHandler.warning(warning);
    }

    @Override
    public void info(Diagnostic info) {
      if (printInfo) {
        diagnosticsHandler.info(info);
      }
    }
  }
}
