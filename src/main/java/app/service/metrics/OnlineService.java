package app.service.metrics;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
@Service
public class OnlineService {

    private final ConcurrentHashMap<UUID, Long> online = new ConcurrentHashMap<>();

    @Async
    public void heartbeat(UUID id) {
        Long now = System.currentTimeMillis();
        online.put(id, now);
    }

    @Scheduled(fixedRate = 300000L)
    public void autoClear() {
        Long now = System.currentTimeMillis();
        for (UUID key : online.keySet()) {
            if (now - online.get(key) > 120000L) {
                online.remove(key);
            }
        }
    }

}
