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
 * <p>Слот собирается из того же, чем пара уже описана в файле: день недели, чётность, номер
 * пары и время. Отдельной сущностью он нужен затем, что сетка — свойство версии, а не строки:
 * по ней строят пустое расписание, ищут окна у преподавателя и проверяют, что две пары не
 * встали в одно место. Пока слот жил колонками внутри пары, ни одного из этих вопросов
 * задать было нельзя.
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
     * @return сколько слотов пришлось создать — в норме их немного и только на первой загрузке
     */
    public int resolve(Version version, List<Schedule> schedule) {
        Map<String, TimeSlot> grid = new HashMap<>();
        for (TimeSlot slot : timeSlotRepository.findGrid(version.getId())) {
            grid.put(key(slot.getDayWeek(), slot.getWeekCount(), slot.getLessonCount(), slot.getTimeRange()), slot);
        }

        List<TimeSlot> created = new ArrayList<>();
        for (Schedule pair : schedule) {
            String timeRange = pair.getTimePeriod() == null ? "" : pair.getTimePeriod().trim();
            String key = key(pair.getDayWeek(), pair.getWeekCount(), pair.getLessonCount(), timeRange);

            TimeSlot slot = grid.get(key);
            if (slot == null) {
                slot = new TimeSlot();
                slot.setVersion(version);
                slot.setDayWeek(pair.getDayWeek());
                slot.setWeekCount(pair.getWeekCount());
                slot.setLessonCount(pair.getLessonCount());
                slot.setTimeRange(timeRange);
                slot.setStartedAt(bound(timeRange, 0));
                slot.setFinishedAt(bound(timeRange, 1));
                grid.put(key, slot);
                created.add(slot);
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
