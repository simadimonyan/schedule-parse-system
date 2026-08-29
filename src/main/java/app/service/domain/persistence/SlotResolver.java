package app.service.domain.persistence;

import app.repository.dao.TimeSlotRepository;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.TimeSlot;
import app.repository.models.entity.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Подставляет разобранным парам ячейку недельной сетки.
 *
 * <p>Место в неделе приходит заготовкой слота ({@link TimeSlot#draft}): разбор файла и пачка
 * редактора называют день, чётность, номер пары и время, но какой это слот версии — знает
 * только сетка. Отдельной сущностью слот нужен затем, что сетка — свойство версии, а не
 * строки: по ней строят пустое расписание, ищут окна у преподавателя и проверяют, что две
 * пары не встали в одно место. Пока те же значения дублировались колонками внутри пары, эти
 * вопросы задавались двум источникам сразу и получали расходящиеся ответы.
 *
 * <p>Сетка версии читается разом и держится в памяти на весь разбор: файл даёт тысячи строк
 * на несколько десятков слотов, и поштучный поиск в базе был бы тысячей запросов ради
 * повторяющихся ответов.
 */
@Slf4j
@Service
public class SlotResolver {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private final TimeSlotRepository timeSlotRepository;

    public SlotResolver(TimeSlotRepository timeSlotRepository) {
        this.timeSlotRepository = timeSlotRepository;
    }

    /**
     * Проставляет парам слоты версии, заводя недостающие.
     *
     * <p>Пара приходит с заготовкой слота вместо ссылки. Совпала с сеткой — заготовка
     * выбрасывается, пара встаёт в готовую ячейку; не совпала — заготовка ею и становится.
     * Уже разрешённый слот (с идентификатором) не трогается: так пары, пришедшие вперемешку с
     * копированием версии, не заводят сетке двойников.
     *
     * @return сколько слотов пришлось создать — в норме их немного и только на первой загрузке
     */
    public int resolve(Version version, List<Schedule> schedule) {
        Map<String, TimeSlot> grid = new HashMap<>();
        for (TimeSlot slot : timeSlotRepository.findGrid(version.getId())) {
            grid.put(key(slot.getDayWeek(), slot.getWeekCount(), slot.getLessonCount(), slot.getTimeRange()), slot);
        }

        List<TimeSlot> created = new ArrayList<>();
        for (Schedule pair : schedule) {
            TimeSlot draft = pair.getSlot();

            // места в сетке у пары нет вовсе — ни ячейки, ни заготовки. Ставить её некуда:
            // выборки расписания идут через слот, и такая пара просто не нашлась бы
            if (draft == null) {
                log.error("Пара «{}» группы {} пришла без места в сетке — слот не подобран",
                        pair.getLessonName(), pair.getGroupMasterId());
                continue;
            }

            if (draft.getId() != null) continue;

            String timeRange = draft.getTimeRange() == null ? "" : draft.getTimeRange().trim();
            String key = key(draft.getDayWeek(), draft.getWeekCount(), draft.getLessonCount(), timeRange);

            TimeSlot slot = grid.get(key);
            if (slot == null) {
                draft.setVersion(version);
                draft.setTimeRange(timeRange);
                draft.setStartedAt(bound(timeRange, 0));
                draft.setFinishedAt(bound(timeRange, 1));
                grid.put(key, draft);
                created.add(draft);
                slot = draft;
            }
            pair.setSlot(slot);
        }

        if (!created.isEmpty()) {
            timeSlotRepository.saveAll(created);
            log.info("В версию {} добавлено {} слотов сетки", version.getId(), created.size());
        }
        return created.size();
    }

    /**
     * Граница слота из строки вида «8:00-9:30».
     *
     * <p>Формат ячейки задаёт составитель файла, и он не обязан быть узнаваемым. Неразобранное
     * время — не ошибка разбора: слот всё равно определён своим {@code time_range}, а колонки
     * с границами остаются пустыми до тех пор, пока их кто-нибудь не заполнит руками.
     */
    private static LocalTime bound(String timeRange, int index) {
        if (timeRange == null || timeRange.isBlank()) return null;

        String[] parts = timeRange.split("-");
        if (parts.length != 2) return null;

        try {
            // шаблон «H:mm» разбирает и «8:00», и «08:00»: при чтении H принимает одну цифру
            // или две, и приводить строку к одному виду не нужно
            return LocalTime.parse(parts[index].trim(), TIME);
        } catch (Exception e) {
            log.debug("Время слота не разобрано: {}", timeRange);
            return null;
        }
    }

    private static String key(String dayWeek, Integer weekCount, Integer lessonCount, String timeRange) {
        return dayWeek + "|" + weekCount + "|" + lessonCount + "|" + (timeRange == null ? "" : timeRange);
    }

}
