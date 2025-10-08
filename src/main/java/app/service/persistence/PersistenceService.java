package app.service.persistence;

import app.repository.dao.ConfigRepository;
import app.repository.dao.GroupRepository;
import app.repository.dao.ScheduleRepository;
import app.repository.dao.TeacherRepository;
import app.repository.models.entity.Config;
import app.repository.models.entity.Group;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import app.service.excel.ExcelService;
import app.service.storage.StorageService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PersistenceService {

    private final ExcelService excelService;
    private final StorageService storageService;

    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;
    private final ConfigRepository configRepository;

    @Autowired
    public PersistenceService(
            ExcelService excelService,
            StorageService storageService,
            ScheduleRepository scheduleRepository,
            TeacherRepository teacherRepository,
            GroupRepository groupRepository,
            ConfigRepository configRepository
    ) {
        this.excelService = excelService;
        this.storageService = storageService;
        this.scheduleRepository = scheduleRepository;
        this.teacherRepository = teacherRepository;
        this.groupRepository = groupRepository;
        this.configRepository = configRepository;
    }

    @Cacheable("groups")
    public Group getGroup(String name) {
        Group group = groupRepository.findByName(name).orElse(null);
        if (group == null) throw new EntityNotFoundException(String.format("Группа %s не найдена", name));
        return group;
    }

    @Cacheable("groups")
    public List<Group> getGroups(Integer course, String level) {
        List<Group> groups;
        if (level == null)
            groups = groupRepository.findAllByCourse(course).orElse(new ArrayList<>());
        else
            groups = groupRepository.findAllByCourseAndLevel(course, level).orElse(new ArrayList<>());
        return groups;
    }

    @Cacheable("levels")
    public List<String> getLevels(Integer course) {
        return groupRepository.findDistinctLevels(course).orElse(new ArrayList<>());
    }

    @Cacheable("courses")
    public List<Integer> getCourses() {
        return groupRepository.findDistinctCourses().orElse(new ArrayList<>());
    }

    @Cacheable("schedule")
    public List<Schedule> getGroupSchedule(String name, String dayWeek, Integer weekCount) {
        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByGroupNameAndWeekCount(name, weekCount)
                    .orElse(new ArrayList<>());
        else
            schedule = scheduleRepository.findAllByGroupNameAndDayWeekAndWeekCount(name, dayWeek, weekCount)
                    .orElse(new ArrayList<>());
        return schedule;
    }

    @Cacheable("teachers")
    public Teacher getTeacher(String label) {
        Teacher teacher = teacherRepository.findByLabel(label).orElse(null);
        if (teacher == null) throw new EntityNotFoundException(String.format("Преподаватель %s не найден", label));
        return teacher;
    }

    @Cacheable("teachers")
    public List<Teacher> getTeachers(String department) {
        List<Teacher> teachers;
        if (department == null)
            teachers = teacherRepository.findAll();
        else
            teachers = teacherRepository.findAllByDepartment(department).orElse(new ArrayList<>());
        return teachers;
    }

    @Cacheable("schedule")
    public List<Schedule> getTeacherSchedule(String label, String dayWeek, Integer weekCount) {
        List<Schedule> schedule;
        if (dayWeek == null)
            schedule = scheduleRepository.findAllByTeacherLabelAndWeekCount(label, weekCount)
                    .orElse(new ArrayList<>());
        else
            schedule = scheduleRepository.findAllByTeacherLabelAndDayWeekAndWeekCount(label, dayWeek, weekCount)
                    .orElse(new ArrayList<>());
        return schedule;
    }

    @Cacheable("configs")
    public Config getConfig(String key) {
        Config pair = configRepository.findAllByKey(key).orElse(null);
        if (pair == null) throw new EntityNotFoundException("База не содержит конфигурации с ключем: " + key);
        return pair;
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void swapWeek() {
        try {
            Config pair = configRepository.findAllByKey("weekCount").orElse(null);
            if (pair == null) {
                log.info("Инициализация параметра: weekCount - присвоено значение 1");
                setConfig("weekCount", "1");
            }
            else if (pair.getValue().equals("1")) {
                log.info("Переключение четности недели: старое значение = {}, новое значение = 2", pair.getValue());
                setConfig("weekCount", "2");
            }
            else {
                log.info("Переключение четности недели: старое значение = {}, новое значение = 1", pair.getValue());
                setConfig("weekCount", "1");
            }
        }
        catch (Exception e) {
            log.error("Ошибка при переключении четности недели", e);
        }
    }

    @CacheEvict(value = "configs", allEntries = true)
    public void setConfig(String key, String value) {
        Config pair = configRepository.findAllByKey(key).orElse(null);
        if (pair == null) {
            Config savable = new Config();
            savable.setKey(key);
            savable.setValue(value);
            configRepository.save(savable);
            return;
        }
        pair.setValue(value);
        configRepository.save(pair);
    }

    @Async
    @Transactional
    public void persistSchedule(String fileName) throws IOException {
        InputStream excel = storageService.getObjectByName(fileName.split("/")[1]);
        List<Schedule> newSchedules = excelService.parseWorkbook(fileName, excel);

        for (Schedule schedule : newSchedules) {
            Teacher teacher = schedule.getTeacher();
            if (teacher != null) {
                Teacher managedTeacher = teacherRepository.findByLabel(teacher.getLabel())
                        .orElseGet(() -> teacherRepository.saveAndFlush(teacher));
                schedule.setTeacher(managedTeacher);
            }

            Group group = schedule.getGroup();
            if (group != null) {
                Group managedGroup = groupRepository.findByName(group.getName())
                        .orElseGet(() -> groupRepository.saveAndFlush(group));
                schedule.setGroup(managedGroup);
            }
        }

        Set<Long> groupIds = newSchedules.stream()
                .map(s -> s.getGroup().getId())
                .collect(Collectors.toSet());

        for (Long groupId : groupIds) {
            scheduleRepository.deleteAllByGroupId(groupId);
        }

        scheduleRepository.saveAllAndFlush(newSchedules);
    }

}
