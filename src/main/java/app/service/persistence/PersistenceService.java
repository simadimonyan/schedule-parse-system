package app.service.persistence;

import app.repository.dao.ScheduleRepository;
import app.repository.dao.TeacherRepository;
import app.repository.dao.GroupRepository;
import app.repository.models.entity.Schedule;
import app.repository.models.entity.Teacher;
import app.repository.models.entity.Group;
import app.service.excel.ExcelService;
import app.service.storage.StorageService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersistenceService {

    private final ExcelService excelService;
    private final StorageService storageService;

    private final ScheduleRepository scheduleRepository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public PersistenceService(
            ExcelService excelService,
            StorageService storageService,
            ScheduleRepository scheduleRepository,
            TeacherRepository teacherRepository,
            GroupRepository groupRepository
    ) {
        this.excelService = excelService;
        this.storageService = storageService;
        this.scheduleRepository = scheduleRepository;
        this.teacherRepository = teacherRepository;
        this.groupRepository = groupRepository;
    }

    @Transactional
    public void persistSchedule(String fileName) throws IOException {
        InputStream excel = storageService.getObjectByName(fileName.split("/")[1]);
        List<Schedule> newSchedules = excelService.parseWorkbook(fileName, excel);

        for (Schedule schedule : newSchedules) {
            Teacher teacher = schedule.getTeacher();
            if (teacher != null) {
                Teacher managedTeacher = teacherRepository.findAllByLabel(teacher.getLabel())
                        .orElseGet(() -> teacherRepository.saveAndFlush(teacher));
                schedule.setTeacher(managedTeacher);
            }

            Group group = schedule.getGroup();
            if (group != null) {
                Group managedGroup = groupRepository.findAllByName(group.getName())
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
