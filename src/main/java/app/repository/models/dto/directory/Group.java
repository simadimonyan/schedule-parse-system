package app.repository.models.dto.directory;

import lombok.Data;

import java.io.Serializable;

/**
 * Группа расписания.
 *
 * <p>Не сущность: справочник групп принадлежит мастер-сервису, и собственной таблицы у
 * расписания больше нет — пара ссылается на группу идентификатором мастера.
 *
 * <p>Объект собирается двумя путями, и от пути зависит, что в нём заполнено:
 * <ul>
 *   <li><b>парсер файла</b> — имя, курс, уровень и форма обучения известны, {@code id}
 *       пуст: идентификатор мастера появляется только при сохранении;
 *   <li><b>чтение справочника</b> — заполнено всё, включая {@code id}.
 * </ul>
 */
@Data
public class Group implements Serializable {

    /** Идентификатор группы в справочнике мастер-сервиса. */
    private Long id;

    private Integer course;

    // уровень образования
    private String level;

    // форма обучения
    private String studyForm;

    private String name;

    private Long updatedAt;

}
