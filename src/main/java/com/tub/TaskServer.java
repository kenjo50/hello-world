package com.tub;

import com.tub.common.Task;
import com.tub.common.TaskList;
import com.tub.member.GetMyTasksRequest;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskServer {

    static final int PORT = 8980;
    static void main(String[] args) throws IOException,InterruptedException {
        Map<Long,Task> tasks = new ConcurrentHashMap<>();
        Server server = Grpc.newServerBuilderForPort(PORT, InsecureServerCredentials.create())
                .addService(new BossServiceImpl(tasks))
                .addService(new MemberServiceImpl(tasks))
                .build()
                .start();
        System.out.println("Server läuft auf Port " + PORT);
        server.awaitTermination();   // hält den Server am Laufen

    }

}
