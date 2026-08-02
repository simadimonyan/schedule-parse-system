package app.service.infra;

import app.repository.dao.ScheduleRepository;
import app.service.domain.version.VersionService;
import app.repository.models.dto.directory.Group;
import app.repository.models.dto.master.MasterAuditoriumRequest;
import app.repository.models.dto.master.MasterAuditoriumView;
import app.repository.models.dto.master.MasterBlockRequest;
import app.repository.models.dto.master.MasterBlockView;
import app.repository.models.dto.master.MasterGroupRequest;
import app.repository.models.dto.master.MasterGroupView;
import app.repository.models.dto.master.MasterSubjectRequest;
import app.repository.models.dto.master.MasterSubjectView;
import app.repository.models.dto.master.MasterTeacherRequest;
import app.repository.models.dto.master.MasterTeacherView;
import app.service.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Связывание расписания со справочниками мастер-сервиса.
 *
 * <p>Направления два и они несимметричны:
 * <ul>
 *   <li><b>расписание → мастер</b>: всё, что встретилось в разобранном файле — группы,
 *       преподаватели, предметы, корпуса и аудитории, — ищется в справочнике и заводится,
 *       если его там нет;
 *   <li><b>мастер → расписание</b>: события из Kafka говорят, что справочник изменился, и
 *       ответы сервиса пора пересобрать.
 * </ul>
 *
 * <p>Связывание групп и преподавателей — не отложенная работа, а условие сохранения: пара
 * ссылается на группу идентификатором, и без него её негде записать. Поэтому
 * {@link #linkGroups} и {@link #linkTeachers} вызываются до вставки строк и возвращают
 * готовое соответствие «имя → идентификатор». Предметы и аудитории, наоборот,
 * синхронизируются после: в паре они хранятся строками, и от них запись не зависит.
 *
 * <p>Своей таблицы соответствий сервис не держит. Каждая загрузка файла начинается с поиска
 * по естественному ключу ({@code resolve/batch}): он же отвечает на вопрос «что из этого уже
 * заведено», он же не даёт слать в справочник одно и то же дважды. Цена — лишняя пачка
 * запросов на загрузку; выгода — единственный источник правды и нечему протухать.
 *
 * <p>Пишется справочник по HTTP ({@link MasterServiceManager}), а читается через общий кеш
 * мастера в Redis ({@link MasterCacheReader}) — к моменту события слепок там уже свежий.
 */
@Slf4j
@Service
public class MasterSyncService {

    private final MasterServiceManager masterServiceManager;
    private final MasterCacheReader masterCacheReader;
    private final ScheduleRepository scheduleRepository;
    private final CacheService cacheService;
    private final VersionService versionService;
    private final Long defaultFacultyId;
    private final Long defaultBlockId;

    public MasterSyncService(
            MasterServiceManager masterServiceManager,
            MasterCacheReader masterCacheReader,
            ScheduleRepository scheduleRepository,
            CacheService cacheService,
            VersionService versionService,
            @Value("${master.default.faculty.id}") Long defaultFacultyId,
            @Value("${master.default.block.id}") Long defaultBlockId
    ) {
        this.masterServiceManager = masterServiceManager;
        this.masterCacheReader = masterCacheReader;
        this.scheduleRepository = scheduleRepository;
        this.cacheService = cacheService;
        this.versionService = versionService;
        this.defaultFacultyId = defaultFacultyId;
        this.defaultBlockId = defaultBlockId;
    }

    /**
     * Переводит группы разобранного файла в идентификаторы справочника, заводя недостающие.
     *
     * <p>Транзакции здесь нет и не нужно: метод ничего не пишет к себе, только обменивается с
     * мастером. Частичный результат допустим — не связавшиеся группы дождутся следующей
     * загрузки файла.
     *
     * @return соответствие «название группы → идентификатор мастера»; групп, которых связать
     *         не удалось, в ответе нет — их расписание сохранять не с чем
     */
    public Map<String, Long> linkGroups(Collection<Group> parsed) {
        Map<String, Group> byName = distinctByName(parsed);
        if (byName.isEmpty()) return Map.of();

        // сперва забираем идентификаторы тех, кто в справочнике уже есть: создание такой
        // группы мастер отклонит по уникальности названия и идентификатора не вернёт,
        // а значит связать её было бы нечем
        List<MasterGroupView> existing = masterServiceManager.resolveGroups(List.copyOf(byName.keySet()));

        Map<String, Long> linked = existing.stream()
                .filter(view -> view.name() != null && !Boolean.TRUE.equals(view.isDeleted()))
                .collect(Collectors.toMap(view -> view.name().trim(), MasterGroupView::id,
                        (a, b) -> a, LinkedHashMap::new));

        pushStudyForm(byName, linked);

        List<String> missing = missing(byName.keySet(), linked);
        if (missing.isEmpty()) return linked;

        List<MasterGroupRequest.GroupItem> items = missing.stream()
                .map(byName::get)
                .map(group -> new MasterGroupRequest.GroupItem(
                        null,
                        defaultFacultyId,
                        group.getCourse(),
                        group.getName(),
                        group.getLevel(),
                        group.getStudyForm(),
                        null
                ))
                .toList();

        List<MasterGroupView> created = masterServiceManager.createGroups(items);
        for (MasterGroupView view : created) {
            if (view.name() == null) continue;
            linked.put(view.name().trim(), view.id());
            log.info("Группа {} заведена в мастер-сервисе: master_id = {}", view.name().trim(), view.id());
        }

        int missed = missing.size() - created.size();
        if (missed > 0) {
            log.error("Не связано групп: {} из {} — их расписание не будет сохранено",
                    missed, missing.size());
        }
        return linked;
    }

    /**
     * Переводит преподавателей разобранного файла в идентификаторы справочника.
     *
     * <p>В отличие от группы, преподаватель для сохранения пары не обязателен: несвязанный
     * оставит поле пустым, но саму пару в расписании не отменит.
     *
     * @return соответствие «строка „Фамилия И.О.“ → идентификатор мастера»
     */
    public Map<String, Long> linkTeachers(Collection<String> labels) {
        Set<String> keys = distinct(labels);
        if (keys.isEmpty()) return Map.of();

        Map<String, Long> linked = new LinkedHashMap<>(existingTeachers(List.copyOf(keys)));

        List<String> missing = missing(keys, linked);
        if (missing.isEmpty()) return linked;

        List<MasterTeacherRequest.TeacherItem> items = new ArrayList<>();
        for (String label : missing) {
            String[] parts = TeacherLabels.split(label);
            items.add(new MasterTeacherRequest.TeacherItem(null, null, parts[1], parts[0], parts[2], null));
        }

        List<MasterTeacherView> created = masterServiceManager.createTeachers(items);

        // У преподавателя нет естественного ключа — сопоставить ответ с запросом можно
        // только по порядку. Порядок надёжен, лишь когда мастер принял пакет целиком:
        // при неполном ответе идентификаторы разъедутся и достанутся чужим людям
        if (created.size() != missing.size()) {
            log.warn("Мастер-сервис принял {} преподавателей из {} — связывание отложено до следующей загрузки",
                    created.size(), missing.size());
            return linked;
        }

        for (int i = 0; i < missing.size(); i++) {
            linked.put(missing.get(i), created.get(i).id());
            log.info("Преподаватель {} заведён в мастер-сервисе: master_id = {}",
                    missing.get(i), created.get(i).id());
        }
        return linked;
    }

    /**
     * Ищет преподавателей, которые в справочнике уже есть.
     *
     * <p>Здесь это критично: проверки на дубликат у мастера нет — тёзки среди людей
     * нормальны, — поэтому повторная отправка не отклоняется, а молча заводит второго
     * такого же человека. Сверка идёт по фамилии, имени и отчеству: фамилии для поиска
     * достаточно, чтобы вытащить кандидатов, а различает их полное ФИО.
     */
    private Map<String, Long> existingTeachers(List<String> labels) {
        List<String> lastNames = labels.stream()
                .map(label -> TeacherLabels.split(label)[0])
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Long> idByFullName = masterServiceManager.resolveTeachers(lastNames).stream()
                .filter(view -> !Boolean.TRUE.equals(view.isDeleted()))
                .collect(Collectors.toMap(
                        view -> TeacherLabels.fullName(view.lastName(), view.name(), view.patronymic()),
                        MasterTeacherView::id,
                        (a, b) -> a));

        if (idByFullName.isEmpty()) return Map.of();

        Map<String, Long> found = new LinkedHashMap<>();
        for (String label : labels) {
            String[] parts = TeacherLabels.split(label);
            Long masterId = idByFullName.get(TeacherLabels.fullName(parts[0], parts[1], parts[2]));
            if (masterId != null) found.put(label, masterId);
        }
        return found;
    }

    /**
     * Досылает форму обучения группам, которые уже есть в справочнике.
     *
     * <p>Владелец этого поля — расписание: только оно знает форму, определяя её по имени
     * файла. Без досылки форма попадала бы в мастер лишь при первом заведении группы, а у
     * заведённых раньше так и осталась бы пустой.
     *
     * <p>Обновление уходит, лишь когда значение у мастера отличается: {@code PUT} заменяет
     * запись целиком, и гонять весь справочник на каждой загрузке файла незачем.
     */
    private void pushStudyForm(Map<String, Group> parsed, Map<String, Long> linked) {
        if (linked.isEmpty()) return;

        List<MasterGroupView> views = masterCacheReader.getGroups(List.copyOf(linked.values()));
        if (views.isEmpty()) return;

        List<MasterGroupRequest.GroupItem> stale = views.stream()
                .filter(view -> {
                    Group group = parsed.get(view.name());
                    return group != null
                            && group.getStudyForm() != null
                            && !group.getStudyForm().equals(view.studyForm());
                })
                .map(view -> new MasterGroupRequest.GroupItem(
                        view.id(),
                        view.faculty() == null ? defaultFacultyId : view.faculty().id(),
                        view.course(),
                        view.name(),
                        view.level(),
                        parsed.get(view.name()).getStudyForm(),
                        view.capacity()
                ))
                .toList();

        if (stale.isEmpty()) return;

        int updated = masterServiceManager.updateGroups(stale).size();
        log.info("Форма обучения дослана в мастер-сервис: обновлено {} из {}", updated, stale.size());
    }

    /**
     * Публикует в справочник предметы, встреченные в расписании.
     *
     * <p>Своей сущности у предмета в расписании нет — это название ячейки, поэтому уже
     * заведённые узнаются только поиском у мастера. Он же не даёт слать весь список заново:
     * создаётся лишь то, чего в справочнике не нашлось.
     *
     * <p>Тип занятия принадлежит паре, а не предмету: один предмет читается и лекцией, и
     * практикой. В справочник он попадает, только если во всём расписании у предмета
     * встретился единственный тип — иначе поле остаётся пустым, потому что название
     * предмета у мастера уникально и выбрать один вариант из нескольких было бы произволом.
     */
    public void syncSubjects(Collection<String> names) {
        Map<String, String> types = lessonTypes(names);
        if (types.isEmpty()) return;

        List<MasterSubjectView> existing =
                masterServiceManager.resolveSubjects(List.copyOf(types.keySet()));

        Set<String> known = existing.stream()
                .map(MasterSubjectView::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> fresh = types.keySet().stream().filter(name -> !known.contains(name)).toList();
        if (!fresh.isEmpty()) {
            List<MasterSubjectRequest.SubjectItem> items = fresh.stream()
                    .map(name -> new MasterSubjectRequest.SubjectItem(null, name, types.get(name)))
                    .toList();

            int created = masterServiceManager.createSubjects(items).size();
            log.info("Предметы в мастер-сервисе: отправлено {}, принято {}", fresh.size(), created);
        }

        pushSubjectType(types, existing);
    }

    /**
     * Тип занятия по названию предмета: значение — только для однозначных, {@code null} —
     * для тех, у кого типов несколько или нет вовсе. Ключи покрывают все переданные имена,
     * чтобы потерявшие однозначность предметы получили сброс, а не сохранили старый тип.
     */
    private Map<String, String> lessonTypes(Collection<String> names) {
        Set<String> keys = distinct(names);

        Map<String, String> types = new LinkedHashMap<>();
        keys.forEach(name -> types.put(name, null));

        if (keys.isEmpty()) return types;

        for (Object[] row : scheduleRepository.findUnambiguousLessonTypes(versionService.writableId(), keys)) {
            types.put((String) row[0], (String) row[1]);
        }
        return types;
    }

    /**
     * Досылает тип занятия предметам, которые в справочнике уже есть.
     *
     * <p>Тип становится известен по мере разбора файлов: пока предмет встретился только
     * лекцией, он однозначен, а после файла с практикой — уже нет. Поэтому значение
     * обновляется в обе стороны, в том числе сбрасывается в пустое, когда однозначность
     * потерялась.
     */
    private void pushSubjectType(Map<String, String> types, List<MasterSubjectView> existing) {
        List<MasterSubjectRequest.SubjectItem> stale = existing.stream()
                .filter(view -> view.name() != null && types.containsKey(view.name()))
                .filter(view -> !Objects.equals(types.get(view.name()), view.subjectType()))
                .map(view -> new MasterSubjectRequest.SubjectItem(
                        view.id(), view.name(), types.get(view.name())))
                .toList();

        if (stale.isEmpty()) return;

        int updated = masterServiceManager.updateSubjects(stale).size();
        log.info("Тип занятия дослан в мастер-сервис: обновлено {} предметов из {}", updated, stale.size());
    }

    /**
     * Публикует в справочник аудитории, встреченные в расписании.
     *
     * <p>В расписании аудитория записана одной строкой, где префикс — корпус: «1-114а» это
     * аудитория «114а» корпуса «1». Корпуса заводятся первыми, потому что мастер требует у
     * аудитории {@code block_id}. Если префикса нет («114а»), корпус неизвестен и берётся
     * корпус-заглушка {@code master.default.block.id} — терять аудиторию из-за неполного
     * формата хуже, чем привязать её к заглушке.
     */
    public void syncAuditoriums(Collection<String> numbers) {
        Set<String> full = distinct(numbers);
        if (full.isEmpty()) return;

        Map<String, Long> blocks = syncBlocks(full.stream()
                .map(MasterSyncService::blockOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        // Номер уникален лишь внутри корпуса, поэтому по одному номеру мастер возвращает
        // все совпадения — нужную выбираем по корпусу из исходной строки расписания.
        List<MasterAuditoriumView> existing = masterServiceManager
                .resolveAuditoriums(full.stream().map(MasterSyncService::numberOf).distinct().toList());

        List<String> fresh = full.stream()
                .filter(number -> existing.stream().noneMatch(view -> matches(view, number, blocks)))
                .toList();

        if (fresh.isEmpty()) return;

        List<MasterAuditoriumRequest.AuditoriumItem> items = fresh.stream()
                .map(number -> new MasterAuditoriumRequest.AuditoriumItem(
                        null,
                        blockIdOf(number, blocks),
                        null,
                        null,
                        null,
                        numberOf(number),
                        null))
                .toList();

        int created = masterServiceManager.createAuditoriums(items).size();
        log.info("Аудитории в мастер-сервисе: отправлено {}, принято {}", fresh.size(), created);
    }

    /** Совпадает ли аудитория справочника со строкой расписания — по номеру и корпусу. */
    private boolean matches(MasterAuditoriumView view, String fullNumber, Map<String, Long> blocks) {
        Long blockId = view.block() == null ? null : view.block().id();
        return numberOf(fullNumber).equals(view.number())
                && Objects.equals(blockIdOf(fullNumber, blocks), blockId);
    }

    /**
     * Заводит корпуса, которых ещё нет в справочнике.
     *
     * @return соответствие «название корпуса → идентификатор», нужное аудиториям этой же
     *         загрузки: у мастера аудитория ссылается на корпус идентификатором
     */
    private Map<String, Long> syncBlocks(Collection<String> names) {
        if (names.isEmpty()) return Map.of();

        Map<String, Long> blocks = masterServiceManager.resolveBlocks(List.copyOf(names)).stream()
                .filter(view -> view.name() != null)
                .collect(Collectors.toMap(MasterBlockView::name, MasterBlockView::id,
                        (a, b) -> a, LinkedHashMap::new));

        List<String> fresh = names.stream().filter(name -> !blocks.containsKey(name)).toList();
        if (fresh.isEmpty()) return blocks;

        List<MasterBlockRequest.BlockItem> items = fresh.stream()
                .map(name -> new MasterBlockRequest.BlockItem(null, name, null, null))
                .toList();

        List<MasterBlockView> created = masterServiceManager.createBlocks(items);
        created.stream()
                .filter(view -> view.name() != null)
                .forEach(view -> blocks.put(view.name(), view.id()));

        log.info("Корпуса в мастер-сервисе: отправлено {}, принято {}", fresh.size(), created.size());
        return blocks;
    }

    /**
     * Справочник изменился — ответы сервиса пора пересобрать.
     *
     * <p>Больше делать нечего: пары ссылаются на идентификаторы, и переименование группы их
     * не задевает, а название, курс и уровень попадают в выдачу прямо из справочника. Всё,
     * что могло устареть, — это кеш, включая переводы имён в идентификаторы.
     */
    public void invalidate(String reason) {
        log.info("Справочник изменился ({}) — кеш сброшен", reason);
        cacheService.clearAllCaches();
    }

    /** Идентификатор корпуса для аудитории; при неизвестном корпусе — заглушка. */
    private Long blockIdOf(String fullNumber, Map<String, Long> blocks) {
        String blockName = blockOf(fullNumber);
        if (blockName == null) return defaultBlockId;

        return blocks.getOrDefault(blockName, defaultBlockId);
    }

    /** «1-114а» → «1»; у номера без префикса корпуса нет. */
    private static String blockOf(String fullNumber) {
        int dash = fullNumber.indexOf('-');
        if (dash <= 0) return null;

        String prefix = fullNumber.substring(0, dash);
        return prefix.chars().allMatch(Character::isDigit) ? prefix : null;
    }

    /** «1-114а» → «114а»; номер без префикса возвращается как есть. */
    private static String numberOf(String fullNumber) {
        return blockOf(fullNumber) == null ? fullNumber : fullNumber.substring(fullNumber.indexOf('-') + 1);
    }

    private static List<String> missing(Collection<String> keys, Map<String, Long> linked) {
        return keys.stream().filter(key -> !linked.containsKey(key)).toList();
    }

    private static Map<String, Group> distinctByName(Collection<Group> groups) {
        if (groups == null) return Map.of();

        Map<String, Group> byName = new LinkedHashMap<>();
        for (Group group : groups) {
            if (group == null || group.getName() == null || group.getName().isBlank()) continue;
            byName.putIfAbsent(group.getName().trim(), group);
        }
        return byName;
    }

    private static Set<String> distinct(Collection<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
