package com.tub;

import com.tub.boss.BossServiceGrpc;
import com.tub.boss.CreateTaskRequest;
import com.tub.common.Task;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

public class BossClient {

    public static void main(String[] args) {
        // 1. Verbindung zum Server aufbauen (localhost = selber Rechner, Port 8980)
        ManagedChannel channel = Grpc.newChannelBuilder(
                "localhost:8980", InsecureChannelCredentials.create()).build();

        // 2. Den Stub holen – der Chef-Stellvertreter
        BossServiceGrpc.BossServiceBlockingStub stub =
                BossServiceGrpc.newBlockingStub(channel);

        // 3. Eine Task-Erstellung vorbereiten
        CreateTaskRequest request = CreateTaskRequest.newBuilder()
                .setTitle("Folien fertig machen")
                .setDescription("bis Freitag")
                .setMemberID("anna")
                .build();

        // 4. Den Server aufrufen – DAS ist der Netzwerk-Aufruf!
        Task ergebnis = stub.createTask(request);

        // 5. Antwort anzeigen
        System.out.println("Task erstellt!");
        System.out.println("  ID:     " + ergebnis.getTaskID());
        System.out.println("  Titel:  " + ergebnis.getTitle());
        System.out.println("  Status: " + ergebnis.getStatus());

        // 6. Verbindung schließen
        channel.shutdown();
    }
}