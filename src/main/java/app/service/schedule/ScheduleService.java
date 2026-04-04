package app.service.schedule;

import app.service.cache.CacheService;
import app.service.max.MaxService;
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
    private final MaxService maxService;

    @Autowired
    public ScheduleService(SchedulePersistenceService schedulePersistenceService, CacheService cacheService, MaxService maxService) {
        this.schedulePersistenceService = schedulePersistenceService;
        this.cacheService = cacheService;
        this.maxService = maxService;
    }

    // ночь 00:00 с воскресенье на понедельник
    @Scheduled(cron = "0 0 0 * * 1", zone = "Europe/Moscow")
    public void swapWeekParity() {
        schedulePersistenceService.swapWeek();
        cacheService.clearAllCaches();
    }

    // один раз в 08:00 кроме воскресенья
    @Scheduled(cron = "0 0 8 * * 1-6", zone = "Europe/Moscow")
    public void loadAndPersistGroups() {
        maxService.loadAndPersistGroups();
    }

    // с 8 до 8 каждые 15 минут кроме воскресенья
    @Scheduled(cron = "0 15/15 8-19 * * 1-6", zone = "Europe/Moscow")
    public void loadAndPersistSchedule() {
        maxService.loadAndPersistSchedule();
    }

}
