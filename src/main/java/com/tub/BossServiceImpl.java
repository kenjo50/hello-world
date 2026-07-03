package com.tub;

import com.tub.boss.*;        // CreateTaskRequest, MetricsRequest, MetricsResponse, BossServiceGrpc
import com.tub.common.Task;
import com.tub.common.TaskStatus;
import io.grpc.stub.StreamObserver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class BossServiceImpl extends BossServiceGrpc.BossServiceImplBase {

    // Die Datenhaltung – der gemeinsame Aktenschrank
    private Map<Long, Task> tasks;
    private AtomicLong nextId = new AtomicLong(1);

    @Override
    public void createTask(CreateTaskRequest request,
                           StreamObserver<Task> responseObserver) {
        // 1. Task zusammenbauen (deine Logik von vorhin)
        long id = nextId.getAndIncrement();

        Task task = Task.newBuilder()
                .setTaskID(id)
                .setTitle(request.getTitle())
                .setDescription(request.getDescription())
                .setMemberID(request.getMemberID())
                .setStatus(TaskStatus.OPEN)
                .build();

        // 2. In den Aktenschrank legen
        tasks.put(id, task);

        // 3. Antwort an den Client schicken (statt return!)
        responseObserver.onNext(task);
        responseObserver.onCompleted();
    }
    @Override
    public void getMetrics(MetricsRequest request, StreamObserver<MetricsResponse> responseObserver) {
        int openTasks = 0;
        int doneTasks = 0;
        int totalTasks = 0;
        int inProgress = 0;

        for (Task t : tasks.values()) {
            if(t.getStatus().equals(TaskStatus.DONE)){
                doneTasks++;
            }
            if(t.getStatus().equals(TaskStatus.OPEN)){
                openTasks++;
            }
            if(t.getStatus().equals(TaskStatus.IN_PROGRESS)){
                inProgress++;
            }

        }
        MetricsResponse response = MetricsResponse.newBuilder()
                .setTotalTasks(openTasks+doneTasks+inProgress)
                .setOpenTasks(openTasks)
                .setDoneTasks(doneTasks)
                .setInProgressTasks(inProgress)
                        .build();
      responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
    public BossServiceImpl(Map<Long,Task> tasks) {
        this.tasks = tasks;
    }
}