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
import java.util.*;
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

                        //log.info("Строка {}, колонка {}, значение: '{}'", row.getRowNum(), cell.getColumnIndex(), cell);

                        // перебор групп на курсе | индексация с 3 колонки
                        if (cell.getColumnIndex() >= 2 && row.getRowNum() == 5 && !cell.getStringCellValue().isBlank()) {

                            Group group = new Group();

                            String name = cell.getStringCellValue().trim();
                            String[] nameParts = name.trim().split(" ");
                            log.info(Arrays.toString(nameParts));

                            if (nameParts.length > 1) {

                                List<String> nonEmptyParts = new ArrayList<>();
                                for (String part : nameParts) {
                                    if (!part.isEmpty()) {
                                        nonEmptyParts.add(part);
                                    }
                                }

                                // если безномерная часть одной группы равна второй "24-ОЗДЗ-01 24-ОЗДЗ-02"
                                if (nonEmptyParts.get(0).substring(0, nonEmptyParts.get(0).length() - 3).equals(nonEmptyParts.get(1).substring(0, nonEmptyParts.get(1).length() - 3))) {
                                    // общее расписание - "XX-XXX-01/02"
                                    name = nonEmptyParts.get(0).substring(0, nonEmptyParts.get(0).length() - 2) // "XX-XXX-"
                                            + nonEmptyParts.get(0).substring(nonEmptyParts.get(0).length() - 2) // 01
                                            + "/"
                                            + nonEmptyParts.get(1).substring(nonEmptyParts.get(1).length() - 2); // 02
                                }
                            }

                            String[] fileNameParts = fileName.split(" ");
                            int index = IntStream.range(0, fileNameParts.length)
                                    .filter(s -> fileNameParts[s].equals("курс"))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("Файл не содержит слово 'курс'"));
                            int course;
                            try {
                                course = Integer.parseInt(fileNameParts[index - 1]);
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

                            log.info("Парсинг ячейки (строка {}, колонка {}): {}", row.getRowNum(), cell.getColumnIndex(), cell.getStringCellValue());

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

                            boolean masterMode = fileName.contains("Мг");

                            String type = "";
                            String subject = "";
                            String label = "";
                            String auditory = "";

                            String[] lines = currentCell.value.trim().split("\n");
                            String eiosLink = "";

                            // парсинг ссылки eios (при наличии)
                            if (currentCell.value.contains("https")) {
                                String[] tempSplit;

                                log.info(Arrays.toString(lines));

                                if (lines.length == 1) {
                                    tempSplit = currentCell.value.split("https");
                                    lines = new String[] { tempSplit[0].trim() };
                                }
                                else {
                                    tempSplit = lines[1].split("https");
                                    lines = new String[] { lines[0], tempSplit[0].trim() };
                                }

                                log.info(Arrays.toString(tempSplit));
                                eiosLink = "https" + tempSplit[1];
                            }

                            // Парсинг ячеек СПО / Бакалавриат
                            if (!masterMode) {

                                String firstLine = lines[0].trim();

                                // разделитель по точке л. пр. лаб.
                                String dotSplit = firstLine.substring(0, firstLine.indexOf(".")).trim();

                                // данные предмета и его типа
                                type = dotSplit.equals("л") ? "Лекция" : dotSplit.equals("лаб") ? "Лабораторная" : "Практика";
                                if (lines.length > 1) subject = firstLine.replaceFirst("^[а-яА-ЯёЁ]+\\.", "").trim();

                                String secondLine = lines.length > 1 ? lines[1].replaceAll("\\s+", " ").trim() : "";

                                label = null;
                                auditory = null;

                                // ячейки пар без ссылок
                                if (!secondLine.isEmpty()) {

                                    Matcher mTeacherAuditory = Pattern.compile(
                                            "^(?<teacher>.+?)\\s+(?<auditory>[0-9]+(?:-[0-9]+)?[а-яa-z]?|с/зал\\.[0-9]+)$",
                                            Pattern.UNICODE_CASE | Pattern.DOTALL
                                    ).matcher(secondLine);

                                    Matcher mTeacherOnly = Pattern.compile(
                                            "^[А-Яа-яЁёA-Za-z-]+\\s+[А-ЯA-Z]\\.\\s*[А-ЯA-Z]?\\.?$",
                                            Pattern.UNICODE_CASE | Pattern.DOTALL
                                    ).matcher(secondLine);

                                    if (mTeacherAuditory.matches()) {
                                        // "Иванов И.И. 2-405"
                                        label = mTeacherAuditory.group("teacher").trim();
                                        auditory = mTeacherAuditory.group("auditory").trim();
                                    } else if (mTeacherOnly.matches()) {
                                        // "Иванов И.И." (только ФИО)
                                        label = secondLine.trim();
                                    } else {
                                        // "2-405" (только аудитория)
                                        auditory = secondLine.trim();
                                    }

                                }
                                else { // ячейки с ссылками

                                    String processedLine = firstLine.trim();

                                    Matcher m = Pattern.compile("^(.*?)\\s+([А-Яа-яЁёA-Za-z-]+)\\s+([А-ЯA-Z]\\.\\s*[А-ЯA-Z]?\\.?)\\s*(https?:.*)?$",
                                            Pattern.UNICODE_CASE | Pattern.DOTALL).matcher(processedLine);

                                    // данные преподавателя и название пары
                                    if (m.matches()) {
                                        String surname = m.group(2);
                                        String initials = m.group(3);
                                        label = surname + " " + initials;
                                        subject = m.group(1).replaceFirst("^[а-яА-ЯёЁ]+\\.", "").trim();
                                    }
                                    else { // нет преподавателя (л.Физическая культура и спорт https://eios.imsit.ru/course/view.php?id=12020)
                                        subject = processedLine.replaceFirst("^[а-яА-ЯёЁ]+\\.", "").trim();;
                                    }

                                }

                            }
                            else { // шаблон относительно инициалов: (пара) (фамилия) И.И (аудитория / ссылка / пустота)

                                Matcher m = Pattern.compile("\\s*(.*?)\\s+([А-Яа-яЁёA-Za-z-]+)\\s+([А-ЯA-Z]\\.[А-ЯA-Z]?\\.?)\\s*(.*)?",
                                        Pattern.UNICODE_CASE | Pattern.DOTALL).matcher(currentCell.value.trim());

                                if (m.matches()) {
                                    subject = m.group(1).trim();
                                    String surname = m.group(2);
                                    String initials = m.group(3);
                                    String end = m.group(4);

                                    label = surname + " " + initials;

                                    if (end != null && !end.contains("https")) {
                                        auditory = end.trim();
                                    }
                                } else {
                                    log.error("No match found for: " + currentCell.value.trim());
                                    log.debug("Cell value: '" + currentCell.value.trim() + "'");
                                }

                            }

                            // идемпотентность для сохранения (анти-дубликат)
                            if (label != null && !label.isBlank()) {

                                Teacher teacher = null;
                                String[] tempSplit = label.trim().split(" ");

                                if (!teacherCache.isEmpty()) {

                                    for (String teacherLabel : teacherCache.keySet()) {

                                        // Наличие приставки или звания
                                        if (tempSplit.length == 3 && teacherLabel.contains(tempSplit[1])) {
                                            teacher = teacherCache.get(teacherLabel);
                                            break;
                                        } // Отсутствие приставки или звания
                                        else if (tempSplit.length == 2 && teacherLabel.contains(tempSplit[0])) {
                                            teacher = teacherCache.get(teacherLabel);
                                            break;
                                        }
                                        else {
                                            Teacher t = new Teacher();
                                            if (tempSplit.length == 3) t.setLabel(tempSplit[1] + " " + tempSplit[2]);
                                            else t.setLabel(label.trim());
                                            teacher = t;
                                            break;
                                        }

                                    }

                                }
                                else {
                                    Teacher t = new Teacher();
                                    if (tempSplit.length == 3) t.setLabel(tempSplit[1] + " " + tempSplit[2]);
                                    else t.setLabel(label.trim());
                                    teacher = t;
                                }

                                schedule.setTeacher(teacher);
                                teacherCache.put(label.trim(), teacher);
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
                            schedule.setEiosLink(eiosLink);

                            schedules.add(schedule);
                            log.info("Индексировано занятие: {}:{}:{} | {} | {} - {} - {} - {} | {}",
                                    schedule.getDayWeek(),
                                    schedule.getTimePeriod(),
                                    schedule.getWeekCount(),
                                    schedule.getGroup().getName(),
                                    schedule.getLessonType(),
                                    schedule.getLessonName(),
                                    schedule.getTeacher() == null ? "Нет преподавателя" : schedule.getTeacher().getLabel(),
                                    schedule.getAuditory() == null ? "Нет аудитории" : schedule.getAuditory(),
                                    schedule.getEiosLink()
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
