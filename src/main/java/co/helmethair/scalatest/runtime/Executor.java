package co.helmethair.scalatest.runtime;

import co.helmethair.scalatest.descriptor.ScalatestEngineDescriptor;
import co.helmethair.scalatest.descriptor.ScalatestFailedInitDescriptor;
import co.helmethair.scalatest.descriptor.ScalatestSuiteDescriptor;
import co.helmethair.scalatest.descriptor.ScalatestTestDescriptor;
import co.helmethair.scalatest.reporter.JUnitReporter;
import co.helmethair.scalatest.scala.ScalaConversions;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.scalatest.*;
import org.scalatest.events.Event;
import org.scalatest.events.Ordinal;
import org.scalatest.events.RunAborted$;
import org.scalatest.events.SuiteAborted$;
import org.scalatest.exceptions.TestFailedException;
import scala.Option;
import scala.util.control.NonFatal$;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.helmethair.scalatest.scala.OptionHelper.getOrElse;
import static co.helmethair.scalatest.scala.ScalaConversions.*;

public class Executor {

    private static final ConfigMap emptyConfigMap = ConfigMap$.MODULE$.empty();
    private static final scala.collection.immutable.Set<String> chosenStyles = ScalaConversions.asScalaSet(Collections.<String>emptySet()).toSet();
    private boolean skipAfterFail;

    public Executor() {
        skipAfterFail = false;
    }

    public Executor(boolean skipAfterFail) {
        this.skipAfterFail = skipAfterFail;
    }

    public void executeTest(TestDescriptor test, JUnitReporter reporter) {
        try {
            if (skipAfterFail && reporter.getSkipWithCause() != null) {
                reporter.getJunitListener().executionFinished(test, TestExecutionResult.aborted(reporter.getSkipWithCause()));
                return;
            }
            if (test instanceof ScalatestSuiteDescriptor) {
                if (test.getChildren().isEmpty()) {
                    //if there are no children try to execute it with Scalatest native executor (no reporting)
                    ((ScalatestSuiteDescriptor) test).getScalasuite().execute(
                            null, emptyConfigMap, true, false, false, false, false);
                } else {
                    executeSuite(test, reporter);
                }
            } else if (test instanceof ScalatestTestDescriptor) {
                //this branch should not execute in normal cases as tests are executed together per suite
                runScalatests(((ScalatestTestDescriptor) test).getContainingSuite(),
                        Collections.singletonList((ScalatestTestDescriptor) test), reporter);
            } else if (test instanceof ScalatestEngineDescriptor) {
                executeSuite(test, reporter);
            } else if (test instanceof ScalatestFailedInitDescriptor) {
                reporter.getJunitListener().executionStarted(test);
                reporter.getJunitListener().executionFinished(test, TestExecutionResult.failed(((ScalatestFailedInitDescriptor) test).getCause()));
            }
        } catch (Throwable e) {
            reporter.getJunitListener().executionFinished(test, TestExecutionResult.failed(e));
        }
    }

    private void executeSuite(TestDescriptor test, JUnitReporter reporter) {
        reporter.getJunitListener().executionStarted(test);
        Set<? extends TestDescriptor> children = test.getChildren();

        List<ScalatestTestDescriptor> tests = children.stream().filter(c -> c instanceof ScalatestTestDescriptor)
                .map(c -> (ScalatestTestDescriptor) c).collect(Collectors.toList());

        Set<TestDescriptor> subSuites = new HashSet<>(children);
        subSuites.removeAll(tests);

        subSuites.stream()
                .sorted(Comparator.comparing(TestDescriptor::getDisplayName))
                .forEach(c -> executeTest(c, reporter));

        boolean suitExecutedOk = true;
        if (!tests.isEmpty()) {
            suitExecutedOk = runScalatests((ScalatestSuiteDescriptor) test,
                    tests.stream()
                            .sorted(Comparator.comparing(TestDescriptor::getDisplayName))
                            .collect(Collectors.toList()),
                    reporter);
        }

        if (suitExecutedOk){
            // if exception is thrown during suit execution (init, before/after all) we should not report a SUCCESS
            reporter.getJunitListener().executionFinished(test, TestExecutionResult.successful());
        }
    }

