package app.service.schedule;

import app.service.cache.CacheService;
import app.service.persistence.SchedulePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScheduleService {

    private final SchedulePersistenceService schedulePersistenceService;
    private final CacheService cacheService;

    @Autowired
    public ScheduleService(SchedulePersistenceService schedulePersistenceService, CacheService cacheService) {
        this.schedulePersistenceService = schedulePersistenceService;
        this.cacheService = cacheService;
    }

    // ночь 00:00 с воскресенье на понедельник
    @Scheduled(cron = "0 0 0 * * 1", zone = "Europe/Moscow")
    public void swapWeekParity() {
        schedulePersistenceService.swapWeek();
        cacheService.clearAllCaches();
    }

}
