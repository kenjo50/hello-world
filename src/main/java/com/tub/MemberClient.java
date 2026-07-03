package com.tub;

import com.tub.common.Task;
import com.tub.common.TaskList;
import com.tub.member.CompleteTaskRequest;
import com.tub.member.GetMyTasksRequest;
import com.tub.member.MemberServiceGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;

public class MemberClient {
    static void main(String[] args) {
        ManagedChannel channel = Grpc.newChannelBuilder("localhost:8980", InsecureChannelCredentials.create()).build();
        MemberServiceGrpc.MemberServiceBlockingStub stub = MemberServiceGrpc.newBlockingStub(channel);
        GetMyTasksRequest request = GetMyTasksRequest.newBuilder()
                .setMemberID("anna")
                .build();

        TaskList ergebnis = stub.getMyTasks(request);
        for (Task t : ergebnis.getTasksList()) {
            System.out.println( t.getTaskID() + " Task ID "+ t.getTitle() +"Titel" + t.getStatus() + " Task Status");
        }
        if (ergebnis.getTasksCount() == 0) {
            System.out.println("Keine Tasks für diesen Member vorhanden.");
            channel.shutdown();
            return;
        }
        long id = ergebnis.getTasks(0).getTaskID();
        CompleteTaskRequest completeTaskRequest = CompleteTaskRequest.newBuilder()
                .setTaskID(id)
                .build();
        Task erledigt = stub.completeTask(completeTaskRequest);
        System.out.println("Abgehakt: " + erledigt.getTaskID() + " -> " + erledigt.getStatus());


        channel.shutdown();

    }
}
