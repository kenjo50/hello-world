import com.tub.BossServiceImpl;
import com.tub.MemberServiceImpl;
import com.tub.boss.BossServiceGrpc;
import com.tub.boss.CreateTaskRequest;
import com.tub.boss.MetricsRequest;
import com.tub.boss.MetricsResponse;
import com.tub.common.Task;
import com.tub.common.TaskList;
import com.tub.common.TaskStatus;
import com.tub.member.CompleteTaskRequest;
import com.tub.member.GetMyTasksRequest;
import com.tub.member.MemberServiceGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anforderung 4: Es verbinden sich mehrere Clients der unterschiedlichen Arten
 * (Boss- und Member-Clients) gleichzeitig mit dem Server. Der Test zeigt, dass
 * die gemeinsame Datenhaltung dabei konsistent bleibt (thread safe).
 *
 * Die entscheidenden Beweise:
 *  - Viele Boss-Clients erzeugen parallel Tasks. Wären die IDs (AtomicLong) oder
 *    die Map (ConcurrentHashMap) nicht thread safe, gäbe es doppelte IDs oder
 *    verlorene Schreibzugriffe -> die Assertions auf die IDs/Map-Größe schlügen fehl.
 *  - Gleichzeitig lesen Metrik-Clients laufend über die Datenhaltung. Bei einer
 *    nicht-nebenläufigen Map (z.B. HashMap) würde das während der parallelen
 *    Schreibzugriffe eine ConcurrentModificationException auslösen.
 */
public class ThreadSafetyTest {

    private static final int BOSSES = 8;
    private static final int TASKS_PER_BOSS = 50;
    private static final int MEMBERS = 4;
    private static final int METRIC_READERS = 4;
    private static final int TOTAL_TASKS = BOSSES * TASKS_PER_BOSS;

