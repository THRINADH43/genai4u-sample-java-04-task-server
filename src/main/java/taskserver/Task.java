package taskserver;

/** A submitted task. Status and result change, so they are volatile for visibility across threads. */
public class Task {
    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    private final String id;
    private final String type;
    private final String input;
    private volatile Status status = Status.QUEUED;
    private volatile String result;

    public Task(String id, String type, String input) {
        this.id = id;
        this.type = type;
        this.input = input;
    }

    public String id() { return id; }
    public String type() { return type; }
    public String input() { return input; }
    public Status status() { return status; }
    public String result() { return result; }

    void markRunning() { status = Status.RUNNING; }
    void finish(String result) { this.result = result; status = Status.DONE; }
    void fail(String error) { this.result = error; status = Status.FAILED; }

    public String toJson() {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"status\":\"" + status + "\",\"result\":"
                + (result == null ? "null" : "\"" + Json.escape(result) + "\"") + "}";
    }
}
