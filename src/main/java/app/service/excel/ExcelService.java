package app.service.excel;

import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
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

    // очно / очно-заочно
    private record CellWrapper(boolean merged, String value) {}

    // заочно
    private record DistantCellWrapper(String subject, String location, String teacher) {}

    // очно / очно-заочно
    private static final Map<String, Integer> lessonNumberMap = Map.of(
            "08.00-09.30", 1,
            "09.40-11.10", 2,
            "11.30-13.00", 3,
            "13.10-14.40", 4,
            "14.50-16.20", 5,
            "16.30-18.00", 6,
            "18.10-19.40", 7
    );

    // заочно
    private static final Map<String, Integer> lessonDistantMap = Map.of(
            "08:00", 1,
            "09:40", 2,
            "11:30", 3,
            "13:10", 4,
            "14:50", 5,
            "16:30", 6,
            "18:10", 7
    );

    private static final Map<String, Teacher> teacherCache = new HashMap<>();

    /**
     * Возвращает коллекцию List<Schedule> объекта расписания
     * @param fileName имя файла
     * @param inputStream поток байтов файла
     * @return List<Schedule>
     */
    public List<Schedule> parseWorkbook(String fileName, InputStream inputStream) throws IOException {
        log.info("Начало парсинга файла: {}", fileName);
        List<Schedule> schedules = new ArrayList<>();
        HashMap<Integer, List<Group>> groups = new HashMap<>();
        try {
            Workbook workbook = WorkbookFactory.create(inputStream);
            for (int l = 0; l < (fileName.contains("ЗФО") ? workbook.getNumberOfSheets() : 1); l++) {

                Sheet sheet = workbook.getSheetAt(l);
                log.info("Обработка листа: {}", sheet.getSheetName());
                log.info("Количество строк: {}", sheet.getLastRowNum() + 1);

                boolean fullDistantForm = fileName.contains("ЗФО");
                boolean weekOdd = true; // нечетная
                int i = fullDistantForm ? 6 : 5; // индексация с 6 строки если очно или очно-заочно | 7 строки если заочно
                for (Row row : sheet) {

                    if (row.getRowNum() >= i && row.getRowNum() <= 90) {

                        if (fullDistantForm) { // при заочке пропускается четный ряд по индексу
                            if (row.getRowNum() != i && row.getRowNum() % 2 == 0) {
                                continue;
                            }
                        }

                        log.info("Переход на строку: {}", row.getRowNum());

                        for (Cell cell : row) {

                            if (fullDistantForm) { // при заочке пропускается четная ячейка по индексу
                                if ((cell.getColumnIndex() % 2 == 0)) {
                                    continue;
                                }
                            }

                            // перебор групп на курсе | индексация с 3 колонки
                            if (cell.getColumnIndex() >= (fullDistantForm ? 3 : 2) && row.getRowNum() == (fullDistantForm ? 6 : 5) && !cell.getStringCellValue().isBlank()) {

                                String name = cell.getStringCellValue().trim().replaceAll(",", "");
                                String[] nameParts = name.trim().split(" ");
                                List<Group> createdGroups = new ArrayList<>();

                                // парсинг групп в ячейках через пробел
                                for (String part : nameParts) {
                                    if (!part.isEmpty()) {

                                        Group group = new Group();

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

                                        String level = part.contains("СПО") ? "СПО" : part.contains("Мг") ? "Магистратура" : "Бакалавриат";

                                        group.setName(part);
                                        group.setCourse(course);
                                        group.setLevel(level);
                                        group.setStudyForm(fileName.contains("ОФО") ? "Очная" : fileName.contains("ЗФО") ? "Заочная" : "Очно-заочная");

                                        log.info("Индексирована группа: {}", group.getName());
                                        createdGroups.add(group);

                                    }
                                }
                                groups.put(cell.getColumnIndex(), createdGroups);
                                log.info("groups: " + groups.entrySet().toString());

                            }
                            else if (cell.getColumnIndex() >= (fullDistantForm ? 3 : 2)) { // перебор расписания

                                // заочно
                                if (fullDistantForm) {

                                    // получить данные ячейки
                                    DistantCellWrapper currentCell = getDistantCellValue(sheet, row.getRowNum(), cell.getColumnIndex());

                                    if (currentCell == null) continue;

                                    if (currentCell.subject.isBlank() || currentCell.subject.isEmpty()) continue;

                                    log.info("Парсинг ячейки (строка {}, колонка {}): {}", row.getRowNum(), cell.getColumnIndex(), cell.getStringCellValue());

                                    log.info("ДАННЫЕ: " + currentCell.subject + " " + currentCell.location + " " + currentCell.teacher);

                                    // поиск групп в ячейке (общее расписание)
                                    List<Group> indexedDistantGroups = groups.get(cell.getColumnIndex()); //groups.get((cell.getColumnIndex() - 3) / 2);

                                    for (Group group : indexedDistantGroups) {

                                        // у заочной формы нет четности недели
                                        for (int w = 1; w <= 2; w++) {

                                            Schedule schedule = new Schedule();

                                            StringBuilder name = new StringBuilder(currentCell.subject.trim());
                                            String type = "";
                                            if (name.toString().contains("Экзамен") || name.toString().contains("Зачёт")) {
                                                String[] tempSplit = name.toString().split(":");
                                                type = tempSplit[0];
                                                log.info("type: " + type);
                                                name = new StringBuilder();
                                                for (int part = 1; part < tempSplit.length; part++) {
                                                    String add = (part == 1) ? tempSplit[part].substring(1) : tempSplit[part];
                                                    name.append(add);
                                                }
                                            }

                                            byte[] rgb = new byte[]{(byte) 255, (byte) 255, (byte) 255};
                                            Color color = fileName.contains(".xlsx") ? new XSSFColor(rgb, null) : HSSFColor.HSSFColorPredefined.WHITE.getColor();
                                            Color cellColor = cell.getCellStyle().getFillForegroundColorColor();

                                            log.info("color: " + color);
                                            log.info("cellColor: " + cellColor);

                                            // если цвет ячейки красный
                                            if (cellColor != null && !isWhiteColor(cellColor, color) && !(type.contains("Экзамен") || type.contains("Зачёт"))) type = "Вводная пара";

                                            schedule.setLessonName(name.toString());
                                            schedule.setLessonType(type);

                                            // идемпотентность для сохранения (анти-дубликат)
                                            if (currentCell.teacher != null && !currentCell.teacher.isBlank()) {

                                                Teacher teacher = null;
                                                String[] tempSplit = currentCell.teacher.trim().split(" ");

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
                                                            else t.setLabel(currentCell.teacher.trim());
                                                            teacher = t;
                                                            break;
                                                        }

                                                    }

                                                }
                                                else {
                                                    Teacher t = new Teacher();
                                                    if (tempSplit.length == 3) t.setLabel(tempSplit[1] + " " + tempSplit[2]);
                                                    else t.setLabel(currentCell.teacher.trim());
                                                    teacher = t;
                                                }

                                                schedule.setTeacher(teacher);
                                                teacherCache.put(currentCell.teacher.trim(), teacher);
                                            }

                                            // время занятия
                                            String timePeriod = getCellValueWithMerge(sheet, row.getRowNum(), 2).value.trim();
                                            timePeriod = timePeriod.equals("8:00") ? "08:00" : timePeriod.equals("9:40") ? "09:40" : timePeriod;

                                            log.info("timePeriod: " + timePeriod);
                                            log.info("count: " + lessonDistantMap.get(timePeriod));

                                            // день недели (0 - день недели | 1 - дата)
                                            String cellDate = getCellValueWithMerge(sheet, row.getRowNum(), 1).value.trim();
                                            String dayWeek = (!cellDate.isBlank() && !cellDate.isEmpty()) ? cellDate.split(" ")[0] : getCellValueWithMerge(sheet, timePeriod.contains("08:00") ? row.getRowNum() + 2 : row.getRowNum(), 1).value.trim().split(" ")[0];;
                                            String pinnedDate = (!cellDate.isBlank() && !cellDate.isEmpty()) ? cellDate.split(" ")[1] : getCellValueWithMerge(sheet, timePeriod.contains("08:00") ? row.getRowNum() + 2 : row.getRowNum(), 1).value.trim().split(" ")[1];;
                                            if (!dayWeek.isBlank()) dayWeek = dayWeek.substring(0, 1).toUpperCase() + dayWeek.substring(1).toLowerCase();

                                            schedule.setGroup(group);
                                            schedule.setAuditory(currentCell.location == null ? "Нет аудитории" : currentCell.location.trim());
                                            schedule.setDayWeek(dayWeek);

                                            // конвертировать время из одного формата в другой
                                            String finalTimePeriod = timePeriod;
                                            schedule.setTimePeriod(lessonNumberMap.entrySet().stream()
                                                    .filter(entry -> entry.getValue().equals(lessonDistantMap.get(finalTimePeriod)))
                                                    .map(Map.Entry::getKey)
                                                    .findFirst().orElse("Время не найдено"));

                                            schedule.setLessonCount(lessonDistantMap.get(timePeriod));
                                            schedule.setWeekCount(w);
                                            schedule.setEiosLink("");
                                            schedule.setPinnedDate(pinnedDate);

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

                                } // очно / очно-заочно
                                else {

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

                                    // поиск групп в ячейке (общее расписание)
                                    List<Group> indexedGroups = groups.get(cell.getColumnIndex()); //groups.get(cell.getColumnIndex() - 2);

                                    for (Group group : indexedGroups) {

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

                                            if (lines.length == 1) {
                                                tempSplit = currentCell.value.split("https");
                                                lines = new String[] { tempSplit[0].trim() };
                                            }
                                            else {
                                                tempSplit = lines[1].split("https");
                                                lines = new String[] { lines[0], tempSplit[0].trim() };
                                            }
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

                                        schedule.setGroup(group);
                                        schedule.setLessonType(type);
                                        schedule.setLessonName(subject.trim());
                                        schedule.setAuditory(auditory == null ? "Нет аудитории" : auditory.trim());
                                        schedule.setDayWeek(dayWeek);
                                        schedule.setTimePeriod(timePeriod);
                                        schedule.setLessonCount(lessonNumberMap.get(timePeriod));
                                        schedule.setWeekCount(weekOdd ? 1 : 2);
                                        schedule.setEiosLink(eiosLink);
                                        schedule.setPinnedDate("");

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

                            }
                        }
                        if (row.getRowNum() != 5 && !fullDistantForm) weekOdd = !weekOdd;
                    }
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
     * Возвращает значение первой ячейки в смерженном секторе (очная / очно-заочная форма)
     * @param sheet страница
     * @param rowIndex индекс строки
     * @param colIndex индекс ячейки
     * @return CellWrapper
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

    /**
     * Возвращает значение первой ячейки в смерженном секторе (заочная форма)
     * @param sheet страница
     * @param rowIndex индекс строки
     * @param colIndex индекс ячейки
     * @return DistantCellWrapper
     */
    private DistantCellWrapper getDistantCellValue(Sheet sheet, int rowIndex, int colIndex) {

        // является ли ячейка частью объединённого диапазона
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress range = sheet.getMergedRegion(i);
            if (range.isInRange(rowIndex, colIndex)) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                Cell firstCell = firstRow.getCell(colIndex);

                if (firstCell != null && (firstCell.getCellType() != CellType.BLANK || !firstCell.getStringCellValue().isEmpty())) {

                    CellType type = firstRow.getCell(colIndex + 1).getCellType();
                    String location = type == CellType.NUMERIC ? String.valueOf((int) firstRow.getCell(colIndex + 1).getNumericCellValue()) : firstRow.getCell(colIndex + 1).getStringCellValue();

                    if (firstCell.getStringCellValue().contains("CОБРАНИЕ")) {
                        return new DistantCellWrapper(firstCell.getStringCellValue(), location, "");
                    }
                    else {
                        String teacher = sheet.getRow(range.getLastRow() + 1).getCell(colIndex).getStringCellValue();
                        return new DistantCellWrapper(firstCell.getStringCellValue(), location, teacher);
                    }
                }
                else
                    return null;
            }
        }

        // если ячейка не является частью объединённого диапазона
        Row row = sheet.getRow(rowIndex);
        if (row != null) {
            Cell cell = row.getCell(colIndex);
            if (cell != null && (cell.getCellType() != CellType.BLANK || !cell.getStringCellValue().isEmpty())) {

                CellType type = row.getCell(colIndex + 1).getCellType();
                String location = type == CellType.NUMERIC ? String.valueOf((int) row.getCell(colIndex + 1).getNumericCellValue()) : row.getCell(colIndex + 1).getStringCellValue();

                if (cell.getStringCellValue().contains("CОБРАНИЕ")) {
                    return new DistantCellWrapper(cell.getStringCellValue(), location, "");
                }
                else {
                    String teacher = sheet.getRow(rowIndex + 1).getCell(colIndex).getStringCellValue();
                    return new DistantCellWrapper(cell.getStringCellValue(), location, teacher);
                }
            }
        }

        return new DistantCellWrapper("", "", "");
    }

    /**
     * Проверка цвета ячейки независимо от формата файла Excel
     * @param color1 цвет 1
     * @param color2 цвет 2
     * @return boolean
     */
    private boolean isWhiteColor(Color color1, Color color2) {
        if (color1 instanceof XSSFColor && color2 instanceof XSSFColor) {
            log.info("check color1: " + Arrays.toString(((XSSFColor) color1).getRGB()));
            log.info("check color2: " + Arrays.toString(((XSSFColor) color2).getRGB()));
            return Arrays.equals(((XSSFColor) color1).getRGB(), ((XSSFColor) color2).getRGB());
        } else if (color1 instanceof HSSFColor && color2 instanceof HSSFColor) {
            return ((HSSFColor) color1).getIndex() == ((HSSFColor) color2).getIndex();
        }
        return false;
    }

}