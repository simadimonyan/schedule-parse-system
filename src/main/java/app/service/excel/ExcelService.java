package app.service.excel;

import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Slf4j
@Service
public class ExcelService {

    private record CellWrapper(boolean merged, String value) {}

    private static final Map<String, Integer> lessonNumberMap = Map.of(
            "08.00-09.30", 1,
            "09.40-11.10", 2,
            "11.30-13.00", 3,
            "13.10-14.40", 4,
            "14.50-16.20", 5,
            "16.30-18.00", 6,
            "18.10-19.40", 7
    );

    private static final Map<String, Teacher> teacherCache = new HashMap<>();

    public List<Schedule> parseWorkbook(String fileName, InputStream inputStream) throws IOException {
        log.info("Начало парсинга файла: {}", fileName);
        List<Schedule> schedules = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        try {
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Обработка листа: {}", sheet.getSheetName());
            log.info("Количество строк: {}", sheet.getLastRowNum() + 1);

            boolean weekOdd = true; // нечетная
            int i = 5; // индексация с 6 строки
            for (Row row : sheet) {
                if (row.getRowNum() >= i && row.getRowNum() <= 90) {

                    log.info("Переход на строку: {}", row.getRowNum());

                    for (Cell cell : row) {

                        log.debug("Строка {}, колонка {}, значение: '{}'", row.getRowNum(), cell.getColumnIndex(), cell);

                        // перебор групп на курсе | индексация с 3 колонки
                        if (cell.getColumnIndex() >= 2 && row.getRowNum() == 5 && !cell.getStringCellValue().isBlank()) {

                            Group group = new Group();

                            String name = cell.getStringCellValue();

                            String[] nameParts = fileName.split(" ");
                            int index = IntStream.range(0, nameParts.length)
                                    .filter(s -> nameParts[s].equals("курс"))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("Файл не содержит слово 'курс'"));
                            int course;
                            try {
                                course = Integer.parseInt(nameParts[index - 1]);
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Не удалось определить номер курса в имени файла: " + fileName, e);
                            }

                            String level = name.contains("СПО") ? "СПО" : name.contains("Мг") ? "Магистратура" : "Бакалавриат";

                            group.setName(name);
                            group.setCourse(course);
                            group.setLevel(level);

                            log.info("Индексирована группа: {}", group.getName());
                            groups.add(group);
                        }
                        else if (cell.getColumnIndex() >= 2) { // перебор расписания

                            // получить данные ячейки (merged и нет)
                            CellWrapper currentCell = getCellValueWithMerge(sheet, row.getRowNum(), cell.getColumnIndex());

                            if (currentCell.value.isBlank() || currentCell.value.isEmpty()) continue;

                            log.debug("Парсинг ячейки (строка {}, колонка {}): {}", row.getRowNum(), cell.getColumnIndex(), cell.getStringCellValue());

                            /*
                              1 - мерж нет - создаем на 1 неделю
                              2 - мерж нет - создаем на 2 неделю
                              3 - мерж да - создаем на 1 неделю
                              4 - мерж да - создаем на 2 неделю
                              5 - мерж нет - создаем на 1 неделю
                              6 - мерж нет - создаем на 2 неделю
                              7 - мерж да - пустой пропускаем
                              8 - мерж да - пустой пропускаем
                              ...
                              15 - мерж да - создаем 1 неделю
                              16 - мерж да - создаем 2 неделю
                             */

                            Schedule schedule = new Schedule();
                            String[] lines = currentCell.value.split("\n");
                            String firstLine = lines[0].trim();
                            String type = firstLine.substring(0, firstLine.indexOf(".")).trim().equals("л") ? "Лекция" : "Практика";
                            String subject = firstLine.substring(firstLine.indexOf(".") + 1).trim();

                            String secondLine = lines.length > 1 ? lines[1].replaceAll("\\s+", " ").trim() : "";

                            String label = null;
                            String auditory = null;

                            if (!secondLine.isEmpty()) {
                                Matcher mTeacherAuditory = Pattern.compile(
                                        "^(?<teacher>.+?)\\s+(?<auditory>\\S+)$",
                                        Pattern.UNICODE_CASE | Pattern.DOTALL
                                ).matcher(secondLine);

                                if (mTeacherAuditory.matches()) {
                                    label = mTeacherAuditory.group("teacher") != null ? mTeacherAuditory.group("teacher").trim() : null;
                                    auditory = mTeacherAuditory.group("auditory") != null ? mTeacherAuditory.group("auditory").trim() : null;
                                } else {
                                    // Строка содержит только аудиторию
                                    auditory = secondLine.trim();
                                }
                            }

                            // идемпотентность для сохранения (анти-дубликат)
                            if (label != null && !label.isBlank()) {
                                Teacher teacher = teacherCache.computeIfAbsent(label.trim(), l -> {
                                    Teacher t = new Teacher();
                                    t.setLabel(l);
                                    return t;
                                });
                                schedule.setTeacher(teacher);
                            }

                            // день недели
                            String dayWeek = getCellValueWithMerge(sheet, row.getRowNum(), 0).value.trim();
                            if (!dayWeek.isBlank()) dayWeek = dayWeek.substring(0, 1).toUpperCase() + dayWeek.substring(1).toLowerCase();

                            // время занятия
                            String timePeriod = getCellValueWithMerge(sheet, row.getRowNum(), 1).value.trim();

                            schedule.setGroup(groups.get(cell.getColumnIndex() - 2));
                            schedule.setLessonType(type);
                            schedule.setLessonName(subject.trim());
                            schedule.setAuditory(auditory == null ? "Нет аудитории" : auditory.trim());
                            schedule.setDayWeek(dayWeek);
                            schedule.setTimePeriod(timePeriod);
                            schedule.setLessonCount(lessonNumberMap.get(timePeriod));
                            schedule.setWeekCount(weekOdd ? 1 : 2);

                            schedules.add(schedule);
                            log.info("Индексировано занятие: {}:{}:{} | {} | {} - {} - {} - {}",
                                    schedule.getDayWeek(),
                                    schedule.getTimePeriod(),
                                    schedule.getWeekCount(),
                                    schedule.getGroup().getName(),
                                    schedule.getLessonType(),
                                    schedule.getLessonName(),
                                    schedule.getTeacher() == null ? "Нет преподавателя" : schedule.getTeacher().getLabel(),
                                    schedule.getAuditory() == null ? "Нет аудитории" : schedule.getAuditory()
                            );

                        }
                    }
                    if (row.getRowNum() != 5) weekOdd = !weekOdd;
                }
            }
        } catch(IOException e) {
            log.error("Ошибка при парсинге файла: {}", fileName, e);
            throw e;
        }
        log.info("Завершено парсинг файла: {}", fileName);
        return schedules;
    }

    /**
     * Возвращает значение первой ячейки в смерженном секторе
     * @param sheet страница
     * @param rowIndex индекс строки
     * @param colIndex индекс ячейки
     * @return String
     */
    private CellWrapper getCellValueWithMerge(Sheet sheet, int rowIndex, int colIndex) {
        // является ли ячейка частью объединённого диапазона
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress range = sheet.getMergedRegion(i);
            if (range.isInRange(rowIndex, colIndex)) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                Cell firstCell = firstRow.getCell(range.getFirstColumn());
                return new CellWrapper(true, firstCell != null ? firstCell.toString() : "");
            }
        }
        // если ячейка не является частью объединённого диапазона, возвращаем её собственное значение
        Row row = sheet.getRow(rowIndex);
        if (row != null) {
            Cell cell = row.getCell(colIndex);
            if (cell != null && (cell.getCellType() != CellType.BLANK || !cell.getStringCellValue().isEmpty())) {
                return new CellWrapper(false, cell.toString());
            }
        }
        return new CellWrapper(false, "");
    }

}
