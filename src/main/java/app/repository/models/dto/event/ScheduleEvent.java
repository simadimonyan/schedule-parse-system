package app.repository.models.dto.event;

import app.repository.models.entity.Change;
import app.repository.models.entity.ChangeType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Объявление сервиса расписания об изменении (топик {@code schedule-events}).
 *
 * <p>Зеркало {@code CacheEvent} мастер-сервиса, только в другую сторону: там сервис слушает
 * чужой справочник, здесь — рассказывает о своём расписании. Общей библиотеки на два сервиса
 * нет и не будет: сервисы деплоятся независимо, а общий класс связал бы их версии. Подписчик
 * заводит у себя копию этой записи — ровно как {@code CacheEvent} скопирован сюда от мастера.
 *
 * <p>Запись плоская и общая на все события: у каждого типа заполнена своя часть полей,
 * остальные приходят пустыми и в тело сообщения не попадают. Дерево классов на каждый тип
 * читалось бы строже, но заставило бы подписчика разбирать сообщение дважды — сначала чтобы
 * узнать тип, потом чтобы прочитать тело.
 *
 * <p>Событие говорит, что изменилось, но не как: ни расписания, ни списка пар в нём нет.
 * Подписчик, которому нужны сами пары, идёт за ними в API — иначе контракт топика повторял бы
 * контракт выдачи и расходился бы с ним при первой же правке.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleEvent(

        @JsonProperty("event_type")
        ScheduleEventType eventType,

        /** Версия, которой касается событие. Пусто у {@link ScheduleEventType#WEEK_SWAPPED}. */
        @JsonProperty("version_id")
        Long versionId,

        /** Изменение, слот или график занятости, о котором речь, — если событие точечное. */
        @JsonProperty("entity_id")
        Long entityId,

        /** Группы, чьё расписание затронуто: по ним подписчик решает, кого это касается. */
        @JsonProperty("group_ids")
        List<Long> groupIds,

        /** Преподаватели, которых затронуло событие. */
        @JsonProperty("teacher_ids")
        List<Long> teacherIds,

        @JsonProperty("change_type")
        ChangeType changeType,

        /** День, на который заведено изменение: у изменений он всегда один. */
        @JsonProperty("change_date")
        LocalDate changeDate,

        /** Чётность недели после переключения. */
        @JsonProperty("week_count")
        Integer weekCount,

        @JsonProperty("occurred_at")
        Instant occurredAt,

        String note

) implements Serializable {

    /** Разбор файла заменил расписание групп. */
    public static ScheduleEvent versionParsed(Long versionId, List<Long> groupIds) {
        return of(ScheduleEventType.VERSION_PARSED, versionId, null, groupIds, null, null, null, null, null);
    }

    /** Пары поставлены поячеечно из редактора. */
    public static ScheduleEvent scheduleUpdated(Long versionId, List<Long> groupIds) {
        return of(ScheduleEventType.SCHEDULE_UPDATED, versionId, null, groupIds, null, null, null, null, null);
    }

    /** Версия стала активной: расписание клиентов сменилось целиком. */
    public static ScheduleEvent versionPublished(Long versionId, String name) {
        return of(ScheduleEventType.VERSION_PUBLISHED, versionId, null, null, null, null, null, null, name);
    }

    public static ScheduleEvent versionDiscarded(Long versionId) {
        return of(ScheduleEventType.VERSION_DISCARDED, versionId, null, null, null, null, null, null, null);
    }

    /**
     * Событие изменения расписания, собранное из самой записи.
     *
     * <p>Группа и преподаватель кладутся списками, хотя их здесь по одному: подписчику важно
     * не «сколько», а «кого касается», и один разбор списка на все события проще, чем два
     * разных поля с одинаковым смыслом.
     */
    public static ScheduleEvent change(ScheduleEventType eventType, Change change) {
        return of(
                eventType,
                change.getVersion() == null ? null : change.getVersion().getId(),
                change.getId(),
                ids(change.getGroupMasterId()),
                ids(change.getTeacherMasterId()),
                change.getChangeType(),
                change.getChangeDate(),
                null,
                null);
    }

    /** Правлена сетка звонков: время пар версии сдвинулось. */
    public static ScheduleEvent slotsUpdated(Long versionId, Long slotId, String note) {
        return of(ScheduleEventType.SLOTS_UPDATED, versionId, slotId, null, null, null, null, null, note);
    }

    /** Правлен график занятости преподавателя. */
    public static ScheduleEvent workScheduleUpdated(
            Long versionId, Long workScheduleId, Long teacherMasterId, String note) {
        return of(ScheduleEventType.WORK_SCHEDULE_UPDATED, versionId, workScheduleId,
                null, ids(teacherMasterId), null, null, null, note);
    }

    /** Переключена чётность недели: сменилась показываемая половина расписания. */
    public static ScheduleEvent weekSwapped(Integer weekCount) {
        return of(ScheduleEventType.WEEK_SWAPPED, null, null, null, null, null, null, weekCount, null);
    }

    /**
     * Ключ сообщения в Kafka.
     *
     * <p>Ключом идёт версия, а не тип события: Kafka держит порядок внутри раздела, и события
     * одной версии должны прийти подписчику в том же порядке, в каком случились. Разложи их по
     * типам — и «версия опубликована» могло бы обогнать «расписание групп заменено», а
     * подписчик сбросил бы кеш раньше, чем в базе появились новые пары. Событию без версии
     * ({@code WEEK_SWAPPED}) ключом остаётся его тип.
     */
    public String key() {
        return versionId == null ? String.valueOf(eventType) : String.valueOf(versionId);
    }

    private static List<Long> ids(Long id) {
        return id == null ? null : List.of(id);
    }

    private static ScheduleEvent of(
            ScheduleEventType eventType,
            Long versionId,
            Long entityId,
            List<Long> groupIds,
            List<Long> teacherIds,
            ChangeType changeType,
            LocalDate changeDate,
            Integer weekCount,
            String note
    ) {
        return new ScheduleEvent(
                eventType, versionId, entityId,
                groupIds == null || groupIds.isEmpty() ? null : List.copyOf(groupIds),
                teacherIds == null || teacherIds.isEmpty() ? null : List.copyOf(teacherIds),
                changeType, changeDate, weekCount, Instant.now(), note);
    }

}
