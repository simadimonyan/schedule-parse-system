package app.service.domain.persistence;

import app.repository.dao.ChangeRepository;
import app.repository.dao.ConfigRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.dao.VersionRepository;
import app.repository.models.dto.api.schedule.ScheduleBatch;
import app.repository.models.dto.directory.Group;
import app.repository.models.dto.event.ScheduleEvent;
import app.repository.models.dto.directory.Teacher;
import app.repository.models.dto.mappers.ScheduleMapper;
import app.repository.models.entity.Change;
import app.repository.models.entity.Config;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Version;
import app.service.domain.excel.ExcelService;
import app.service.domain.version.VersionService;
import app.service.infra.MasterDirectoryService;
import app.service.infra.MasterSyncService;
import app.service.metrics.StatService;
import app.service.storage.StorageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Расписание в терминах запросов клиента.
 *
 * <p>Клиент спрашивает по имени — группа «ПИ-301», преподаватель «Иванов И.И.», — а в базе
 * лежат идентификаторы справочника мастер-сервиса. Поэтому у каждого чтения два шага:
 * перевести имя в идентификатор ({@link MasterDirectoryService}), а потом достроить ответ
 * названиями обратно. Своих таблиц у групп и преподавателей больше нет: справочник
 * принадлежит мастеру, и вторая его копия означала бы две расходящиеся версии одних данных.
 *
 * <p>Списки групп, курсов и уровней собираются из самих пар: группы сервиса — это ровно те,
 * чьё расписание он хранит.
 */
@Slf4j
@Service
public class SchedulePersistenceService {

    private final ExcelService excelService;
    private final StorageService storageService;
    private final MasterDirectoryService masterDirectoryService;
    private final StatService statService;
    private final MasterSyncService masterSyncService;
    private final VersionService versionService;

    private final VersionRepository versionRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChangeRepository changeRepository;
    private final ConfigRepository configRepository;

    private final ScheduleMapper scheduleMapper;
    private final ScheduleWriter scheduleWriter;
    private final ApplicationEventPublisher eventPublisher;


