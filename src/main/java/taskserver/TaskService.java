package taskserver;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Accepts tasks and runs them on a fixed thread pool. Tasks live in a ConcurrentHashMap. */
public class TaskService {
    private static final Logger LOG = Logger.getLogger(TaskService.class.getName());

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final ExecutorService pool;

    public TaskService(int threads) {
        this.pool = Executors.newFixedThreadPool(threads);
    }

    public Task submit(String type, String input) {
        TaskHandler handler = TaskHandlers.forType(type)
                .orElseThrow(() -> new IllegalArgumentException("unknown task type: " + type));
        TaskHandlers.validate(type, input);
        Task task = new Task(UUID.randomUUID().toString(), type, input.trim());
        tasks.put(task.id(), task);
        pool.submit(() -> execute(task, handler));
        LOG.info(() -> "queued task " + task.id() + " (" + type + ")");
        return task;
    }

    private void execute(Task task, TaskHandler handler) {
        task.markRunning();
        try {
            task.finish(handler.run(task.input()));
            LOG.info(() -> "task " + task.id() + " done");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.fail("interrupted");
        } catch (Exception e) {
            task.fail(e.getMessage());
            LOG.warning(() -> "task " + task.id() + " failed: " + e.getMessage());
        }
    }

    public Optional<Task> find(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    public void shutdown() throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }
}
