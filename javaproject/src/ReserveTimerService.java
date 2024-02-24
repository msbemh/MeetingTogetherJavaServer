import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReserveTimerService {
    public static ExecutorService executor;
    public ReserveTimerService() {
        // 스레드 풀 생성
        if(executor == null) {
            executor = Executors.newFixedThreadPool(5); // 최대 5개의 스레드를 가진 스레드 풀 생성
        }
    }

    public void startTask(Runnable runnable){
        executor.submit(runnable); // 작업을 스레드 풀에 제출
    }

    public boolean isShutdown(){
        return executor.isShutdown();
    }
}
