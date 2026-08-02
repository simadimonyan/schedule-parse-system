package app.service.infra;

import app.repository.models.dto.master.MasterTeacherView;
import lombok.extern.slf4j.Slf4j;

/**
 * Перевод между строкой преподавателя в расписании и ФИО в справочнике мастер-сервиса.
 *
 * <p>Файл расписания знает преподавателя одной строкой «Фамилия И.О.», а мастер хранит
 * фамилию, имя и отчество по отдельности и требует непустые фамилию и имя. Перевод нужен
 * обоим направлениям обмена, поэтому вынесен из {@link MasterSyncService}: чтение собирает
 * строку из частей ровно так же, как запись их разбирала, — иначе один и тот же человек
 * назывался бы в ответе API иначе, чем в файле.
 */
@Slf4j
final class TeacherLabels {

    /**
     * Подставляется вместо имени, когда в ячейке файла у преподавателя нет инициалов: мастер
     * требует непустое имя, а отбросить преподавателя из-за формата ячейки хуже.
     */
    static final String NAME_PLACEHOLDER = "Н/Д";

    private TeacherLabels() {}

    /**
     * Разбирает строку расписания «Фамилия И.О.» на части ФИО мастера:
     * {@code [фамилия, имя, отчество]}. Инициалы уходят на место имени, потому что ничего
     * другого в строке нет.
     */
    static String[] split(String label) {
        String[] parts = label.trim().split("\\s+", 2);
        String lastName = parts[0];

        if (parts.length < 2 || parts[1].isBlank()) {
            log.warn("В строке преподавателя «{}» нет инициалов", label);
            return new String[]{lastName, NAME_PLACEHOLDER, null};
        }

        String[] initials = parts[1].split("\\.");
        String name = initials[0].isBlank() ? NAME_PLACEHOLDER : initials[0].trim() + ".";
        String patronymic = initials.length > 1 && !initials[1].isBlank() ? initials[1].trim() + "." : null;

        return new String[]{lastName, name, patronymic};
    }

    /**
     * Собирает строку расписания «Фамилия И.О.» из частей ФИО мастера.
     *
     * <p>Заглушка вместо имени в строку не попадает: иначе преподаватель, заведённый в
     * справочнике без инициалов, при первом же событии переименовался бы в «Фамилия Н/Д».
     */
    static String compose(MasterTeacherView view) {
        StringBuilder label = new StringBuilder(view.lastName() == null ? "" : view.lastName().trim());
        if (view.name() != null && !view.name().isBlank() && !NAME_PLACEHOLDER.equals(view.name().trim())) {
            label.append(" ").append(view.name().trim());
        }
        if (view.patronymic() != null && !view.patronymic().isBlank()) {
            label.append(view.patronymic().trim());
        }
        return label.toString().trim();
    }

    /** Ключ сравнения преподавателей: пустые части не должны склеивать разных людей. */
    static String fullName(String lastName, String name, String patronymic) {
        return String.join("|",
                lastName == null ? "" : lastName.trim(),
                name == null ? "" : name.trim(),
                patronymic == null ? "" : patronymic.trim());
    }

}
