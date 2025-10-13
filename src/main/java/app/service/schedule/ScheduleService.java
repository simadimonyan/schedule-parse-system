package app.service.schedule;

import app.service.persistence.PersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScheduleService {

    private final PersistenceService persistenceService;

    @Autowired
    public ScheduleService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    // ночь 00:00 с воскресенье на понедельник
    @Scheduled(cron = "0 0 0 * * 1", zone = "Europe/Moscow")
    public void swapWeekParity() {
        persistenceService.swapWeek();
    }

}
