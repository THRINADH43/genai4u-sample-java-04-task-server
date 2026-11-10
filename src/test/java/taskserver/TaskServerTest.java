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
        for (int i = 0; i < 400; i++) {
            Task t = service.find(id).orElseThrow();
            if (t.status() == Task.Status.DONE || t.status() == Task.Status.FAILED) return t;
            Thread.sleep(20);
        }
        throw new AssertionError("task did not finish");
    }

    @Test
    void submitAndReadTask() throws Exception {
        HttpResponse<String> r = post("type=count_words&text=namaste+duniya+hello");
        assertEquals(201, r.statusCode());
        String id = Json.field(r.body(), "id");
        waitFor(id);
        HttpResponse<String> g = get(id);
        assertEquals(200, g.statusCode());
        assertEquals("DONE", Json.field(g.body(), "status"));
        assertEquals("3", Json.field(g.body(), "result"));
    }

    @Test
    void jsonBodyAlsoWorks() throws Exception {
        HttpResponse<String> r = post("{\"type\":\"count_words\",\"text\":\"one two\"}");
        assertEquals(201, r.statusCode());
        assertEquals("2", waitFor(Json.field(r.body(), "id")).result());
    }

    @Test
    void badRequestsGet400And404() throws Exception {
        assertEquals(400, post("text=x").statusCode());
        assertEquals(400, post("type=rm-rf").statusCode());
        assertEquals(400, post("type=sleep&seconds=60").statusCode());
        assertEquals(400, post("type=sleep&seconds=abc").statusCode());
        assertEquals(400, post("type=count_words").statusCode());
        assertEquals(404, get("no-such-id").statusCode());
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/tasks"))
                .PUT(HttpRequest.BodyPublishers.ofString("")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, put.statusCode());
    }

    @Test
    void failingTaskIsMarkedFailed() throws Exception {
        Task t = new Task("x", "count_words", "a b");
        TaskHandler broken = input -> { throw new IllegalStateException("boom"); };
        java.lang.reflect.Method m = TaskService.class.getDeclaredMethod("execute", Task.class, TaskHandler.class);
        m.setAccessible(true);
        m.invoke(service, t, broken);
        assertEquals(Task.Status.FAILED, t.status());
        assertEquals("boom", t.result());
    }

    @Test
    void tasksRunConcurrently() throws Exception {
        long start = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ids.add(service.submit("sleep", "1").id());
        }
        for (String id : ids) {
            assertEquals(Task.Status.DONE, waitFor(id).status());
        }
        assertTrue(System.currentTimeMillis() - start < 2500, "4 x 1 s on 4 threads should overlap");
    }

    @Test
    void manyThreadsSubmittingAtOnceLoseNoTasks() throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<String> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < 25; j++) ids.add(service.submit("count_words", "a b c").id());
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        assertEquals(200, ids.size());
        for (String id : ids) assertEquals("3", waitFor(id).result());
    }
}
