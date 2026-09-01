package app.service.max;

import app.repository.dao.GroupRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.models.entity.Config;
import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import app.service.persistence.SchedulePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Синхронизация со сторонним сервером расписания (rasp.imsit.ru, приложение в MAX).
 *
 * Сервер отдаёт две страницы:
 *   ?page=search&study_form=...                     — datalist#group-list со всеми группами формы обучения;
 *   ?page=schedule&mode=student&study_form=...&group=<название>&show=1
 *                                                   — календарь на весь учебный год для одной группы.
 *
 * Параметров week и day больше нет: за один запрос приходит весь год, четность недели
 * лежит на самой паре в data-week. Календарь дублируется в вёрстке (сетка месяца для
 * десктопа и недельные слайды для мобильного), поэтому пары берутся только из
 * .calendar-date-cell, а даты дедуплицируются: соседние месяцы делят пограничные дни.
 */
@Slf4j
@Service
public class MaxService {

    private final SchedulePersistenceService persistenceService;
    private final GroupRepository groupRepository;
    private final ScheduleRepository scheduleRepository;
    private final ExternalClient externalClient;

    private static final DateTimeFormatter PINNED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    private static final Map<Integer, String> lessonNumberMap = Map.of(
            1, "08.00-09.30",
            2, "09.40-11.10",
            3, "11.30-13.00",
            4, "13.10-14.40",
            5, "14.50-16.20",
            6, "16.30-18.00",
            7, "18.10-19.40"
    );

    private static final Map<String, Integer> lessonTimeMap = Map.of(
            "08.00-09.30", 1,
            "09.40-11.10", 2,
            "11.30-13.00", 3,
            "13.10-14.40", 4,
            "14.50-16.20", 5,
            "16.30-18.00", 6,
            "18.10-19.40", 7
    );

    private static final Map<DayOfWeek, String> dayWeekMap = Map.of(
            DayOfWeek.MONDAY, "Понедельник",
            DayOfWeek.TUESDAY, "Вторник",
            DayOfWeek.WEDNESDAY, "Среда",
            DayOfWeek.THURSDAY, "Четверг",
            DayOfWeek.FRIDAY, "Пятница",
            DayOfWeek.SATURDAY, "Суббота",
            DayOfWeek.SUNDAY, "Воскресенье"
    );

    private static final Map<String, String> lessonTypeMap = Map.of(
            "л", "Лекция",
            "лаб", "Лабораторная",
            "пр", "Практика"
    );

    @Autowired
    public MaxService(
            SchedulePersistenceService persistenceService,
            GroupRepository groupRepository,
            ScheduleRepository scheduleRepository,
            ExternalClient externalClient
    ) {
        this.persistenceService = persistenceService;
        this.scheduleRepository = scheduleRepository;
        this.groupRepository = groupRepository;
        this.externalClient = externalClient;
    }

    @Async
    @Transactional
    public void loadAndPersistGroups() {
       log.info("Начало синхронизации групп со сторонним сервером!");

       Map<String, List<String>> map = getGroups();

       // группа может встретиться в обеих формах обучения — очная приоритетнее
       Set<String> seen = new HashSet<>();
       List<Group> groups = new ArrayList<>();

       for (Map.Entry<String, List<String>> entry : map.entrySet()) {
           for (String label : entry.getValue()) {
               if (!seen.add(label)) continue;

               Group group = new Group();
               group.setCourse(extractCourse(label));
               group.setStudyForm(entry.getKey().equals("очная") ? "Очная" : "Заочная");
               group.setLevel(label.contains("СПО") ? "СПО" : label.contains("Мг") ? "Магистратура" : "Бакалавриат");
               group.setName(label);
               groups.add(group);
           }
       }

       persistenceService.persistGroups(groups);
       log.info("Синхронизация групп завершена! Всего групп: {}", groups.size());
    }

    @Async
    @Transactional
    public void loadAndPersistSchedule() {
        Group group;
        try {
            Config config = persistenceService.getConfig("last_external_parsed_group");
            group = groupRepository.findFirstByIdGreaterThanOrderByIdAsc(Long.parseLong(config.getValue())).get();
        }
        catch (Exception e) {
            group = groupRepository.findFirstByIdGreaterThanOrderByIdAsc(0L).get();
        }

        log.info("Начало синхронизации расписания %s со сторонним сервером!".formatted(group.getName()));

        try {
            List<Schedule> schedule = getSchedule(group);

            if (!schedule.isEmpty()) {
                scheduleRepository.deleteAllByGroupId(group.getId());
                persistenceService.persistSchedule(schedule);
            }

        }
        catch (Exception e) {
            log.info("Синхронизация расписания %s произошла с ошибкой!".formatted(group.getName()));
        }
        persistenceService.setConfig("last_external_parsed_group", group.getId().toString());
        log.info("Синхронизация расписания %s завершена!".formatted(group.getName()));
    }

