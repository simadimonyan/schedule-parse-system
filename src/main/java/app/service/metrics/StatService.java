package app.service.metrics;

import app.repository.dao.ConfigRepository;
import app.repository.models.entity.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StatService {

    private final ConfigRepository configRepository;

    @Autowired
    public StatService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public Map<String, Integer> getTopGroupsByViews() {

        List<Config> data = configRepository.findAllByTag("group_views")
                .orElseGet(ArrayList::new);

        return data.stream()
                .collect(Collectors.groupingBy(
                        Config::getKey,
                        Collectors.summingInt(c -> Integer.parseInt(c.getValue()))
                ))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    public Map<String, Integer> getTopTeachersByViews() {

        List<Config> data = configRepository.findAllByTag("teacher_views")
                .orElseGet(ArrayList::new);

        return data.stream()
                .collect(Collectors.groupingBy(
                        Config::getKey,
                        Collectors.summingInt(c -> Integer.parseInt(c.getValue()))
                ))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        HashMap::new
                ));
    }

    @Async
    public void addGroupView(String group) {
        Config metric = new Config();
        List<Config> views = configRepository.findAllByKeyAndTag(group, "group_views")
                .orElseGet(ArrayList::new);

        if (views.isEmpty()) {
            metric.setKey(group);
            metric.setTag("group_views");
            metric.setCreatedAt(LocalDateTime.now().toString());
            metric.setUpdatedAt(LocalDateTime.now().toString());
            metric.setValue("1");

            configRepository.save(metric);
        }
        else {
            boolean check = true;
            for (Config key : views) {
                LocalDateTime created = LocalDateTime.parse(key.getCreatedAt());
                LocalDateTime updated = LocalDateTime.parse(key.getUpdatedAt());

                long days = ChronoUnit.DAYS.between(created, updated);

                if (days < 7) {
                    check = false;
                    key.setValue(Integer.parseInt(key.getValue()) + 1 + "");
                    key.setUpdatedAt(LocalDateTime.now().toString());

                    configRepository.saveAndFlush(key);
                }
            }

            if (check) {
                metric.setKey(group);
                metric.setTag("group_views");
                metric.setCreatedAt(LocalDateTime.now().toString());
                metric.setUpdatedAt(LocalDateTime.now().toString());
                metric.setValue("1");

                configRepository.save(metric);
            }
        }
    }

    @Async
    public void addTeacherView(String teacher) {
        Config metric = new Config();
        List<Config> views = configRepository.findAllByKeyAndTag(teacher, "teacher_views")
                .orElseGet(ArrayList::new);

        if (views.isEmpty()) {
            metric.setKey(teacher);
            metric.setTag("teacher_views");
            metric.setCreatedAt(LocalDateTime.now().toString());
            metric.setUpdatedAt(LocalDateTime.now().toString());
            metric.setValue("1");

            configRepository.save(metric);
        }
        else {
            boolean check = true;
            for (Config key : views) {
                LocalDateTime created = LocalDateTime.parse(key.getCreatedAt());
                LocalDateTime updated = LocalDateTime.parse(key.getUpdatedAt());

                long days = ChronoUnit.DAYS.between(created, updated);

                if (days < 7) {
                    check = false;
                    key.setValue(Integer.parseInt(key.getValue()) + 1 + "");
                    key.setUpdatedAt(LocalDateTime.now().toString());

                    configRepository.saveAndFlush(key);
                }
            }

            if (check) {
                metric.setKey(teacher);
                metric.setTag("teacher_views");
                metric.setCreatedAt(LocalDateTime.now().toString());
                metric.setUpdatedAt(LocalDateTime.now().toString());
                metric.setValue("1");

                configRepository.save(metric);
            }
        }
    }

}
