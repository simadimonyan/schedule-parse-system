package app.service.max;

import app.repository.dao.GroupRepository;
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
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MaxService {

    private final SchedulePersistenceService persistenceService;
    private final GroupRepository groupRepository;
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

    // заочно
    private static final Map<String, String> dayWeekDistantMap = Map.of(
            "пн", "Понедельник",
            "вт", "Вторник",
            "ср", "Среда",
            "чт", "Четверг",
            "пт", "Пятница",
            "сб", "Суббота"
    );

    @Autowired
    public MaxService(SchedulePersistenceService persistenceService, GroupRepository groupRepository, String url) {
        this.persistenceService = persistenceService;
        this.groupRepository = groupRepository;
        this.webClient = WebClient.create(url);
    }

    @Async
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

        persistenceService.persistSchedule(schedule);
        persistenceService.setConfig("last_external_parsed_group", group.getId().toString());
        log.info("Синхронизация расписания %s завершена!".formatted(group.getName()));
    }

    private Map<String, ArrayList<String>> getGroups() {
        Map<String, ArrayList<String>> map = new HashMap<>();
        ArrayList<String> groups = new ArrayList<>();

        for (int i = 0; i <= 1; i++) {
            int finalI = i;
            String response = webClient.get().uri(uriBuilder -> uriBuilder
                         .queryParam("study_form", (finalI == 0) ? "очная" : "заочная")
                         .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                Document doc = Jsoup.parse(response);
                Element element = doc.getElementById("group-list");

                groups = (ArrayList<String>) element.stream().map(option ->
                        option.selectFirst("option").attr("value"))
                        .collect(Collectors.toList());
            }

            map.put((finalI == 0) ? "очная" : "заочная", groups);
            groups.clear();
        }

        return map;
    }

    private List<Schedule> getSchedule(Group group) {
        ArrayList<Schedule> schedule = new ArrayList<>();

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

                if (response != null) {
                    Document doc = Jsoup.parse(response);
                    Element element = doc.getElementById("lessons");

                    AtomicReference<String> dayWeek = new AtomicReference<>("");

                    schedule = (ArrayList<Schedule>) element.stream().flatMap(day -> {
                        dayWeek.set(day.getElementById("lesson-day-head")
                                .getElementById("lesson-day-title").text().trim());
                        return day.getElementById("lesson-grid").stream();
                    }).map(lesson -> {
                        Element data = lesson.getElementById("lesson-card");

                        String time = data.getElementById("lesson-time").text();
                        String subject = data.getElementById("lesson-subject").text().trim();
                        Integer weekCount = Integer.parseInt(data.getElementById("lesson-chip-week").text().split(":")[1].trim());

                        AtomicReference<String> type = new AtomicReference<>("");
                        AtomicReference<String> location = new AtomicReference<>("");
                        AtomicReference<String> teacher = new AtomicReference<>("");

                        data.getElementById("lesson-meta").stream().forEach(meta -> {

                            String dotSplit = meta.text().split(":")[1];

                            switch (meta.text().split(":")[0]) {
                                case "Тип" -> type.set(dotSplit.trim().equals("л") ? "Лекция" : dotSplit.equals("лаб") ? "Лабораторная"
                                        :  dotSplit.equals("п") ? "Практика" : type.get().substring(0, 1).toUpperCase()
                                        + toString().substring(1));
                                case "Ауд." -> location.set(dotSplit);
                                case "Преп." -> teacher.set(dotSplit);
                            }
                        });

                        Schedule savable = new Schedule();
                        savable.setGroup(group);
                        savable.setDayWeek(dayWeek.get());
                        savable.setTimePeriod(time);
                        savable.setLessonName(subject);
                        savable.setWeekCount(weekCount);
                        savable.setLessonType(type.get());
                        savable.setAuditory(location.get());
                        savable.setEiosLink("");

                        Teacher teach = persistenceService.getOrPersistTeacher(teacher.get());

                        savable.setTeacher(teach);

                        return savable;
                    }).collect(Collectors.toList());
                }
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

                if (response != null) {
                    Document doc = Jsoup.parse(response);
                    Element element = doc.getElementById("lessons");

                    AtomicReference<String> dayWeek = new AtomicReference<>("");
                    AtomicReference<String> pinnedDate = new AtomicReference<>("");

                    schedule = (ArrayList<Schedule>) element.stream().flatMap(day -> {
                        String head = day.getElementById("lesson-day-head").getElementById("lesson-day-title").text().trim();
                        dayWeek.set(dayWeekDistantMap.get(head.split(" ")[2]));
                        pinnedDate.set(head.split("Дата: ")[1]);
                        return day.getElementById("lesson-grid").stream();
                    }).map(lesson -> {
                        Element data = lesson.getElementById("lesson-card");

                        String time = lessonNumberMap.get(lessonDistantMap.get(data.getElementById("lesson-time").text()));
                        String subject = data.getElementById("lesson-subject").text().trim();
                        Integer weekCount = Integer.parseInt(data.getElementById("lesson-chip-week").text().split(":")[1].trim());

                        AtomicReference<String> type = new AtomicReference<>("");
                        AtomicReference<String> location = new AtomicReference<>("");
                        AtomicReference<String> teacher = new AtomicReference<>("");

                        data.getElementById("lesson-meta").stream().forEach(meta -> {

                            String dotSplit = meta.text().split(":")[1];

                            switch (meta.text().split(":")[0]) {
                                case "Тип" -> type.set(dotSplit.trim().equals("л") ? "Лекция" : dotSplit.equals("лаб") ? "Лабораторная"
                                        :  dotSplit.equals("п") ? "Практика" : type.get().substring(0, 1).toUpperCase()
                                        + toString().substring(1));
                                case "Ауд." -> location.set(dotSplit);
                                case "Преп." -> teacher.set(dotSplit);
                            }
                        });

                        Schedule savable = new Schedule();
                        savable.setGroup(group);
                        savable.setDayWeek(dayWeek.get());
                        savable.setTimePeriod(time);
                        savable.setLessonName(subject);
                        savable.setWeekCount(weekCount);
                        savable.setLessonType(type.get());
                        savable.setAuditory(location.get());
                        savable.setPinnedDate(pinnedDate.get());
                        savable.setEiosLink("");

                        Teacher teach = persistenceService.getOrPersistTeacher(teacher.get());

                        savable.setTeacher(teach);

                        return savable;
                    }).collect(Collectors.toList());
                }
            }
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
