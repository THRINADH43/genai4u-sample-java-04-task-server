package taskserver;

/** Strategy pattern: each task type is one handler, chosen by name at run time. */
public interface TaskHandler {
    String run(String input) throws Exception;
}
