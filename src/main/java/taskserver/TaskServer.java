package taskserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * POST /tasks  type=count_words&text=hello+world  (form fields)  -> 201 {"id":...}
 *              {"type":"sleep","seconds":3}       (JSON also works)
 * GET  /tasks/{id}                                  -> 200 task, 404 if unknown
 */
public class TaskServer {
    private static final Logger LOG = Logger.getLogger(TaskServer.class.getName());

    private final HttpServer server;
    private final TaskService service;

    public TaskServer(int port, TaskService service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/tasks", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));  // request threads, separate from task threads
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
        LOG.info("listening on port " + port());
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (method.equals("POST") && path.equals("/tasks")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> fields = parseFields(body);
                String type = fields.get("type");
                if (type == null) {
                    send(ex, 400, "{\"error\":\"type is required\"}");
                    return;
                }
                try {
                    String field = TaskHandlers.INPUT_FIELD.getOrDefault(type, "text");
                    Task t = service.submit(type, fields.get(field));
                    send(ex, 201, t.toJson());
                } catch (IllegalArgumentException e) {
                    send(ex, 400, "{\"error\":\"" + Json.escape(e.getMessage()) + "\"}");
                }
            } else if (method.equals("GET") && path.startsWith("/tasks/")) {
                String id = path.substring("/tasks/".length());
                service.find(id).ifPresentOrElse(
                        t -> sendQuietly(ex, 200, t.toJson()),
                        () -> sendQuietly(ex, 404, "{\"error\":\"task not found\"}"));
            } else {
                send(ex, 405, "{\"error\":\"method not allowed\"}");
            }
        } finally {
            ex.close();
        }
    }

    /** Form fields (type=sleep&seconds=3) or a flat JSON object. */
    static Map<String, String> parseFields(String body) {
        Map<String, String> fields = new HashMap<>();
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            for (String name : new String[] {"type", "text", "seconds"}) {
                String value = Json.field(trimmed, name);
                if (value != null) fields.put(name, value);
            }
            return fields;
        }
        for (String pair : trimmed.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                fields.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return fields;
    }

    private void sendQuietly(HttpExchange ex, int status, String json) {
        try {
            send(ex, status, json);
        } catch (IOException e) {
            LOG.warning("could not reply: " + e.getMessage());
        }
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        int threads = Integer.parseInt(System.getenv().getOrDefault("THREADS", "4"));
        new TaskServer(port, new TaskService(threads)).start();
    }
}
