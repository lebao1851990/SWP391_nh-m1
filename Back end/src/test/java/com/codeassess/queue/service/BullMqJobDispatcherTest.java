package com.codeassess.queue.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BullMqJobDispatcherTest {

    @Test
    @DisplayName("dispatchGradingJob succeeds when POST returns 200 OK")
    void testDispatchGradingJob_Success() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(requestTo("http://localhost:3001/api/jobs/grade"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        boolean result = dispatcher.dispatchGradingJob("sub-1", "exam-1", "SINGLE_FILE");

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Focused Test 5: POST timeout followed by successful reconciliation via GET")
    void testDispatchGradingJob_PostFails_ReconciledViaGet() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        // First call: POST fails (e.g. 500 error or network disconnect)
        server.expect(requestTo("http://localhost:3001/api/jobs/grade"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // Reconcile call: GET /api/jobs/grading-sub-reconcile returns 200 OK with state waiting
        server.expect(requestTo("http://localhost:3001/api/jobs/grading-sub-reconcile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"waiting\",\"id\":\"grading-sub-reconcile\"}", MediaType.APPLICATION_JSON));

        boolean result = dispatcher.dispatchGradingJob("sub-reconcile", "exam-1", "ZIP");

        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Focused Test 1: GET waiting/active/delayed states correctly distinguished and verified enqueued")
    void testCheckJobStatus_WaitingActiveDelayed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-w"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"waiting\"}", MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-a"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"active\"}", MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"delayed\"}", MediaType.APPLICATION_JSON));

        assertThat(dispatcher.checkJobStatus("sub-w")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.WAITING);
        assertThat(dispatcher.isJobEnqueued("sub-w")).isTrue();

        assertThat(dispatcher.checkJobStatus("sub-a")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.ACTIVE);
        assertThat(dispatcher.isJobEnqueued("sub-a")).isTrue();

        assertThat(dispatcher.checkJobStatus("sub-d")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.DELAYED);
        assertThat(dispatcher.isJobEnqueued("sub-d")).isTrue();

        server.verify();
    }

    @Test
    @DisplayName("Focused Test 2: GET completed/failed states correctly distinguished and verified accepted")
    void testCheckJobStatus_CompletedFailed() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-comp"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"completed\"}", MediaType.APPLICATION_JSON));

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-fail"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"failed\"}", MediaType.APPLICATION_JSON));

        assertThat(dispatcher.checkJobStatus("sub-comp")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.COMPLETED);
        assertThat(dispatcher.isJobEnqueued("sub-comp")).isTrue();

        assertThat(dispatcher.checkJobStatus("sub-fail")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.FAILED);
        assertThat(dispatcher.isJobEnqueued("sub-fail")).isTrue();

        server.verify();
    }

    @Test
    @DisplayName("Focused Test 3: GET 404 returns NOT_FOUND (job confirmed absent)")
    void testCheckJobStatus_Http404_ReturnsNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-404"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        assertThat(dispatcher.checkJobStatus("sub-404")).isEqualTo(BullMqJobDispatcher.JobQueueStatus.NOT_FOUND);
        assertThat(dispatcher.isJobEnqueued("sub-404")).isFalse();

        server.verify();
    }

    @Test
    @DisplayName("Focused Test 4: GET timeout/connection error returns UNKNOWN and is not confused with 404")
    void testCheckJobStatus_NetworkError_ReturnsUnknown() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(ExpectedCount.twice(), requestTo("http://localhost:3001/api/jobs/grading-sub-net-err"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // Crucial distinction: network error yields UNKNOWN, NOT NOT_FOUND (404)
        BullMqJobDispatcher.JobQueueStatus status = dispatcher.checkJobStatus("sub-net-err");
        assertThat(status).isEqualTo(BullMqJobDispatcher.JobQueueStatus.UNKNOWN);
        assertThat(status).isNotEqualTo(BullMqJobDispatcher.JobQueueStatus.NOT_FOUND);
        assertThat(dispatcher.isJobEnqueued("sub-net-err")).isFalse();

        server.verify();
    }

    @Test
    @DisplayName("dispatchGradingJob returns false when POST fails and GET reconciliation returns 404")
    void testDispatchGradingJob_PostFails_ReconciliationNotFound_ReturnsFalse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:3001");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(restClient, true, "http://localhost:3001");

        server.expect(requestTo("http://localhost:3001/api/jobs/grade"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        server.expect(requestTo("http://localhost:3001/api/jobs/grading-sub-notfound"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        boolean result = dispatcher.dispatchGradingJob("sub-notfound", "exam-1", "ZIP");

        assertThat(result).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("dispatchGradingJob returns false immediately when disabled")
    void testDispatchGradingJob_Disabled_ReturnsFalse() {
        BullMqJobDispatcher dispatcher = new BullMqJobDispatcher(null, false, "http://localhost:3001");
        boolean result = dispatcher.dispatchGradingJob("sub-1", "exam-1", "ZIP");
        assertThat(result).isFalse();
    }
}