    private boolean runScalatests(ScalatestSuiteDescriptor containingSuite, List<ScalatestTestDescriptor> tests, JUnitReporter reporter) {

        Suite scalasuite = containingSuite.getScalasuite();

        String SelectedTag = "Selected";
        scala.collection.immutable.Set<String> selectedSet = asScalaSet(Collections.singleton(SelectedTag));
        Set<String> desiredTests = setAsJavaSet(scalasuite.testNames()).stream()
                .filter(s -> tests.stream().anyMatch(t -> s.equals(t.getTestName())
                        || NameTransformer.decode(s).equals(t.getTestName())))
                .collect(Collectors.toSet());

        scala.collection.immutable.Map<String, scala.collection.immutable.Set<String>> taggedTests = asScalaMap(desiredTests
                .stream().collect(Collectors.toMap(
                        Function.identity(),
                        x -> selectedSet
                )));

        scala.collection.immutable.Map<String, scala.collection.immutable.Map<String, scala.collection.immutable.Set<String>>> dynaTagsMap = asScalaMap(
                Collections.singletonMap(scalasuite.suiteId(), taggedTests)
        );
        scala.collection.immutable.Map<String, scala.collection.immutable.Set<String>> emptyMap = asScalaMap(Collections.emptyMap());

        Filter filter = Filter$.MODULE$.apply(
                Option.apply(selectedSet),
                Filter$.MODULE$.apply$default$2(),
                false,
                new DynaTags(emptyMap, dynaTagsMap)
        );

        Args args = createArgs(reporter, filter);
        try {
            Status status = scalasuite.run(Option.apply(null), args);
            status.waitUntilCompleted();
            return true;
        } catch (Throwable e) {
            if (e instanceof InstantiationException || e instanceof IllegalAccessException) {
                reporter.apply(suiteAborted(args.tracker().nextOrdinal(), e, Resources.cannotInstantiateSuite(e.getMessage()), scalasuite));
            } else if (e instanceof TestFailedException) {
                reporter.apply(suiteAborted(args.tracker().nextOrdinal(),
                        getOrElse(((TestFailedException) e).cause(), e), Resources.bigProblems(e), scalasuite));
            } else if (e instanceof NoClassDefFoundError) {
                reporter.apply(runAborted(args.tracker().nextOrdinal(), e, Resources.cannotLoadClass(e.getMessage()), scalasuite));
            } else {
                reporter.apply(runAborted(args.tracker().nextOrdinal(), e, Resources.bigProblems(e), scalasuite));
                if (!NonFatal$.MODULE$.apply(e)) {
                    throw e;
                }
            }
            return false;
        }
    }

    private Event runAborted(Ordinal ordinal, Throwable e, String reason, Suite scalasuite) {
        return RunAborted$.MODULE$.apply(
                ordinal,
                reason,
                Option.apply(e),
                Option.apply(null),
                Option.apply(null),
                Option.apply(null),
                Option.apply(null),
                Option.apply(scalasuite),
                Thread.currentThread().getName(),
                (new Date()).getTime()
        );
    }

    private Event suiteAborted(Ordinal ordinal, Throwable e, String message, Suite suite) {
        return SuiteAborted$.MODULE$.apply(
                ordinal,
                message,
                suite.suiteName(),
                suite.suiteId(),
                Option.apply(suite.getClass().getName()),
                Option.apply(e),
                Option.apply(null),
                Option.apply(null),
                Option.apply(null),
                Option.apply(null),
                Option.apply(null),
                Thread.currentThread().getName(),
                (new Date()).getTime()
        );
    }

    private Args createArgs(JUnitReporter reporter, Filter filter) {
        Filter filterParam;
        if (filter == null) {
            filterParam = Filter$.MODULE$.apply(
                    Filter$.MODULE$.apply$default$1(),
                    Filter$.MODULE$.apply$default$2(),
                    Filter$.MODULE$.apply$default$3(),
                    Filter$.MODULE$.apply$default$4());
        } else {
            filterParam = filter;
        }

        return new Args(reporter, new StopperImpl(), filterParam, emptyConfigMap,
                Option.apply(null), new Tracker(new Ordinal(0)), chosenStyles, false,
                Option.apply(null), Option.apply(null));
    }
}
