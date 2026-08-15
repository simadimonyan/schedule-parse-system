package app.service.infra;

import app.repository.models.dto.master.MasterAuditoriumRequest;
import app.repository.models.dto.master.MasterAuditoriumView;
import app.repository.models.dto.master.MasterBatchResponse;
import app.repository.models.dto.master.MasterBlockRequest;
import app.repository.models.dto.master.MasterBlockView;
import app.repository.models.dto.master.MasterGroupRequest;
import app.repository.models.dto.master.MasterGroupView;
import app.repository.models.dto.master.MasterIdsRequest;
import app.repository.models.dto.master.MasterKeysRequest;
import app.repository.models.dto.master.MasterSubjectRequest;
import app.repository.models.dto.master.MasterSubjectView;
import app.repository.models.dto.master.MasterTeacherRequest;
import app.repository.models.dto.master.MasterTeacherView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент мастер-сервиса — справочника групп и преподавателей ISMS.
 *
 * <p>Все операции у мастера пакетные, ответ приходит в общем конверте
 * {@link MasterBatchResponse}: HTTP 200 не значит, что обработаны все записи — неудачи
 * лежат в {@code errors} поштучно. Отсюда контракт методов: возвращается то, что мастер
 * действительно обработал, остальное уходит в лог.
 *
 * <p>Недоступность мастера не должна ломать разбор расписания: файл уже загружен, пары
 * разобраны, и терять их из-за сетевой ошибки нельзя. Поэтому любая ошибка обмена
 * гасится логом, а метод отдаёт пустой список — связывание повторится на следующей
 * загрузке файла или придёт событием из Kafka.
 */
@Slf4j
@Service
public class MasterServiceManager {

    // Ограничение пакета у мастера — от 1 до 500 записей
    private static final int BATCH_LIMIT = 500;

    // Префикс /master обязателен: у мастер-сервиса свои контроллеры висят на
    // /api/v1/master/**, а /api/v1/** — это пути самого сервиса расписания
    private static final String MASTER_API = "/api/v1/master";

    private static final String GROUPS = MASTER_API + "/groups";
    private static final String TEACHERS = MASTER_API + "/teachers";
    private static final String SUBJECTS = MASTER_API + "/subjects";
    private static final String BLOCKS = MASTER_API + "/blocks";
    private static final String AUDITORIUMS = MASTER_API + "/auditoriums";

    private static final ParameterizedTypeReference<MasterBatchResponse<MasterGroupView>> GROUP_BATCH =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<MasterBatchResponse<MasterTeacherView>> TEACHER_BATCH =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<MasterBatchResponse<MasterSubjectView>> SUBJECT_BATCH =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<MasterBatchResponse<MasterBlockView>> BLOCK_BATCH =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<MasterBatchResponse<MasterAuditoriumView>> AUDITORIUM_BATCH =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final Duration timeout;

