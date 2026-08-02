package app.service.infra;

import app.repository.dao.ScheduleRepository;
import app.service.domain.version.VersionService;
import app.repository.models.dto.directory.Group;
import app.repository.models.dto.directory.Teacher;
import app.repository.models.dto.master.MasterGroupView;
import app.repository.models.dto.master.MasterTeacherView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Чтение справочников мастер-сервиса в терминах расписания.
 *
 * <p>Расписание спрашивают по имени — «расписание группы ПИ-301», «расписание Иванова
 * И.И.», — а хранит оно идентификаторы мастера. Между этими двумя мирами и стоит этот класс.
 *
 * <p>Своей таблицы соответствий у сервиса нет: единственный источник — сам мастер. Имя
 * переводится в идентификатор его поиском по естественному ключу ({@code resolve/batch}), а
 * идентификатор в название — общим кешем мастера в Redis ({@link MasterCacheReader}).
 *
 * <p>Отсюда цена: перевод имени — сетевой вызов, и мастер оказывается на пути чтения
 * расписания. Смягчает его кеш сервиса: результат резолва живёт в регионе {@code directory}
 * ровно столько же, сколько остальные ответы, и сбрасывается теми же событиями. Промах
 * стоит одного запроса к мастеру, попадание — ни одного.
 *
 * <p>Пустой результат не кешируется намеренно: группа, которой ещё нет в справочнике,
 * появится там при ближайшей загрузке файла, и держать час «её не существует» значило бы
 * прятать её от клиента.
 */
@Slf4j
@Service
public class MasterDirectoryService {

    private final MasterCacheReader masterCacheReader;
    private final MasterServiceManager masterServiceManager;
    private final ScheduleRepository scheduleRepository;
    private final VersionService versionService;

    public MasterDirectoryService(
            MasterCacheReader masterCacheReader,
            MasterServiceManager masterServiceManager,
            ScheduleRepository scheduleRepository,
            VersionService versionService
    ) {
        this.masterCacheReader = masterCacheReader;
        this.masterServiceManager = masterServiceManager;
        this.scheduleRepository = scheduleRepository;
        this.versionService = versionService;
    }

    /** Идентификатор группы по названию из запроса; {@code null} — в справочнике такой нет. */
    @Cacheable(value = "directory", key = "'group:' + #name", unless = "#result == null")
    public Long groupId(String name) {
        if (name == null || name.isBlank()) return null;

        String key = name.trim();
        return masterServiceManager.resolveGroups(List.of(key)).stream()
                .filter(view -> key.equals(view.name()) && !Boolean.TRUE.equals(view.isDeleted()))
                .map(MasterGroupView::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Идентификатор преподавателя по строке «Фамилия И.О.» из запроса.
     *
     * <p>Естественный ключ у мастера — фамилия, и однофамильцы приходят все разом: нужного
     * выбирает совпадение собранной из ФИО строки с запрошенной.
     */
    @Cacheable(value = "directory", key = "'teacher:' + #label", unless = "#result == null")
    public Long teacherId(String label) {
        if (label == null || label.isBlank()) return null;

        String key = label.trim();
        return masterServiceManager.resolveTeachers(List.of(TeacherLabels.split(key)[0])).stream()
                .filter(view -> !Boolean.TRUE.equals(view.isDeleted()))
                .filter(view -> key.equals(TeacherLabels.compose(view)))
                .map(MasterTeacherView::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Группы по идентификаторам мастера — одной пачкой.
     *
     * <p>Поштучное чтение здесь недопустимо: расписание преподавателя ссылается на десятки
     * групп, и каждая обошлась бы отдельным походом в Redis.
     */
    public Map<Long, Group> groups(Collection<Long> ids) {
        List<Long> unique = distinct(ids);
        if (unique.isEmpty()) return Map.of();

        Map<Long, Group> result = new LinkedHashMap<>();
        for (MasterGroupView view : masterCacheReader.getGroups(unique)) {
            result.put(view.id(), toGroup(view));
        }

        // подставить название неоткуда: справочник — единственный, кто его знает
        if (result.size() < unique.size()) {
            log.warn("Справочник не отдал {} групп из {} — пары останутся в выдаче без названия группы",
                    unique.size() - result.size(), unique.size());
        }
        return result;
    }

    /** Преподаватели по идентификаторам мастера. Строка «Фамилия И.О.» собирается из ФИО. */
    public Map<Long, Teacher> teachers(Collection<Long> ids) {
        List<Long> unique = distinct(ids);
        if (unique.isEmpty()) return Map.of();

        Map<Long, Teacher> result = new LinkedHashMap<>();
        for (MasterTeacherView view : masterCacheReader.getTeachers(unique)) {
            result.put(view.id(), toTeacher(view.id(), TeacherLabels.compose(view)));
        }
        return result;
    }

    /** Группы, у которых есть расписание, — перечень групп сервиса для выдачи списков. */
    public List<Group> knownGroups() {
        return List.copyOf(groups(scheduleRepository.findDistinctGroupMasterIds(versionService.activeId())).values());
    }

    /** Преподаватели, у которых есть расписание. */
    public List<Teacher> knownTeachers() {
        return List.copyOf(teachers(scheduleRepository.findDistinctTeacherMasterIds(versionService.activeId())).values());
    }

    private static List<Long> distinct(Collection<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static Group toGroup(MasterGroupView view) {
        Group group = new Group();
        group.setId(view.id());
        group.setName(view.name());
        group.setCourse(view.course());
        group.setLevel(view.level());
        group.setStudyForm(view.studyForm());
        return group;
    }

    private static Teacher toTeacher(Long id, String label) {
        Teacher teacher = new Teacher();
        teacher.setId(id);
        teacher.setLabel(label);
        return teacher;
    }

}
