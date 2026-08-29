package app.controller.api;

import app.repository.models.dto.api.parse.ParseResponse;
import app.repository.models.dto.api.parse.UploadResponse;
import app.service.domain.persistence.SchedulePersistenceService;
import app.service.storage.StorageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Файл расписания: загрузка в бакет и запуск разбора.
 *
 * <p>Обычно разбор запускает сам MinIO: загрузка файла в бакет шлёт вебхук. Ручка
 * {@code /parse} нужна, когда вебхук не сработал или файл лежит в бакете давно —
 * перезалить его ради повторного разбора не хочется.
 *
 * <p>Ручка {@code /upload} появилась ради личного кабинета: в браузере нет доступа к
 * MinIO — ни адреса, ни ключей, — и класть файл в бакет ему приходится через сервис.
 * Раньше единственным путём была консоль MinIO, то есть учётка администратора хранилища
 * у каждого, кто ведёт расписание.
 *
 * <p>Разбор идёт в фоне, поэтому ответ говорит лишь о том, какой файл принят в работу;
 * результат смотрят в логе и в самом расписании.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schedule/schedule")
@SecurityRequirement(name = "Authorization")
public class ParseController {

    private final SchedulePersistenceService persistenceService;
    private final StorageService storageService;

    public ParseController(SchedulePersistenceService persistenceService, StorageService storageService) {
        this.persistenceService = persistenceService;
        this.storageService = storageService;
    }

    @PostMapping("/parse")
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<ParseResponse> parse(
            @RequestParam(value = "file", required = false) String fileName
    ) throws IOException {
        log.info("POST Запрос: /api/v1/schedule/parse?file={}", fileName);
        String resolved = persistenceService.resolveFile(fileName);
        if (resolved == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ParseResponse(
                    null,
                    fileName == null || fileName.isBlank()
                            ? "Бакет расписания пуст — разбирать нечего"
                            : "Файла нет в бакете расписания: " + fileName));
        }

        persistenceService.persistSchedule(resolved);
        return ResponseEntity.accepted().body(new ParseResponse(resolved, "Файл принят в разбор"));
    }

    /**
     * Кладёт файл расписания в бакет и, если попросили, отправляет его в разбор.
     *
     * <p>Имя объекта — имя загруженного файла: по нему разбор определяет курс, а бакет
     * остаётся читаемым для того, кто потом смотрит в него через консоль. Одноимённый файл
     * перезаписывается намеренно — повторная загрузка правленого расписания не должна
     * плодить в бакете почти одинаковые объекты, между которыми потом не разобраться.
     *
     * <p>Проверки имени сделаны здесь, а не оставлены разбору: разбор идёт в фоне, и его
     * отказ виден только в логе сервиса. Человек, который загрузил файл, к этому времени
     * уже ушёл со страницы, будучи уверен, что всё получилось.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_SCHEDULE')")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parse", defaultValue = "true") boolean parse
    ) throws IOException {
        String original = originalName(file);
        log.info("POST Запрос: /api/v1/schedule/upload | Файл: {} ({} байт), разбор: {}",
                original, file.getSize(), parse);

        String rejection = reasonToReject(file, original);
        if (rejection != null) {
            log.warn("Загрузка отклонена: {}", rejection);
            return ResponseEntity.badRequest().body(new UploadResponse(null, false, rejection));
        }

        try (InputStream stream = file.getInputStream()) {
            if (!storageService.put(original, stream, file.getSize(), file.getContentType())) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new UploadResponse(
                        null, false, "Файл не удалось положить в хранилище — смотрите лог сервиса"));
            }
        }

        if (!parse) {
            return ResponseEntity.ok(new UploadResponse(original, false, "Файл загружен"));
        }

        // Подходящий под маску файл MinIO уже отправил в разбор своим вебхуком. Позвать
        // разбор ещё и отсюда значит пройти по одному файлу дважды — параллельно и в две
        // версии расписания сразу.
        if (StorageService.triggersWebhook(original)) {
            return ResponseEntity.accepted().body(new UploadResponse(
                    original, true, "Файл загружен, разбор запущен хранилищем"));
        }

        persistenceService.persistSchedule(original);
        return ResponseEntity.accepted().body(new UploadResponse(
                original, true, "Файл загружен и принят в разбор"));
    }

    /**
     * Имя файла без пути.
     *
     * <p>Браузеры кладут в {@code filename} только имя, но заголовок формирует клиент, а не
     * браузер: в имени объекта не должно оказаться ни каталогов, ни {@code ..}.
     */
    private static String originalName(MultipartFile file) {
        String raw = file.getOriginalFilename();
        if (raw == null || raw.isBlank()) return "";

        String name = Paths.get(raw.replace('\\', '/')).getFileName().toString().trim();
        return name.equals("..") || name.equals(".") ? "" : name;
    }

    /** Почему файл не примут. {@code null} — примут. */
    private static String reasonToReject(MultipartFile file, String name) {
        if (file.isEmpty()) return "Файл пустой";
        if (name.isEmpty()) return "У файла нет имени";

        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
            return "Расписание разбирается только из .xls и .xlsx, а пришло: " + name;
        }

        return courseRejection(name);
    }

    /**
     * Номер курса берётся из имени файла — это требование разбора, а не прихоть.
     *
     * <p>{@code ExcelService} ищет в имени слово «курс» и читает число перед ним; без них
     * разбор падает уже в фоновом потоке. Проверяем той же логикой и теми же словами,
     * чтобы человек увидел причину сразу и переименовал файл, а не гадал, почему
     * расписание не появилось.
     */
    private static String courseRejection(String name) {
        String[] parts = name.split(" ");
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].equals("курс")) continue;
            try {
                Integer.parseInt(parts[i - 1]);
                return null;
            } catch (NumberFormatException ignored) {
                return "Перед словом «курс» в имени файла должен стоять номер курса, "
                        + "а стоит: " + parts[i - 1];
            }
        }
        return "В имени файла нет номера курса: разбор ищет слово «курс» и число перед ним "
                + "(например «Расписание 2 курс ОФО СПО.xlsx»)";
    }

}
