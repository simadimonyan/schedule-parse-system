package app.service.domain.version;

import app.repository.dao.ChangeRepository;
import app.repository.dao.ConfigRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.dao.TimeSlotRepository;
import app.repository.dao.VersionRepository;
import app.repository.dao.WorkScheduleRepository;
import app.repository.models.entity.Change;
import app.repository.models.entity.Config;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.TimeSlot;
import app.repository.models.entity.Version;
import app.repository.models.entity.WorkSchedule;
import app.service.cache.CacheService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Версии расписания: какая версия видна клиентам, куда идёт разбор файлов и как одно
 * становится другим.
 *
 * <p>Версий две по назначению. Активная — та, что отвечает на запросы; черновая — та, в
 * которую пишет разбор. Пока черновик наполняется, читатели работают с прежним расписанием и
 * промежуточного состояния не видят: файлы приходят по одному, и между первым и последним
 * расписание неполно.
 *
 * <p>Черновик заводится копией активной версии, а не пустым. Файл заменяет расписание только
 * своих групп — начни черновик с нуля, и публикация выкинула бы все группы, чьих файлов в
 * этот раз не загружали.
 *
 * <p>Старые версии не удаляются: на них откатываются {@link #activate(Long)}. Чистка — дело
 * администратора через {@link #discard(Long)}.
 */
@Slf4j
@Service
public class VersionService {

    private static final DateTimeFormatter NAME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));

    private final VersionRepository versionRepository;
    private final ScheduleRepository scheduleRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ChangeRepository changeRepository;
    private final WorkScheduleRepository workScheduleRepository;
    private final ConfigRepository configRepository;
    private final CacheService cacheService;

    public VersionService(
            VersionRepository versionRepository,
            ScheduleRepository scheduleRepository,
            TimeSlotRepository timeSlotRepository,
            ChangeRepository changeRepository,
            WorkScheduleRepository workScheduleRepository,
            ConfigRepository configRepository,
            CacheService cacheService
    ) {
        this.versionRepository = versionRepository;
        this.scheduleRepository = scheduleRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.changeRepository = changeRepository;
        this.workScheduleRepository = workScheduleRepository;
        this.configRepository = configRepository;
        this.cacheService = cacheService;
    }

    /**
     * Версия, которую отдают клиентам.
     *
     * <p>Если активной нет — заводится первая: на пустой базе расписание всё равно негде
     * взять, а читающий код не должен разбираться, случился ли уже первый импорт.
     */
    @Transactional
    public Version active() {
        List<Version> active = versionRepository.findActive();
        if (active.size() > 1) {
            log.warn("Активных версий больше одной: {} — берётся последняя",
                    active.stream().map(Version::getId).toList());
        }
        if (!active.isEmpty()) return active.getFirst();

        log.info("Активной версии нет — заводится первая");
        Version version = new Version();
        version.setName("Начальная версия");
        version.setIsActive(true);
        version.setIsDraft(false);
        return versionRepository.save(version);
    }

    /** Идентификатор активной версии — им ограничены все выборки расписания. */
    public Long activeId() {
        return active().getId();
    }

    /**
     * Версия, в которую идёт запись: открытый черновик, иначе активная.
     *
     * <p>Черновик здесь не заводится, и это намеренно. Заводить его на каждую загрузку нельзя:
     * файлы приходят по одному, черновик начинается копией активной версии, и десять файлов
     * означали бы десять полных копий расписания за один заход. Пока черновик никто не открыл,
     * разбор пишет прямо в активную версию — ровно как работало до появления версий.
     *
     * <p>Открытый черновик разворачивает поведение: загрузки копятся в нём, а клиенты остаются
     * на прежней активной версии, пока черновик не опубликуют.
     */
    @Transactional
    public Version writable() {
        List<Version> drafts = versionRepository.findDrafts();
        return drafts.isEmpty() ? active() : drafts.getFirst();
    }

    /** Идентификатор версии, открытой на запись. */
    public Long writableId() {
        return writable().getId();
    }

    /**
     * Версия, открытая на запись: существующий черновик или новый, снятый с активной.
     *
     * <p>Копия делается один раз на черновик, а не на каждый файл: до публикации все загрузки
     * ложатся в одну и ту же версию, заменяя расписание своих групп.
     */
    @Transactional
    public Version draft() {
        List<Version> drafts = versionRepository.findDrafts();
        if (!drafts.isEmpty()) return drafts.getFirst();

        Version source = active();

        Version draft = new Version();
        draft.setName("Черновик от " + NAME_FORMAT.format(Instant.now()));
        draft.setIsActive(false);
        draft.setIsDraft(true);
        draft = versionRepository.save(draft);

        copy(source, draft);
        log.info("Заведён черновик {} копией версии {}", draft.getId(), source.getId());
        return draft;
    }

    /**
     * Делает версию активной: ею отвечают на запросы, черновиком она быть перестаёт.
     *
     * <p>Тем же вызовом делается откат — старая версия из таблицы не исчезала, и вернуть её
     * значит снова назначить активной.
     */
    @Transactional
    public Version activate(Long versionId) {
        Version target = versionRepository.findByIdAndIsDeletedFalse(versionId)
                .orElseThrow(() -> new EntityNotFoundException("Версия " + versionId + " не найдена"));

        for (Version version : versionRepository.findActive()) {
            if (version.getId().equals(target.getId())) continue;
            version.setIsActive(false);
            versionRepository.save(version);
        }

        target.setIsActive(true);
        target.setIsDraft(false);
        Version published = versionRepository.save(target);

        // выдача целиком построена на прежней версии: в кеше лежат и пары, и собранные из
        // них списки групп, курсов и уровней
        cacheService.clearAllCaches();

        log.info("Активной стала версия {} ({})", published.getId(), published.getName());
        return published;
    }

    /** Версии сервиса, новые сверху. */
    public List<Version> list() {
        return versionRepository.findAllAlive();
    }

    /**
     * Удаляет версию вместе со всем, что к ней относится.
     *
     * <p>Активную не трогает: снести то, чем сейчас отвечают на запросы, — не операция, а
     * авария. Откатитесь на другую версию и удаляйте эту.
     */
    @Transactional
    public void discard(Long versionId) {
        Version version = versionRepository.findByIdAndIsDeletedFalse(versionId)
                .orElseThrow(() -> new EntityNotFoundException("Версия " + versionId + " не найдена"));

        if (Boolean.TRUE.equals(version.getIsActive())) {
            throw new IllegalStateException("Активную версию удалить нельзя: сначала переключитесь на другую");
        }

        // порядок обратный ссылкам: изменения смотрят на пары и слоты, поэтому уходят
        // первыми — иначе внешний ключ не даст снести то, на что они указывают
        changeRepository.deleteByVersion(versionId);
        scheduleRepository.deleteByVersion(versionId);
        timeSlotRepository.deleteByVersion(versionId);
        workScheduleRepository.deleteByVersion(versionId);
        configRepository.deleteByVersion(versionId);

        version.setIsDeleted(true);
        version.setIsDraft(false);
        versionRepository.save(version);

        log.info("Версия {} удалена", versionId);
    }

    /**
     * Переносит содержимое версии в другую.
     *
     * <p>Порядок задан ссылками: сначала слоты, потом пары, потом изменения, которые смотрят
     * и туда, и туда. Идентификаторы при копировании меняются, поэтому старые сопоставляются
     * с новыми по карте — иначе строки черновика указывали бы на чужую версию, и правка в
     * черновике меняла бы опубликованное расписание.
     */
    private void copy(Version source, Version target) {
        Map<Long, TimeSlot> slots = new HashMap<>();
        List<TimeSlot> savableSlots = new ArrayList<>();
        for (TimeSlot slot : timeSlotRepository.findGrid(source.getId())) {
            TimeSlot copy = new TimeSlot();
            copy.setVersion(target);
            copy.setLessonCount(slot.getLessonCount());
            copy.setWeekCount(slot.getWeekCount());
            copy.setDayWeek(slot.getDayWeek());
            copy.setTimeRange(slot.getTimeRange());
            copy.setStartedAt(slot.getStartedAt());
            copy.setFinishedAt(slot.getFinishedAt());
            copy.setIsDeleted(slot.getIsDeleted());
            savableSlots.add(copy);
            slots.put(slot.getId(), copy);
        }
        timeSlotRepository.saveAll(savableSlots);

        // пары копируются раньше изменений: изменение ссылается на пару, и без новой пары
        // ему не на что было бы указать
        Map<Long, Schedule> pairs = new HashMap<>();
        List<Schedule> savableSchedule = new ArrayList<>();
        for (Schedule schedule : scheduleRepository.findAllByVersion(source.getId())) {
            Schedule copy = new Schedule();
            copy.setVersion(target);
            copy.setSlot(schedule.getSlot() == null ? null : slots.get(schedule.getSlot().getId()));
            copy.setTeacherMasterId(schedule.getTeacherMasterId());
            copy.setGroupMasterId(schedule.getGroupMasterId());
            copy.setLessonCount(schedule.getLessonCount());
            copy.setLessonType(schedule.getLessonType());
            copy.setLessonName(schedule.getLessonName());
            copy.setAuditory(schedule.getAuditory());
            copy.setDayWeek(schedule.getDayWeek());
            copy.setPinnedDate(schedule.getPinnedDate());
            copy.setWeekCount(schedule.getWeekCount());
            copy.setTimePeriod(schedule.getTimePeriod());
            copy.setEiosLink(schedule.getEiosLink());
            copy.setIsDeleted(schedule.getIsDeleted());
            savableSchedule.add(copy);
            pairs.put(schedule.getId(), copy);
        }
        scheduleRepository.saveAll(savableSchedule);

        List<Change> savableChanges = new ArrayList<>();
        for (Change change : changeRepository.findAllByVersion(source.getId())) {
            Change copy = new Change();
            copy.setVersion(target);
            copy.setSlot(change.getSlot() == null ? null : slots.get(change.getSlot().getId()));
            copy.setSchedule(change.getSchedule() == null ? null : pairs.get(change.getSchedule().getId()));
            copy.setChangeType(change.getChangeType());
            copy.setChangeDate(change.getChangeDate());
            copy.setSubjectMasterId(change.getSubjectMasterId());
            copy.setTeacherMasterId(change.getTeacherMasterId());
            copy.setGroupMasterId(change.getGroupMasterId());
            copy.setAuditoriumMasterId(change.getAuditoriumMasterId());
            copy.setDepartmentMasterId(change.getDepartmentMasterId());
            copy.setPayload(change.getPayload());
            copy.setCreatedAt(change.getCreatedAt());
            copy.setIsDeleted(change.getIsDeleted());
            savableChanges.add(copy);
        }
        changeRepository.saveAll(savableChanges);

        List<WorkSchedule> savableWork = new ArrayList<>();
        for (WorkSchedule work : workScheduleRepository.findAllByVersion(source.getId())) {
            WorkSchedule copy = new WorkSchedule();
            copy.setVersion(target);
            copy.setTeacherMasterId(work.getTeacherMasterId());
            copy.setDayWeek(work.getDayWeek());
            copy.setStartedAt(work.getStartedAt());
            copy.setFinishedAt(work.getFinishedAt());
            copy.setIsDeleted(work.getIsDeleted());
            savableWork.add(copy);
        }
        workScheduleRepository.saveAll(savableWork);

        List<Config> savableConfig = new ArrayList<>();
        for (Config config : configRepository.findAllByVersion(source.getId())) {
            Config copy = new Config();
            copy.setVersion(target);
            copy.setTag(config.getTag());
            copy.setKey(config.getKey());
            copy.setValue(config.getValue());
            copy.setCreatedAt(config.getCreatedAt());
            copy.setUpdatedAt(config.getUpdatedAt());
            savableConfig.add(copy);
        }
        configRepository.saveAll(savableConfig);

        log.info("В версию {} скопировано: пар {}, слотов {}, изменений {}, графиков занятости {}, настроек {}",
                target.getId(), savableSchedule.size(), savableSlots.size(),
                savableChanges.size(), savableWork.size(), savableConfig.size());
    }

}
