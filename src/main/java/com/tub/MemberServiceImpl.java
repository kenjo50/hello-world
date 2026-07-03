package com.tub;

import com.tub.common.Task;
import com.tub.common.TaskList;
import com.tub.common.TaskStatus;
import com.tub.member.CompleteTaskRequest;
import com.tub.member.GetMyTasksRequest;
import com.tub.member.MemberServiceGrpc;
import io.grpc.stub.StreamObserver;

import java.util.Map;
import java.util.stream.Stream;

public class MemberServiceImpl extends MemberServiceGrpc.MemberServiceImplBase{
    private final Map<Long, Task> tasks;

    public MemberServiceImpl(Map<Long, Task> tasks) {
        this.tasks = tasks;
    }
    @Override
    public void getMyTasks(GetMyTasksRequest request, StreamObserver<TaskList> responseObserver){
        String memberID = request.getMemberID();
        TaskList.Builder taskListBuilder = TaskList.newBuilder();
            for (Task t :tasks.values() ){
                if (memberID.equals(t.getMemberID())){
                    taskListBuilder.addTasks(t);
                }
            }
            responseObserver.onNext(taskListBuilder.build());
            responseObserver.onCompleted();
    }
    @Override
    public void completeTask(CompleteTaskRequest request, StreamObserver<Task> responseObserver){
        long taskID = request.getTaskID();
        // Atomares Read-Modify-Write: verhindert verlorene Updates bei parallelen Zugriffen
        // auf denselben Task (im Gegensatz zu get(..) + put(..)).
        Task neueTask = tasks.computeIfPresent(taskID,
                (id, alteTask) -> alteTask.toBuilder()
                        .setStatus(TaskStatus.DONE)
                        .build());
        if (neueTask == null) {
            // Task existiert nicht -> sauberer gRPC-Fehler statt NullPointerException
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription("Task " + taskID + " existiert nicht")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(neueTask);
        responseObserver.onCompleted();
    }




}
