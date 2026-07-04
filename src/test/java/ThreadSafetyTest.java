import com.tub.BossServiceImpl;
import com.tub.MemberServiceImpl;
import com.tub.boss.BossServiceGrpc;
import com.tub.boss.CreateTaskRequest;
import com.tub.boss.MetricsRequest;
import com.tub.boss.MetricsResponse;
import com.tub.common.Task;
import com.tub.common.TaskStatus;
import com.tub.member.CompleteTaskRequest;
import com.tub.member.MemberServiceGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Tests fuer den gRPC-Server (Aufgabe 1).
// Test 1 prueft die Grundfunktion, Test 2 zeigt, dass der Server thread safe ist.
public class ThreadSafetyTest {

    Server server;
    Map<Long, Task> store;   // die gemeinsame Datenhaltung
    int port;

    @BeforeEach
    void serverStarten() throws Exception {
        // Eine gemeinsame Map, die sich beide Services teilen (wie im TaskServer)
        store = new ConcurrentHashMap<>();
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new BossServiceImpl(store))
                .addService(new MemberServiceImpl(store))
                .build()
                .start();
        port = server.getPort(); // der Server sucht sich einen freien Port aus
    }

    @AfterEach
    void serverStoppen() {
        server.shutdownNow();
    }

    // Test 1: Grundfunktion - einen Task anlegen und wieder abhaken
    @Test
    void taskAnlegenUndAbhaken() {
        ManagedChannel channel = Grpc.newChannelBuilder(
                "localhost:" + port, InsecureChannelCredentials.create()).build();
        BossServiceGrpc.BossServiceBlockingStub bossStub = BossServiceGrpc.newBlockingStub(channel);
        MemberServiceGrpc.MemberServiceBlockingStub memberStub = MemberServiceGrpc.newBlockingStub(channel);

        // Boss legt einen Task an
        CreateTaskRequest request = CreateTaskRequest.newBuilder()
                .setTitle("Folien machen")
                .setMemberID("anna")
                .build();
        Task task = bossStub.createTask(request);

        // Member hakt denselben Task ab
        Task erledigt = memberStub.completeTask(
                CompleteTaskRequest.newBuilder().setTaskID(task.getTaskID()).build());

        assertEquals(TaskStatus.DONE, erledigt.getStatus());
        channel.shutdown();
    }

    // Test 2: Thread-Safety - mehrere Boss-Clients legen gleichzeitig Tasks an
    @Test
    void mehrereClientsGleichzeitig() throws Exception {
        int anzahlThreads = 5;
        int tasksProThread = 20;

        // Fuer jeden Client einen eigenen Thread anlegen
        Thread[] threads = new Thread[anzahlThreads];
        for (int i = 0; i < anzahlThreads; i++) {
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    // Jeder Thread ist ein eigener Client mit eigener Verbindung
                    ManagedChannel channel = Grpc.newChannelBuilder(
                            "localhost:" + port, InsecureChannelCredentials.create()).build();
                    BossServiceGrpc.BossServiceBlockingStub stub = BossServiceGrpc.newBlockingStub(channel);
                    for (int j = 0; j < tasksProThread; j++) {
                        CreateTaskRequest request = CreateTaskRequest.newBuilder()
                                .setTitle("Task")
                                .setMemberID("anna")
                                .build();
                        stub.createTask(request);
                    }
                    channel.shutdown();
                }
            });
        }

        // Alle Threads starten
        for (int i = 0; i < anzahlThreads; i++) {
            threads[i].start();
        }
        // Warten, bis alle Threads fertig sind
        for (int i = 0; i < anzahlThreads; i++) {
            threads[i].join();
        }

        // Es muessen genau 5 * 20 = 100 Tasks in der Map sein.
        // Waere der Server nicht thread safe, wuerden Tasks verloren gehen oder
        // IDs doppelt vergeben - dann waere die Anzahl kleiner als 100.
        int erwartet = anzahlThreads * tasksProThread;
        assertEquals(erwartet, store.size());

        // Zur Kontrolle auch ueber die Metriken-API pruefen
        ManagedChannel channel = Grpc.newChannelBuilder(
                "localhost:" + port, InsecureChannelCredentials.create()).build();
        BossServiceGrpc.BossServiceBlockingStub stub = BossServiceGrpc.newBlockingStub(channel);
        MetricsResponse metrics = stub.getMetrics(MetricsRequest.newBuilder().build());
        assertEquals(erwartet, metrics.getTotalTasks());
        channel.shutdown();
    }
}
