# Concurrent task server

An HTTP server built on the JDK's own `com.sun.net.httpserver.HttpServer`. It accepts tasks,
runs them in the background on a thread pool, and reports their status. No frameworks and no
libraries apart from JUnit for tests.

## Build, test and run

Java 17+ and Maven.

```
mvn test
mvn package
PORT=8080 THREADS=4 java -cp target/classes taskserver.TaskServer
```

```
curl -X POST localhost:8080/tasks -d 'type=count_words&text=hello world from java'
# 201 {"id":"6b1e...","type":"count_words","status":"QUEUED","result":null}
curl -X POST localhost:8080/tasks -d 'type=sleep&seconds=3'
curl -X POST localhost:8080/tasks -d '{"type":"sleep","seconds":3}'      # JSON works too
curl localhost:8080/tasks/6b1e...
# 200 {"id":"6b1e...","type":"count_words","status":"DONE","result":"4"}
curl -X POST localhost:8080/tasks -d 'type=sleep&seconds=60'
# 400 {"error":"seconds must be from 1 to 10"}
```

Task types: `count_words` (field `text`) and `sleep` (field `seconds`, 1 to 10). Status is
`QUEUED`, `RUNNING`, `DONE` or `FAILED`.

## Endpoints

| Request            | Answer |
|--------------------|--------|
| `POST /tasks`      | 201 with the task; 400 if `type` is missing or unknown, or its field is missing or out of range |
| `GET /tasks/{id}`  | 200 with the task; 404 if unknown |
| anything else      | 405 |

## Concurrency

- Tasks run on `Executors.newFixedThreadPool(THREADS)`; HTTP requests have their own small
  pool, so slow tasks never block requests.
- All tasks are kept in a `ConcurrentHashMap`. A task's `status` and `result` are `volatile`,
  so a request thread always sees the latest value written by a worker thread.
- Tests check that 4 one-second sleep tasks finish in well under 4 seconds, and that 8 threads
  submitting 200 tasks at once lose none of them.

## Design patterns

- **Strategy**: `TaskHandler` is one interface; each task type (`count_words`, `sleep`) is one
  implementation. Adding a type means adding one entry, with no `if/else` chain in the service.
- **Factory**: `TaskHandlers.forType(name)` picks the handler, so the service and HTTP code
  never know the concrete classes.

## Logging

`java.util.logging` records each queued, finished and failed task.

## CI

`.github/workflows/tests.yml` runs `mvn test` on every push.