    @Async
    @Transactional
    public void loadAndPersistSchedule(String label) {
        Group group = groupRepository.findByName(label).orElse(null);

        if (group != null) {
            log.info("Начало синхронизации расписания %s со сторонним сервером!".formatted(group.getName()));

            List<Schedule> schedule = getSchedule(group);

            scheduleRepository.deleteAllByGroupId(group.getId());
            persistenceService.persistSchedule(schedule);
            log.info("Синхронизация расписания %s завершена!".formatted(group.getName()));
        }
        else
            log.info("Группа %s не найдена!".formatted(label));
    }

    /** Список групп по формам обучения: ?page=search&study_form=... → datalist#group-list. */
    private Map<String, List<String>> getGroups() {
        Map<String, List<String>> map = new LinkedHashMap<>();

        for (String studyForm : List.of("очная", "заочная")) {
            String response = externalClient.get(uriBuilder -> uriBuilder
                    .queryParam("page", "search")
                    .queryParam("mode", "student")
                    .queryParam("study_form", studyForm)
                    .build());

            List<String> groups = new ArrayList<>();
            if (response != null && !response.isEmpty()) {
                Document doc = Jsoup.parse(response);
                Element element = doc.getElementById("group-list");
                if (element != null) {
                    groups = element.select("option").stream()
                            .map(option -> option.attr("value").trim())
                            .filter(value -> !value.isEmpty())
                            .toList();
                }
                else
                    log.warn("[{}] Список групп не найден: элемент #group-list отсутствует", studyForm);
            }
            else
                log.warn("[{}] Список групп не получен: пустой ответ от сервера", studyForm);

            log.info("[{}] Получено групп: {}", studyForm, groups.size());
            map.put(studyForm, groups);
        }

        return map;
    }

    private List<Schedule> getSchedule(Group group) {
        boolean distant = "Заочная".equals(group.getStudyForm());
        String studyForm = distant ? "заочная" : "очная";

        log.info("Парсинг расписания для группы {} (форма: {}, курс: {})", group.getName(), group.getStudyForm(), group.getCourse());

        String response = externalClient.get(uriBuilder -> uriBuilder
                .queryParam("page", "schedule")
                .queryParam("mode", "student")
                .queryParam("study_form", studyForm)
                .queryParam("group", group.getName())
                .queryParam("show", 1)
                .build());

        if (response == null || response.isEmpty()) {
            log.warn("[{}] [{}] Пустой ответ от сервера", studyForm, group.getName());
            return List.of();
        }

        return parseSchedule(group, response);
    }

