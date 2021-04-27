package co.helmethair.scalatest;

import co.helmethair.scalatest.helper.TestEngineExecutionListener;
import co.helmethair.scalatest.helper.TestHelpers;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.spy;

public class BeforeAfterTest implements TestHelpers {
    @Test
    void beforeAllAndAfterEachCalledTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.BeforeAndAfterEachTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("beforeEach", 2);
            put("afterEach", 2);
            put("runs", 1);
            put("runs again", 1);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));
    }

    @Test
    void beforeAllAndAfterAllCalledTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.BeforeAndAfterAllTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("beforeAll", 1);
            put("afterAll", 1);
            put("runs", 1);
            put("runs again", 1);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));
    }

    @Test
    void beforeFailedTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.FailInBeforeTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("before", 1);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));

        verifyTestFailReportedWith("[engine:scalatest]/[suite:tests.FailInBeforeTest]", listener, null);
        verifyTestSuccessNotReported("[engine:scalatest]/[suite:tests.FailInBeforeTest]", listener);
    }

    @Test
    void beforeAllFailedTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.FailInBeforeAllTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("before", 1);
            put("test 1 runs", 0);
            put("test 2 runs", 0);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));
        verifyTestFailReportedWith("[engine:scalatest]/[suite:tests.FailInBeforeAllTest]", listener, null);
        verifyTestSuccessNotReported("[engine:scalatest]/[suite:tests.FailInBeforeAllTest]", listener);
    }

    @Test
    void afterFailedTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.FailInAfterTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("after", 1);
            put("runs", 1);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));

        verifyTestSuccessReported("[engine:scalatest]/[suite:tests.FailInAfterTest]/[test:test]", listener);
        verifyTestFailReportedWith("[engine:scalatest]/[suite:tests.FailInAfterTest]", listener, null);
        verifyTestSuccessNotReported("[engine:scalatest]/[suite:tests.FailInAfterTest]", listener);
    }

    @Test
    void afterAllFailedTest() {
        EngineDiscoveryRequest discoveryRequest = createClassDiscoveryRequest("tests.FailInAfterAllTest");
        TestDescriptor discoveredTests = engine.discover(discoveryRequest, engineId);
        TestEngineExecutionListener listener = spy(new TestEngineExecutionListener());
        ExecutionRequest executionRequest = new ExecutionRequest(discoveredTests, listener, null);

        Map<String, Integer> calls = new HashMap<String, Integer>() {{
            put("after", 1);
            put("test 1 runs", 1);
            put("test 2 runs", 1);
        }};

        verifyTestExecuteCode(calls, () -> engine.execute(executionRequest));

        verifyTestSuccessReported("[engine:scalatest]/[suite:tests.FailInAfterAllTest]/[test:test 1]", listener);
        verifyTestSuccessReported("[engine:scalatest]/[suite:tests.FailInAfterAllTest]/[test:test 2]", listener);
        verifyTestFailReportedWith("[engine:scalatest]/[suite:tests.FailInAfterAllTest]", listener, null);
        verifyTestSuccessNotReported("[engine:scalatest]/[suite:tests.FailInAfterAllTest]", listener);
    }
}