    public MasterServiceManager(
            WebClient webClient,
            @Value("${master.service.timeout.seconds:10}") long timeoutSeconds
    ) {
        this.webClient = webClient;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** Создать группы в справочнике. Возвращает только те, которые мастер принял. */
    public List<MasterGroupView> createGroups(List<MasterGroupRequest.GroupItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.POST, GROUPS + "/create/batch", new MasterGroupRequest(chunk),
                GROUP_BATCH, "Создание групп в мастер-сервисе"));
    }

    /**
     * Обновить группы справочника. У мастера {@code PUT} заменяет запись целиком, поэтому
     * элемент должен нести все поля, а не только изменённые.
     */
    public List<MasterGroupView> updateGroups(List<MasterGroupRequest.GroupItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.PUT, GROUPS + "/update/batch", new MasterGroupRequest(chunk),
                GROUP_BATCH, "Обновление групп в мастер-сервисе"));
    }

    /**
     * Найти в справочнике записи по естественному ключу — названию, фамилии, номеру.
     *
     * <p>Единственный способ узнать идентификатор записи, которую завёл кто-то другой:
     * создание такой записи мастер отклонит по уникальности и идентификатора не вернёт, а
     * значит связать её будет нечем.
     */
    public List<MasterGroupView> resolveGroups(List<String> names) {
        return batched(names, chunk -> call(
                HttpMethod.POST, GROUPS + "/resolve/batch", new MasterKeysRequest(chunk),
                GROUP_BATCH, "Поиск групп в мастер-сервисе"));
    }

    /** Найти преподавателей справочника по фамилиям. */
    public List<MasterTeacherView> resolveTeachers(List<String> lastNames) {
        return batched(lastNames, chunk -> call(
                HttpMethod.POST, TEACHERS + "/resolve/batch", new MasterKeysRequest(chunk),
                TEACHER_BATCH, "Поиск преподавателей в мастер-сервисе"));
    }

    /** Найти предметы справочника по названиям. */
    public List<MasterSubjectView> resolveSubjects(List<String> names) {
        return batched(names, chunk -> call(
                HttpMethod.POST, SUBJECTS + "/resolve/batch", new MasterKeysRequest(chunk),
                SUBJECT_BATCH, "Поиск предметов в мастер-сервисе"));
    }

    /** Найти корпуса справочника по названиям. */
    public List<MasterBlockView> resolveBlocks(List<String> names) {
        return batched(names, chunk -> call(
                HttpMethod.POST, BLOCKS + "/resolve/batch", new MasterKeysRequest(chunk),
                BLOCK_BATCH, "Поиск корпусов в мастер-сервисе"));
    }

    /** Найти аудитории справочника по номерам (без префикса корпуса). */
    public List<MasterAuditoriumView> resolveAuditoriums(List<String> numbers) {
        return batched(numbers, chunk -> call(
                HttpMethod.POST, AUDITORIUMS + "/resolve/batch", new MasterKeysRequest(chunk),
                AUDITORIUM_BATCH, "Поиск аудиторий в мастер-сервисе"));
    }

    /** Прочитать группы справочника по идентификаторам мастера. */
    public List<MasterGroupView> getGroups(List<Long> ids) {
        return batched(ids, chunk -> call(
                HttpMethod.GET, GROUPS + "/list/batch", new MasterIdsRequest(chunk),
                GROUP_BATCH, "Чтение групп из мастер-сервиса"));
    }

    /** Создать преподавателей в справочнике. Возвращает только тех, которых мастер принял. */
    public List<MasterTeacherView> createTeachers(List<MasterTeacherRequest.TeacherItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.POST, TEACHERS + "/create/batch", new MasterTeacherRequest(chunk),
                TEACHER_BATCH, "Создание преподавателей в мастер-сервисе"));
    }

    /** Прочитать преподавателей справочника по идентификаторам мастера. */
    public List<MasterTeacherView> getTeachers(List<Long> ids) {
        return batched(ids, chunk -> call(
                HttpMethod.GET, TEACHERS + "/list/batch", new MasterIdsRequest(chunk),
                TEACHER_BATCH, "Чтение преподавателей из мастер-сервиса"));
    }

    /** Создать предметы в справочнике. Возвращает только те, которые мастер принял. */
    public List<MasterSubjectView> createSubjects(List<MasterSubjectRequest.SubjectItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.POST, SUBJECTS + "/create/batch", new MasterSubjectRequest(chunk),
                SUBJECT_BATCH, "Создание предметов в мастер-сервисе"));
    }

    /** Обновить предметы справочника. {@code PUT} заменяет запись целиком. */
    public List<MasterSubjectView> updateSubjects(List<MasterSubjectRequest.SubjectItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.PUT, SUBJECTS + "/update/batch", new MasterSubjectRequest(chunk),
                SUBJECT_BATCH, "Обновление предметов в мастер-сервисе"));
    }

    /** Создать корпуса в справочнике. Возвращает только те, которые мастер принял. */
    public List<MasterBlockView> createBlocks(List<MasterBlockRequest.BlockItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.POST, BLOCKS + "/create/batch", new MasterBlockRequest(chunk),
                BLOCK_BATCH, "Создание корпусов в мастер-сервисе"));
    }

    /** Создать аудитории в справочнике. Возвращает только те, которые мастер принял. */
    public List<MasterAuditoriumView> createAuditoriums(
            List<MasterAuditoriumRequest.AuditoriumItem> items) {
        return batched(items, chunk -> call(
                HttpMethod.POST, AUDITORIUMS + "/create/batch", new MasterAuditoriumRequest(chunk),
                AUDITORIUM_BATCH, "Создание аудиторий в мастер-сервисе"));
    }

    /**
     * Разбивает запрос по лимиту пакета: пакет больше 500 записей мастер отклоняет целиком,
     * а групп на курсе бывает и больше.
     */
    private <T, R> List<R> batched(List<T> items, java.util.function.Function<List<T>, List<R>> action) {
        if (items == null || items.isEmpty()) return List.of();

        List<R> result = new ArrayList<>();
        for (int from = 0; from < items.size(); from += BATCH_LIMIT) {
            result.addAll(action.apply(items.subList(from, Math.min(from + BATCH_LIMIT, items.size()))));
        }
        return result;
    }

    /**
     * Чтение у мастера — это {@code GET} с телом запроса, поэтому запрос собирается через
     * {@code method(...)}: {@code WebClient.get()} тело не отдаёт.
     */
    private <R> List<R> call(
            HttpMethod method,
            String uri,
            Object body,
            ParameterizedTypeReference<MasterBatchResponse<R>> type,
            String action
    ) {
        try {
            MasterBatchResponse<R> response = webClient.method(method)
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(type)
                    .block(timeout);

            if (response == null) {
                log.error("{}: пустой ответ мастер-сервиса", action);
                return List.of();
            }

            if (response.errors() != null && !response.errors().isEmpty()) {
                response.errors().forEach(error ->
                        log.warn("{}: запись отклонена — {} (код {})", action, error.message(), error.code()));
            }

            return response.updated() == null ? List.of() : response.updated();
        }
        catch (Exception e) {
            log.error("{}: обмен не удался, справочник остался несвязанным", action, e);
            return List.of();
        }
    }

}
