package app.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Schedule-Async-");
        executor.initialize();
        return executor;
    }

    /**
     * Отдельный однопоточный пул под полный обход всех групп: обход идёт часами
     * (между группами пауза), и занимать под него поток общего пула нельзя.
     * Очередь на один элемент — второй обход просто не встанет в очередь,
     * повторный запуск отсекается ещё и флагом в MaxService.
     */
    @Bean(name = "sweepExecutor")
    public Executor sweepExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("Schedule-Sweep-");
        executor.initialize();
        return executor;
    }

}