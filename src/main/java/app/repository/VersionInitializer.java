package app.repository;

import app.repository.dao.ScheduleRepository;
import app.repository.models.entity.Version;
import app.service.domain.version.VersionService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Заводит первую версию и подбирает под неё пары, оставшиеся от бесверсионной базы.
 *
 * <p>Версии появились позже самого расписания, и в рабочей базе лежат строки без
 * {@code version_id}. Выборки ограничены версией, поэтому такие пары просто перестали бы
 * находиться — расписание пропало бы целиком при первом же старте после обновления.
 *
 * <p>Работает один раз: после первого прохода строк без версии не остаётся, и повторный
 * запуск ничего не делает. Новая база сюда тоже попадает — там просто нечего подбирать,
 * версия заводится пустой.
 *
 * <p>Идёт первым: {@link SlotBackfill} разбирает те же строки, а слот заводится внутри
 * версии — без неё ему не в чем появиться.
 */
@Slf4j
@Order(1)
@Component
public class VersionInitializer implements ApplicationRunner {

    private final VersionService versionService;
    private final ScheduleRepository scheduleRepository;

    public VersionInitializer(VersionService versionService, ScheduleRepository scheduleRepository) {
        this.versionService = versionService;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Version active = versionService.active();

        if (scheduleRepository.countOrphans() == 0) {
            log.info("Активная версия расписания: {} ({})", active.getId(), active.getName());
            return;
        }

        int adopted = scheduleRepository.adopt(active.getId());
        log.info("Версия {} приняла {} пар, загруженных до появления версий", active.getId(), adopted);
    }

}
