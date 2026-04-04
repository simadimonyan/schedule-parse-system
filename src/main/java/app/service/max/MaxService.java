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
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MaxService {

    private final SchedulePersistenceService persistenceService;
    private final GroupRepository groupRepository;
    private final ScheduleRepository scheduleRepository;
    private final WebClient webClient;

    // очно / очно-заочно
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

    private static final Map<String, String> dayWeekDistantMap = Map.of(
            "пн", "Понедельник",
            "вт", "Вторник",
            "ср", "Среда",
            "чт", "Четверг",
            "пт", "Пятница",
            "сб", "Суббота"
    );

    @Autowired
    public MaxService(
            SchedulePersistenceService persistenceService,
            GroupRepository groupRepository,
            ScheduleRepository scheduleRepository,
            String url
    ) {
        this.persistenceService = persistenceService;
        this.scheduleRepository = scheduleRepository;
        this.groupRepository = groupRepository;
        this.webClient = WebClient.create(url);
    }

    @Async
    @Transactional
    public void loadAndPersistGroups() {
       log.info("Начало синхронизации групп со сторонним сервером!");

       Map<String, ArrayList<String>> map = getGroups();
       List<Group> groups = map.values().stream().flatMap(Collection::stream).map(label -> {
           Group group = new Group();
           group.setCourse(extractCourse(label));
           group.setStudyForm(map.get("очная").contains(label) ? "Очная" : "Заочная");
           group.setLevel(label.contains("СПО") ? "СПО" : label.contains("Мг") ? "Магистратура" : "Бакалавриат");
           group.setName(label);
           return group;
       }).toList();

       persistenceService.persistGroups(groups);
       log.info("Синхронизация групп завершена!");
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

        List<Schedule> schedule = getSchedule(group);

        scheduleRepository.deleteAllByGroupId(group.getId());
        persistenceService.persistSchedule(schedule);
        persistenceService.setConfig("last_external_parsed_group", group.getId().toString());
        log.info("Синхронизация расписания %s завершена!".formatted(group.getName()));
    }

    @Async
    @Transactional
    public void loadAndPersistSchedule(String label) {
        Group group = groupRepository.findByName(label).get();

        log.info("Начало синхронизации расписания %s со сторонним сервером!".formatted(group.getName()));

        if (group != null) {
            List<Schedule> schedule = getSchedule(group);

            scheduleRepository.deleteAllByGroupId(group.getId());
            persistenceService.persistSchedule(schedule);
            log.info("Синхронизация расписания %s завершена!".formatted(group.getName()));
        }
        else
            log.info("Группа %s не найдена!".formatted(label));
    }

    private Map<String, ArrayList<String>> getGroups() {
        Map<String, ArrayList<String>> map = new HashMap<>();

        for (int i = 0; i <= 1; i++) {
            int finalI = i;
            String studyForm = (finalI == 0) ? "очная" : "заочная";
            String response = webClient.get().uri(uriBuilder -> uriBuilder
                         .queryParam("study_form", studyForm)
                         .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(e -> log.error("Ошибка сети: {}", e.getMessage()))
                    .onErrorReturn("")
                    .block();

            ArrayList<String> groups = new ArrayList<>();
            if (response != null && !response.isEmpty()) {
                Document doc = Jsoup.parse(response);
                Element element = doc.getElementById("group-list");
                if (element != null) {
                    groups = (ArrayList<String>) element.select("option").stream()
                            .map(option -> option.attr("value"))
                            .collect(Collectors.toList());
                }
            }

            map.put(studyForm, groups);
        }

        return map;
    }

    private List<Schedule> getSchedule(Group group) {
        ArrayList<Schedule> schedule = new ArrayList<>();
        log.info("Парсинг расписания для группы {} (форма: {}, курс: {})", group.getName(), group.getStudyForm(), group.getCourse());

        if (group.getStudyForm().equals("Очная")) {
            for (int i = 0; i <= 1; i++) {
                int finalI = i;
                String response = webClient.get().uri(uriBuilder -> uriBuilder
                            .queryParam("mode", "student")
                            .queryParam("study_form", "очная")
                            .queryParam("week", finalI + 1)
                            .queryParam("day", "all")
                            .queryParam("group", group.getName())
                            .queryParam("show", 1)
                            .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (response == null) {
                    log.warn("[Очная] [{}] Неделя {}: пустой ответ от сервера", group.getName(), finalI + 1);
                    continue;
                }

                Document doc = Jsoup.parse(response);
                Element lessons = doc.getElementsByClass("lessons").first();
                if (lessons == null) {
                    log.warn("[Очная] [{}] Неделя {}: элемент .lessons не найден — расписание пустое", group.getName(), finalI + 1);
                    continue;
                }

                log.info("[Очная] [{}] Неделя {}: начало парсинга", group.getName(), finalI + 1);
                AtomicReference<String> dayWeek = new AtomicReference<>("");

                schedule.addAll(lessons.getElementsByClass("lesson-day").stream().flatMap(day -> {
                    dayWeek.set(day.getElementsByClass("lesson-day-title").first().text().trim());
                    log.debug("[Очная] [{}] Неделя {}: парсинг дня «{}»", group.getName(), finalI + 1, dayWeek.get());
                    int lessonCardCount = day.getElementsByClass("lesson-card").size();
                    log.info("[Очная] [{}] Неделя {}: найдено {} lesson-card в дне {}", group.getName(), finalI + 1, lessonCardCount, dayWeek.get());
                    return day.getElementsByClass("lesson-card").stream();
                }).map(data -> {
                    try {
                        String time = data.getElementsByClass("lesson-time").first().text();
                        Integer lessonCount = lessonTimeMap.get(time);
                        String subject = data.getElementsByClass("lesson-subject").first().text().trim();
                        Integer weekCount = Integer.parseInt(data.getElementsByClass("lesson-chip-week").first().text().split(":")[1].trim());

                        AtomicReference<String> type = new AtomicReference<>("");
                        AtomicReference<String> location = new AtomicReference<>("");
                        AtomicReference<String> teacher = new AtomicReference<>("");

                        data.getElementsByClass("lesson-meta").first().getElementsByClass("lesson-chip").stream().forEach(meta -> {
                            String[] parts = meta.text().split(":", 2);
                            if (parts.length < 2) return;
                            String val = parts[1].trim();
                            switch (parts[0]) {
                                case "Тип" -> type.set(val.equals("л") ? "Лекция" : val.equals("лаб") ? "Лабораторная"
                                        : val.equals("пр") ? "Практика" : val);
                                case "Ауд." -> location.set(val);
                                case "Преп." -> teacher.set(val);
                            }
                        });

                        log.debug("[Очная] [{}] Пара: день={}, время={}, предмет={}, тип={}, ауд={}, преп={}",
                                group.getName(), dayWeek.get(), time, subject, type.get(), location.get(), teacher.get());

                        Schedule savable = new Schedule();
                        savable.setGroup(group);
                        savable.setDayWeek(dayWeek.get());
                        savable.setTimePeriod(time);
                        savable.setLessonCount(lessonCount);
                        savable.setPinnedDate("");
                        savable.setLessonName(subject);
                        savable.setWeekCount(weekCount);
                        savable.setLessonType(type.get());
                        savable.setAuditory(location.get());
                        savable.setEiosLink("");

                        Teacher teach = persistenceService.getOrPersistTeacher(teacher.get());
                        savable.setTeacher(teach);

                        return savable;
                    } catch (Exception e) {
                        log.error("[Очная] [{}] Ошибка парсинга пары: {}", group.getName(), e.getMessage(), e);
                        return null;
                    }
                }).filter(s -> s != null).collect(Collectors.toList()));
                log.info("[Очная] [{}] Неделя {}: после фильтра {} пар", group.getName(), finalI + 1, schedule.stream().filter(s -> s != null).count());
            }
        } else if (group.getStudyForm().equals("Заочная")) {
            for (int i = 0; i <= 3; i++) {
                int finalI = i;
                String response = webClient.get().uri(uriBuilder -> uriBuilder
                                .queryParam("mode", "student")
                                .queryParam("study_form", "заочная")
                                .queryParam("week", finalI + 1)
                                .queryParam("day", "all")
                                .queryParam("group", group.getName())
                                .queryParam("show", 1)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (response == null) {
                    log.warn("[Заочная] [{}] Неделя {}: пустой ответ от сервера", group.getName(), finalI + 1);
                    continue;
                }

                Document doc = Jsoup.parse(response);
                Element lessons = doc.getElementsByClass("lessons").first();
                if (lessons == null) {
                    log.warn("[Заочная] [{}] Неделя {}: элемент .lessons не найден — расписание пустое", group.getName(), finalI + 1);
                    continue;
                }

                log.info("[Заочная] [{}] Неделя {}: начало парсинга", group.getName(), finalI + 1);
                AtomicReference<String> dayWeek = new AtomicReference<>("");
                AtomicReference<String> pinnedDate = new AtomicReference<>("");

                // head format: "День недели: ср · Дата: 01.04"
                schedule.addAll(lessons.getElementsByClass("lesson-day").stream().flatMap(day -> {
                    String head = day.getElementsByClass("lesson-day-title").first().text().trim();
                    log.debug("[Заочная] [{}] Неделя {}: парсинг дня «{}»", group.getName(), finalI + 1, head);
                    dayWeek.set(dayWeekDistantMap.get(head.split(" ")[2]));
                    pinnedDate.set(head.split("Дата: ")[1]);
                    int lessonCardCount = day.getElementsByClass("lesson-card").size();
                    log.info("[Заочная] [{}] Неделя {}: найдено {} lesson-card в дне {}", group.getName(), finalI + 1, lessonCardCount, head);
                    return day.getElementsByClass("lesson-card").stream();
                }).map(data -> {
                    try {
                        String rawTime = data.getElementsByClass("lesson-time").first().text();
                        String time = lessonNumberMap.get(lessonDistantMap.get(rawTime.equals("8:00") ? "08:00"
                                : rawTime.equals("9:40") ? "09:40" : rawTime));
                        Integer lessonCount = lessonDistantMap.get(rawTime);
                        String subject = data.getElementsByClass("lesson-subject").first().text().trim();

                        if (time == null) {
                            log.warn("[Заочная] [{}] Неизвестное время пары: «{}» — пара пропущена", group.getName(), rawTime);
                            return null;
                        }

                        AtomicReference<String> type = new AtomicReference<>("");
                        AtomicReference<String> location = new AtomicReference<>("");
                        AtomicReference<String> teacher = new AtomicReference<>("");

                        data.getElementsByClass("lesson-meta").first().getElementsByClass("lesson-chip").stream().forEach(meta -> {
                            String[] parts = meta.text().split(":", 2);
                            if (parts.length < 2) return;
                            String val = parts[1].trim();
                            switch (parts[0]) {
                                case "Тип" -> type.set(val.substring(0, 1).toUpperCase() + val.substring(1));
                                case "Ауд." -> location.set(val);
                                case "Преп." -> teacher.set(val);
                            }
                        });

                        log.debug("[Заочная] [{}] Пара: день={}, дата={}, время={}, предмет={}, тип={}, ауд={}, преп={}",
                                group.getName(), dayWeek.get(), pinnedDate.get(), time, subject, type.get(), location.get(), teacher.get());

                        Schedule savable = new Schedule();
                        savable.setGroup(group);
                        savable.setDayWeek(dayWeek.get());
                        savable.setTimePeriod(time);
                        savable.setLessonName(subject);
                        savable.setWeekCount(1);
                        savable.setLessonCount(lessonCount);
                        savable.setLessonType(type.get());
                        savable.setAuditory(location.get());
                        savable.setPinnedDate(pinnedDate.get());
                        savable.setEiosLink("");

                        Teacher teach = persistenceService.getOrPersistTeacher(teacher.get());
                        savable.setTeacher(teach);

                        return savable;
                    } catch (Exception e) {
                        log.error("[Заочная] [{}] Ошибка парсинга пары: {}", group.getName(), e.getMessage(), e);
                        return null;
                    }
                }).filter(Objects::nonNull).toList());
                log.info("[Заочная] [{}] Неделя {}: после фильтра {} пар", group.getName(), finalI + 1, schedule.stream().filter(Objects::nonNull).count());
                log.info("[Заочная] [{}] Неделя {}: спаршено {} пар", group.getName(), finalI + 1, schedule.size());
            }
        } else {
            log.warn("Неизвестная форма обучения для группы {}: {}", group.getName(), group.getStudyForm());
        }

        if (group.getStudyForm().equals("Заочная")) {
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

        return schedule;
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
