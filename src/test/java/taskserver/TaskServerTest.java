package taskserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServerTest {
    TaskService service;
    TaskServer server;
    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        service = new TaskService(4);
        server = new TaskServer(0, service);
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.stop();
        service.shutdown();
    }

    HttpResponse<String> post(String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/tasks"))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String id) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/tasks/" + id))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    Task waitFor(String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            Task t = service.find(id).orElseThrow();
            if (t.status() == Task.Status.DONE || t.status() == Task.Status.FAILED) return t;
            Thread.sleep(20);
        }
        throw new AssertionError("task did not finish");
    }

    @Test
    void submitAndReadTask() throws Exception {
        HttpResponse<String> r = post("{\"type\":\"uppercase\",\"input\":\"namaste\"}");
        assertEquals(201, r.statusCode());
        String id = Json.field(r.body(), "id");
        waitFor(id);
        HttpResponse<String> g = get(id);
        assertEquals(200, g.statusCode());
        assertEquals("NAMASTE", Json.field(g.body(), "result"));
    }

    @Test
    void badRequestsGet400And404() throws Exception {
        assertEquals(400, post("{\"input\":\"x\"}").statusCode());
        assertEquals(400, post("{\"type\":\"rm -rf\"}").statusCode());
        assertEquals(404, get("no-such-id").statusCode());
    }

    @Test
    void failingTaskIsMarkedFailed() throws Exception {
        String id = Json.field(post("{\"type\":\"sleep\",\"input\":\"abc\"}").body(), "id");
        assertEquals(Task.Status.FAILED, waitFor(id).status());
    }

    @Test
    void tasksRunConcurrently() throws Exception {
        long start = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(service.submit("sleep", "400").id());
        }
        for (String id : ids) {
            assertEquals(Task.Status.DONE, waitFor(id).status());
        }
        assertTrue(System.currentTimeMillis() - start < 1200, "4 x 400 ms on 4 threads should overlap");
    }

    @Test
    void manyThreadsSubmittingAtOnceLoseNoTasks() throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<String> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < 25; j++) ids.add(service.submit("reverse", "abc").id());
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        assertEquals(200, ids.size());
        for (String id : ids) assertEquals("cba", waitFor(id).result());
    }
}