    private Server server;
    private Map<Long, Task> store;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        // Genau eine gemeinsame Datenhaltung, die sich beide Service-Arten teilen.
        store = new ConcurrentHashMap<>();
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new BossServiceImpl(store))
                .addService(new MemberServiceImpl(store))
                .build()
                .start();
        port = server.getPort(); // ephemerer Port -> Tests kollidieren nicht mit einem laufenden Server
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private ManagedChannel newChannel() {
        return Grpc.newChannelBuilder("localhost:" + port, InsecureChannelCredentials.create()).build();
    }

    @Test
    void serverBleibtUnterParallelenClientsKonsistent() throws Exception {
        // Thread-sichere Sammlung aller vergebenen Task-IDs (deckt ID-Kollisionen auf).
        Set<Long> createdIds = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newCachedThreadPool();

        // ---- Runde 1: viele Boss-Clients erstellen Tasks + Metrik-Clients lesen – alles parallel ----
        CyclicBarrier startR1 = new CyclicBarrier(BOSSES + METRIC_READERS);
        List<Future<?>> round1 = new ArrayList<>();

        for (int b = 0; b < BOSSES; b++) {
            final int bossIdx = b;
            round1.add(pool.submit(() -> {
                ManagedChannel ch = newChannel();
                try {
                    BossServiceGrpc.BossServiceBlockingStub stub = BossServiceGrpc.newBlockingStub(ch);
                    startR1.await(); // alle Boss-Clients starten gleichzeitig -> maximale Nebenläufigkeit
                    for (int t = 0; t < TASKS_PER_BOSS; t++) {
                        String member = "member-" + ((bossIdx + t) % MEMBERS);
                        Task created = stub.createTask(CreateTaskRequest.newBuilder()
                                .setTitle("task-" + bossIdx + "-" + t)
                                .setDescription("desc")
                                .setMemberID(member)
                                .build());
                        assertTrue(createdIds.add(created.getTaskID()),
                                "Doppelte Task-ID vergeben (nicht thread safe): " + created.getTaskID());
                    }
                } finally {
                    ch.shutdownNow();
                }
                return null;
            }));
        }
        for (int r = 0; r < METRIC_READERS; r++) {
            round1.add(pool.submit(() -> {
                ManagedChannel ch = newChannel();
                try {
                    BossServiceGrpc.BossServiceBlockingStub stub = BossServiceGrpc.newBlockingStub(ch);
                    startR1.await();
                    // Liest laufend über die Datenhaltung, während parallel geschrieben wird.
                    for (int i = 0; i < 300; i++) {
                        MetricsResponse m = stub.getMetrics(MetricsRequest.getDefaultInstance());
                        assertEquals(m.getTotalTasks(),
                                m.getOpenTasks() + m.getInProgressTasks() + m.getDoneTasks(),
                                "Metrik-Invariante verletzt");
                    }
                } finally {
                    ch.shutdownNow();
                }
                return null;
            }));
        }
        // get() propagiert jede Exception/AssertionError aus den Threads -> der Test schlägt dann fehl.
        for (Future<?> f : round1) {
            f.get(60, TimeUnit.SECONDS);
        }

        // Beweis 1: keine kollidierenden IDs und keine verlorenen Schreibzugriffe.
        assertEquals(TOTAL_TASKS, createdIds.size(), "Es wurden nicht alle IDs eindeutig vergeben");
        assertEquals(TOTAL_TASKS, store.size(), "Es gingen Tasks in der Datenhaltung verloren");

        // ---- Runde 2: Member-Clients haken parallel ihre Tasks ab + Metrik-Clients lesen ----
        CyclicBarrier startR2 = new CyclicBarrier(MEMBERS + METRIC_READERS);
        List<Future<?>> round2 = new ArrayList<>();
        for (int mi = 0; mi < MEMBERS; mi++) {
            final String member = "member-" + mi;
            round2.add(pool.submit(() -> {
                ManagedChannel ch = newChannel();
                try {
                    MemberServiceGrpc.MemberServiceBlockingStub stub = MemberServiceGrpc.newBlockingStub(ch);
                    startR2.await();
                    TaskList mine = stub.getMyTasks(GetMyTasksRequest.newBuilder()
                            .setMemberID(member).build());
                    for (Task t : mine.getTasksList()) {
                        Task done = stub.completeTask(CompleteTaskRequest.newBuilder()
                                .setTaskID(t.getTaskID()).build());
                        assertEquals(TaskStatus.DONE, done.getStatus());
                    }
                } finally {
                    ch.shutdownNow();
                }
                return null;
            }));
        }
        for (int r = 0; r < METRIC_READERS; r++) {
            round2.add(pool.submit(() -> {
                ManagedChannel ch = newChannel();
                try {
                    BossServiceGrpc.BossServiceBlockingStub stub = BossServiceGrpc.newBlockingStub(ch);
                    startR2.await();
                    for (int i = 0; i < 300; i++) {
                        MetricsResponse m = stub.getMetrics(MetricsRequest.getDefaultInstance());
                        assertEquals(m.getTotalTasks(),
                                m.getOpenTasks() + m.getInProgressTasks() + m.getDoneTasks());
                    }
                } finally {
                    ch.shutdownNow();
                }
                return null;
            }));
        }
        for (Future<?> f : round2) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Beweis 2: nach der parallelen Bearbeitung ist jeder Task genau einmal vorhanden
        // und wurde konsistent abgehakt (jeder Task war genau einem Member zugeordnet).
        assertEquals(TOTAL_TASKS, store.size(), "Task-Anzahl hat sich unerwartet verändert");
        long done = store.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();
        assertEquals(TOTAL_TASKS, done, "Nicht alle Tasks wurden konsistent abgehakt");

        // Beweis 3: der finale Metrik-Snapshot deckt sich exakt mit der Datenhaltung.
        ManagedChannel ch = newChannel();
        try {
            MetricsResponse m = BossServiceGrpc.newBlockingStub(ch)
                    .getMetrics(MetricsRequest.getDefaultInstance());
            assertEquals(TOTAL_TASKS, m.getTotalTasks());
            assertEquals(TOTAL_TASKS, m.getDoneTasks());
            assertEquals(0, m.getOpenTasks());
        } finally {
            ch.shutdownNow();
        }
    }
}