    /** Разбор страницы расписания в записи базы. Отделено от загрузки, чтобы разбор можно было проверить тестом. */
    List<Schedule> parseSchedule(Group group, String response) {
        boolean distant = "Заочная".equals(group.getStudyForm());
        String studyForm = distant ? "заочная" : "очная";

        Document doc = Jsoup.parse(response);
        Map<String, Element> days = collectDays(doc);

        if (days.isEmpty()) {
            log.warn("[{}] [{}] Календарь не найден — расписание пустое", studyForm, group.getName());
            return List.of();
        }

        // ключ склейки: за год пара повторяется 20+ раз, в базе нужен один слот двухнедельного цикла
        Map<String, Schedule> unique = new LinkedHashMap<>();
        int cards = 0;
        int skipped = 0;

        for (Map.Entry<String, Element> day : days.entrySet()) {
            LocalDate date;
            try {
                date = LocalDate.parse(day.getKey());
            }
            catch (Exception e) {
                log.warn("[{}] [{}] Неизвестный формат даты: «{}» — день пропущен", studyForm, group.getName(), day.getKey());
                continue;
            }

            String dayWeek = dayWeekMap.get(date.getDayOfWeek());
            String pinnedDate = distant ? date.format(PINNED_DATE_FORMAT) : "";

            for (Element card : day.getValue().getElementsByClass("lesson-card")) {
                cards++;

                String time = card.attr("data-time").trim();
                Integer lessonCount = lessonTimeMap.get(time);
                if (lessonCount == null) {
                    log.warn("[{}] [{}] Неизвестное время пары: «{}» — пара пропущена", studyForm, group.getName(), time);
                    skipped++;
                    continue;
                }

                String subject = card.attr("data-subject").trim();
                String lessonType = lessonTypeMap.getOrDefault(card.attr("data-type").trim(), card.attr("data-type").trim());
                String auditory = card.attr("data-room").trim();
                String teacher = card.attr("data-teacher").trim();

                Integer weekCount;
                if (distant)
                    // у заочки пара привязана к дате, а не к четности — она видна на любой неделе
                    weekCount = 1;
                else {
                    weekCount = parseWeek(card.attr("data-week"));
                    if (weekCount == null) {
                        log.warn("[{}] [{}] Неизвестная четность недели: «{}» — пара пропущена", studyForm, group.getName(), card.attr("data-week"));
                        skipped++;
                        continue;
                    }
                }

                String key = String.join("|", pinnedDate, dayWeek, weekCount.toString(),
                        lessonCount.toString(), subject, lessonType, auditory, teacher);

                // преподаватель ищется в базе только для новой пары: за год одна и та же пара повторяется 20+ раз
                if (unique.containsKey(key)) continue;

                Schedule savable = new Schedule();
                savable.setGroup(group);
                savable.setDayWeek(dayWeek);
                savable.setTimePeriod(lessonNumberMap.get(lessonCount));
                savable.setLessonCount(lessonCount);
                savable.setPinnedDate(pinnedDate);
                savable.setLessonName(subject);
                savable.setWeekCount(weekCount);
                savable.setLessonType(lessonType);
                savable.setAuditory(auditory);
                savable.setEiosLink("");
                savable.setTeacher(persistenceService.getOrPersistTeacher(teacher));

                unique.put(key, savable);
                log.debug("[{}] [{}] Пара: неделя={}, день={}, дата={}, время={}, предмет={}, тип={}, ауд={}, преп={}",
                        studyForm, group.getName(), weekCount, dayWeek, pinnedDate, savable.getTimePeriod(),
                        subject, lessonType, auditory, teacher);
            }
        }

        List<Schedule> schedule = new ArrayList<>(unique.values());

        // заочка дублируется на обе четности: даты сессии не зависят от номера недели
        if (distant) {
            List<Schedule> copies = schedule.stream().map(it -> {
                Schedule copy = new Schedule();
                copy.setGroup(it.getGroup());
                copy.setTeacher(it.getTeacher());
                copy.setDayWeek(it.getDayWeek());
                copy.setTimePeriod(it.getTimePeriod());
                copy.setLessonCount(it.getLessonCount());
                copy.setLessonName(it.getLessonName());
                copy.setLessonType(it.getLessonType());
                copy.setAuditory(it.getAuditory());
                copy.setPinnedDate(it.getPinnedDate());
                copy.setEiosLink(it.getEiosLink());
                copy.setWeekCount(2);
                return copy;
            }).toList();
            schedule.addAll(copies);
        }

        log.info("[{}] [{}] Дней в календаре: {}, пар за год: {}, пропущено: {}, сохранится записей: {}",
                studyForm, group.getName(), days.size(), cards, skipped, schedule.size());

        return schedule;
    }

    /**
     * Дни календаря по дате. Пограничные дни попадают в сетки двух соседних месяцев,
     * поэтому на дату остаётся ячейка с наибольшим числом пар.
     */
    private Map<String, Element> collectDays(Document doc) {
        Map<String, Element> days = new LinkedHashMap<>();

        for (Element cell : doc.getElementsByClass("calendar-date-cell")) {
            String date = cell.attr("data-date").trim();
            if (date.isEmpty()) continue;

            Element previous = days.get(date);
            if (previous == null
                    || previous.getElementsByClass("lesson-card").size() < cell.getElementsByClass("lesson-card").size())
                days.put(date, cell);
        }

        return days;
    }

    private Integer parseWeek(String value) {
        try {
            int week = Integer.parseInt(value.trim());
            return (week == 1 || week == 2) ? week : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    private int extractCourse(String label) {
        try {
            int year = Integer.parseInt(label.split("-")[0]);
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            int currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
            int startYear = 2000 + year;
            int course = currentYear - startYear;

            if (currentMonth >= 9) {
                course += 1;
            }

            int maxCourse;

            if (label.contains("Мг")) {
                maxCourse = 2;
            } else if (label.contains("СПО")) {
                maxCourse = 4;
            } else {
                maxCourse = 4;
            }

            course = Math.min(course, maxCourse);
            course = Math.max(course, 1);

            return course;
        } catch (Exception e) {
            return 0;
        }
    }

}