    @Autowired
    public SchedulePersistenceService(
            ExcelService excelService,
            StorageService storageService,
            VersionRepository versionRepository,
            ScheduleRepository scheduleRepository,
            ChangeRepository changeRepository,
            ConfigRepository configRepository,
            StatService statService,
            MasterSyncService masterSyncService,
            MasterDirectoryService masterDirectoryService,
            ScheduleWriter scheduleWriter,
            VersionService versionService,
            ScheduleMapper scheduleMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.excelService = excelService;
        this.storageService = storageService;
        this.versionRepository = versionRepository;
        this.scheduleRepository = scheduleRepository;
        this.changeRepository = changeRepository;
        this.configRepository = configRepository;
        this.statService = statService;
        this.masterSyncService = masterSyncService;
        this.masterDirectoryService = masterDirectoryService;
        this.scheduleWriter = scheduleWriter;
        this.versionService = versionService;
        this.scheduleMapper = scheduleMapper;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(value = "groups", key = "'name:' + #name")
    public Group getGroup(String name) {
        Long masterId = masterDirectoryService.groupId(name);
        if (masterId == null) throw new EntityNotFoundException(String.format("Группа %s не найдена", name));

        Group group = masterDirectoryService.groups(List.of(masterId)).get(masterId);
        if (group == null) throw new EntityNotFoundException(String.format("Группа %s не найдена", name));
        return group;
    }

    @Cacheable(value = "groups", key = "'search:' + #course + ':' + #level")
    public List<Group> getGroups(Integer course, String level) {
        return masterDirectoryService.knownGroups().stream()
                .filter(group -> Objects.equals(group.getCourse(), course))
                .filter(group -> level == null || level.equals(group.getLevel()))
                .sorted(Comparator.comparing(Group::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Cacheable("levels")
    public List<String> getLevels(Integer course) {
        return masterDirectoryService.knownGroups().stream()
                .filter(group -> Objects.equals(group.getCourse(), course))
                .map(Group::getLevel)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Cacheable("courses")
    public List<Integer> getCourses() {
        return masterDirectoryService.knownGroups().stream()
                .map(Group::getCourse)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    // ключ по умолчанию складывается только из аргументов, без имени метода: расписание
    // группы и расписание преподавателя с одинаковыми параметрами попали бы в одну ячейку,
    // а строка приходит из запроса клиента — совпадение подбирается намеренно
    @Cacheable(value = "schedule", key = "'group:' + #name + ':' + #dayWeek + ':' + #date + ':' + #weekCount")
    public ScheduleView getGroupSchedule(String name, String dayWeek, String date, Integer weekCount) {
        statService.addGroupView(name);

        Long groupMasterId = masterDirectoryService.groupId(name);
        if (groupMasterId == null) {
            log.info("Группа {} не найдена в справочнике — расписание пусто", name);
            return ScheduleView.empty();
        }

        Long versionId = versionService.activeId();

        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByGroup(versionId, groupMasterId, weekCount)
                    // фильтры для заочки
                    .stream().filter(s -> matchesWeek(s, date)).toList();
        else
            schedule = scheduleRepository.findAllByGroupAndDay(versionId, groupMasterId, dayWeek, weekCount)
                    .stream().filter(s -> matchesDay(s, date)).toList();

        return new ScheduleView(
                describe(schedule),
                changeRepository.findStandaloneByGroup(versionId, groupMasterId));
    }

    /**
     * Расписание пачкой: пары приходят готовыми полями, а не файлом.
     *
     * <p>Сетка недели заводится по ходу. Слота, в который встаёт пара, может ещё не быть —
     * группу заводят и до первой загрузки файла, — и ждать его неоткуда, поэтому недостающие
     * ячейки создаёт {@link SlotResolver} внутри {@link ScheduleWriter}. Ключ у него тот же,
     * что и у разбора файла: день, чётность, номер пары и время. Значит, вторая пачка на то же
     * место переиспользует заведённый слот, а не положит рядом копию, и загруженный потом файл
     * встанет в ту же сетку.
     *
     * <p>Заменяется ячейка, а не расписание группы: редактор присылает одну пару и ждёт, что
     * встанет ровно она. Замена по группе — дело загрузки файла, где пачка и есть вся неделя.
     *
     * <p>Версию называет клиент, но писать он может только в открытую на запись — в черновик,
     * пока тот есть, иначе в активную. Пришедший id сверяется с ней: молча положить правку в
     * опубликованный снимок хуже, чем отказать, — расписание уехало бы клиентам сразу.
     *
     * <p>Неразрешённая ссылка — отказ с объяснением, а не пустой ответ с кодом 200: диспетчер
     * увидел бы пару в сетке, а после перезагрузки её бы не стало.
     */
    public ScheduleView updateAndSetSchedule(ScheduleBatch batch) {
        if (batch == null || batch.schedule() == null || batch.schedule().isEmpty()) {
            log.info("Пустая пачка расписания — писать нечего");
            return ScheduleView.empty();
        }

        List<ScheduleBatch.ScheduleUnitRequest> requests = batch.schedule();

        // Группа и преподаватель приходят идентификаторами справочника мастера — это и есть
        // те самые master id, по которым хранится пара. Своих таблиц у справочника здесь нет,
        // поэтому запись только проверяется на существование у мастера — и сразу на всю
        // пачку: поштучный вопрос превратил бы неделю расписания в сотню походов в Redis.
        Map<Long, Group> groups = masterDirectoryService.groups(
                requests.stream().map(ScheduleBatch.ScheduleUnitRequest::group).filter(Objects::nonNull).toList());
        Map<Long, Teacher> teachers = masterDirectoryService.teachers(
                requests.stream().map(ScheduleBatch.ScheduleUnitRequest::teacher).filter(Objects::nonNull).toList());

        Version target = versionService.writable();
        List<ScheduleBatch.ScheduleBatchUnit> batchUnits = new ArrayList<>();

        for (ScheduleBatch.ScheduleUnitRequest item : requests) {
            if (!Objects.equals(item.version(), target.getId())) {
                // разные ответы на разные беды: несуществующую версию клиент исправит в
                // запросе, а закрытую на запись — открыв черновик
                if (item.version() == null || versionRepository.findByIdAndIsDeletedFalse(item.version()).isEmpty()) {
                    throw new EntityNotFoundException(
                            String.format("Версия %s не найдена — пачка не записана", item.version()));
                }
                throw new IllegalStateException(String.format(
                        "Версия %s закрыта на запись: правки идут в версию %s (%s). "
                                + "Откройте черновик или выберите её на экране",
                        item.version(), target.getId(),
                        Boolean.TRUE.equals(target.getIsDraft()) ? "черновик" : "активная"));
            }

            Group group = item.group() == null ? null : groups.get(item.group());
            if (group == null) {
                throw new EntityNotFoundException(
                        String.format("Группа %s не найдена в справочнике — пачка не записана", item.group()));
            }

            // преподаватель необязателен: в файле он тоже стоит не у всякой пары
            Teacher teacher = item.teacher() == null ? null : teachers.get(item.teacher());
            if (item.teacher() != null && teacher == null) {
                throw new EntityNotFoundException(String.format(
                        "Преподаватель %s не найден в справочнике — пачка не записана", item.teacher()));
            }

            batchUnits.add(new ScheduleBatch.ScheduleBatchUnit(
                    target,
                    group,
                    item.group(),
                    item.dayWeek(),
                    item.timePeriod(),
                    item.weekCount(),
                    item.lessonCount(),
                    item.lessonType(),
                    item.lessonName(),
                    teacher,
                    item.teacher(),
                    item.pinnedDate(),
                    item.auditory(),
                    item.eiosLink()
            ));
        }

        List<Schedule> schedule = scheduleMapper.toSchedule(batchUnits);
        scheduleWriter.upsert(target, schedule);

        return new ScheduleView(describe(schedule), List.of());
    }

    // без префикса запрос кафедры с названием, совпавшим с ФИО, вернул бы List<Teacher>
    // там, где ожидается Teacher, — ClassCastException вместо ответа
    @Cacheable(value = "teachers", key = "'label:' + #label")
    public Teacher getTeacher(String label) {
        Long masterId = masterDirectoryService.teacherId(label);
        if (masterId == null) throw new EntityNotFoundException(String.format("Преподаватель %s не найден", label));

        Teacher teacher = masterDirectoryService.teachers(List.of(masterId)).get(masterId);
        if (teacher == null) throw new EntityNotFoundException(String.format("Преподаватель %s не найден", label));
        return teacher;
    }

    /**
     * Преподаватели сервиса.
     *
     * <p>Кафедра в представлении преподавателя у мастера не возвращается, поэтому отбор по
     * ней даёт пустую выдачу — ровно как и раньше, когда колонка была, но её никто не
     * заполнял. Без параметра возвращаются все, у кого есть расписание.
     */
    @Cacheable(value = "teachers", key = "'department:' + #department")
    public List<Teacher> getTeachers(String department) {
        if (department != null) {
            log.info("Отбор преподавателей по кафедре не поддерживается справочником: {}", department);
            return new ArrayList<>();
        }
        return masterDirectoryService.knownTeachers().stream()
                .sorted(Comparator.comparing(Teacher::getLabel, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "schedule", key = "'teacher:' + #label + ':' + #dayWeek + ':' + #date + ':' + #weekCount")
    public ScheduleView getTeacherSchedule(String label, String dayWeek, String date, Integer weekCount) {
        statService.addTeacherView(label);

        Long teacherMasterId = masterDirectoryService.teacherId(label);
        if (teacherMasterId == null) {
            log.info("Преподаватель {} не найден в справочнике — расписание пусто", label);
            return ScheduleView.empty();
        }

        Long versionId = versionService.activeId();

        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByTeacher(versionId, teacherMasterId, weekCount)
                    // фильтры для заочки
                    .stream().filter(s -> matchesWeek(s, date)).toList();
        else
            schedule = scheduleRepository.findAllByTeacherAndDay(versionId, teacherMasterId, dayWeek, weekCount)
                    .stream().filter(s -> matchesDay(s, date)).toList();

        return new ScheduleView(
                describe(schedule),
                changeRepository.findStandaloneByTeacher(versionId, teacherMasterId));
    }

    @Cacheable("configs")
    public Config getConfig(String key) {
        Config pair = configRepository.findAllByKey(key).orElse(null);
        if (pair == null) throw new EntityNotFoundException("База не содержит конфигурации с ключем: " + key);
        return pair;
    }

    /**
     * Чётность недели, показываемая просмотрщику.
     *
     * <p>Ключа в базе может не быть: заводит его только переключение чётности, и на свежей
     * базе до первого переключения его нет ни у кого. Отсутствие — это не ошибка, а «идёт
     * первая неделя»: значение проставляется здесь же, чтобы переключение дальше работало
     * от известного, а не заводило ключ заново.
     */
    public int weekCount() {
        Config pair = configRepository.findAllByKey("weekCount").orElse(null);
        if (pair != null) return Integer.parseInt(pair.getValue());

        log.info("Инициализация параметра: weekCount - присвоено значение 1");
        setConfig("weekCount", "1");
        return 1;
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void swapWeek() {
        try {
            Config pair = configRepository.findAllByKey("weekCount").orElse(null);
            String value;

            if (pair == null) {
                log.info("Инициализация параметра: weekCount - присвоено значение 1");
                value = "1";
            }
            else if (pair.getValue().equals("1")) {
                log.info("Переключение четности недели: старое значение = {}, новое значение = 2", pair.getValue());
                value = "2";
            }
            else {
                log.info("Переключение четности недели: старое значение = {}, новое значение = 1", pair.getValue());
                value = "1";
            }

            setConfig("weekCount", value);

            // расписание не менялось — сменилась показываемая половина. Подписчику этого
            // достаточно, чтобы перечитать выдачу, и не нужно версии: чётность одна на сервис
            eventPublisher.publishEvent(ScheduleEvent.weekSwapped(Integer.valueOf(value)));
        }
        catch (Exception e) {
            log.error("Ошибка при переключении четности недели", e);
        }
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void setConfig(String key, String value) {
        Config pair = configRepository.findAllByKey(key).orElse(null);
        if (pair == null) {
            Config savable = new Config();
            savable.setKey(key);
            savable.setValue(value);
            configRepository.save(savable);
            return;
        }
        pair.setValue(value);
        configRepository.save(pair);
    }

    /**
     * Разбирает файл расписания из хранилища.
     *
     * <p>Имя принимается и с бакетом впереди, как присылает вебхук MinIO, и без него, как
     * приходит из запроса на разбор — обрезает лишнее само хранилище. В {@code parseWorkbook}
     * уходит имя целиком: по нему определяются форма обучения, курс и магистратура, и
     * обрезанный префикс тут ничего не портит.
     */
    @Async
    public void persistSchedule(String fileName) throws IOException {
        InputStream excel = storageService.getObjectByName(fileName);
        if (excel == null) {
            log.error("Файл {} не получен из хранилища — расписание не разобрано", fileName);
            return;
        }
        persistSchedule(excelService.parseWorkbook(fileName, excel));
    }

    /**
     * Какой файл разбирать: названный или последний загруженный.
     *
     * @return имя файла или {@code null}, если названного в бакете нет либо бакет пуст
     */
    public String resolveFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return storageService.latestObjectName();

        String name = fileName.trim();
        if (!storageService.exists(name)) {
            log.error("В бакете расписания нет файла {}", name);
            return null;
        }
        return name;
    }

    /**
     * Сохраняет разобранный файл.
     *
     * <p>Порядок шагов задан ссылками: группа и преподаватель хранятся в паре
     * идентификатором справочника, поэтому обмен с мастером идёт до вставки, а не после
     * коммита, как было при своих таблицах. Транзакция при этом остаётся только на самой
     * вставке ({@link ScheduleWriter}) — сетевые вызовы не должны держать соединение с базой.
     *
     * <p>Метод рассчитан на фоновый поток: его вызывает {@code @Async}-разбор файла, и все
     * походы в мастер-сервис происходят там же, не задерживая ответ вебхуку MinIO.
     */
    public void persistSchedule(List<Schedule> parsed) {
        if (parsed == null || parsed.isEmpty()) return;

        Map<String, Long> groupIds = masterSyncService.linkGroups(
                parsed.stream().map(Schedule::getGroup).filter(Objects::nonNull).toList());

        Map<String, Long> teacherIds = masterSyncService.linkTeachers(
                parsed.stream()
                        .map(Schedule::getTeacher)
                        .filter(Objects::nonNull)
                        .map(Teacher::getLabel)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<Schedule> savable = new ArrayList<>();
        Set<String> unresolved = new LinkedHashSet<>();

        for (Schedule schedule : parsed) {
            String groupName = schedule.getGroup() == null ? null : trimmed(schedule.getGroup().getName());
            Long groupMasterId = groupName == null ? null : groupIds.get(groupName);

            // пара без группы висит в воздухе: спросить её будет некому, потому что все
            // запросы расписания идут от группы или от преподавателя
            if (groupMasterId == null) {
                unresolved.add(groupName == null ? "без группы" : groupName);
                continue;
            }

            schedule.setGroupMasterId(groupMasterId);

            String label = schedule.getTeacher() == null ? null : trimmed(schedule.getTeacher().getLabel());
            schedule.setTeacherMasterId(label == null ? null : teacherIds.get(label));

            savable.add(schedule);
        }

        if (!unresolved.isEmpty()) {
            log.error("Расписание не сохранено для групп {}: справочник не выдал идентификатор. "
                    + "Записи появятся после повторной загрузки файла", unresolved);
        }

        scheduleWriter.replace(savable);

        // предмет и аудитория хранятся строками в самой паре: отдельных сущностей у них нет,
        // поэтому в справочник уходят уникальные значения из разобранного файла. Порядок
        // обратный группам — тип занятия считается по уже сохранённым парам
        masterSyncService.syncSubjects(unique(savable, Schedule::getLessonName));
        masterSyncService.syncAuditoriums(unique(savable, Schedule::getAuditory));
    }

    /**
     * Достраивает пары названиями из справочника.
     *
     * <p>В базе от группы и преподавателя остались одни идентификаторы, а ответ API отдаёт
     * названия. Справочник читается двумя пачками на весь список: поштучное чтение
     * превратило бы выдачу недельного расписания в сотню походов в Redis.
     */
    private List<Schedule> describe(List<Schedule> schedule) {
        if (schedule.isEmpty()) return new ArrayList<>();

        Map<Long, Group> groups = masterDirectoryService.groups(
                schedule.stream().map(Schedule::getGroupMasterId).filter(Objects::nonNull).toList());

        Map<Long, Teacher> teachers = masterDirectoryService.teachers(
                schedule.stream().map(Schedule::getTeacherMasterId).filter(Objects::nonNull).toList());

        // изменения одним запросом на всю выдачу: в недельном расписании группы под сорок
        // пар, и спрашивать по одной значило бы сорок походов в базу вместо одного
        Map<Long, List<Change>> changes = changeRepository
                .findAllBySchedules(schedule.stream().map(Schedule::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(change -> change.getSchedule().getId()));

        for (Schedule pair : schedule) {
            pair.setGroup(groups.get(pair.getGroupMasterId()));
            pair.setTeacher(teachers.get(pair.getTeacherMasterId()));
            pair.setChanges(changes.getOrDefault(pair.getId(), List.of()));
        }

        return new ArrayList<>(schedule);
    }

    /** Попадает ли закреплённая дата пары в неделю запрошенной даты. */
    private static boolean matchesWeek(Schedule schedule, String date) {
        String pinnedDate = schedule.getPinnedDate();
        if (pinnedDate == null || pinnedDate.isBlank()) return true;
        if ("full".equals(date)) return true;

        String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yy"));

        // обратная совместимость с предыдущими версиями приложения
        LocalDate anchor = date == null
                ? LocalDate.now()
                : LocalDate.parse(date + "." + year, DateTimeFormatter.ofPattern("dd.MM.yy"))
                        .withYear(LocalDate.now().getYear());

        LocalDate monday = anchor.with(DayOfWeek.MONDAY);
        List<String> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDates.add(monday.plusDays(i).format(DateTimeFormatter.ofPattern("dd.MM.yy")));
        }

        return weekDates.contains(pinnedDate + "." + year);
    }

    /** Совпадает ли закреплённая дата пары с запрошенной. */
    private static boolean matchesDay(Schedule schedule, String date) {
        String pinnedDate = schedule.getPinnedDate();
        if (pinnedDate == null || pinnedDate.isBlank()) return true;
        return date == null || pinnedDate.equals(date);
    }

    private static Set<String> unique(List<Schedule> schedule, Function<Schedule, String> field) {
        return schedule.stream()
                .map(field)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String trimmed(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
