package app.repository.models.entity;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/** Состояние опроса: считается из флага публикации и дат, в базе не хранится */
public enum QuizStatus {

    DRAFT, SCHEDULED, ACTIVE, CLOSED;

    /** Даты опросов задаются и сравниваются по времени института */
    public static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static QuizStatus of(Quiz quiz, LocalDateTime now) {
        if (!Boolean.TRUE.equals(quiz.getPublished())) return DRAFT;
        if (quiz.getStartDate() != null && now.isBefore(quiz.getStartDate())) return SCHEDULED;
        if (quiz.getEndDate() != null && now.isAfter(quiz.getEndDate())) return CLOSED;
        return ACTIVE;
    }

    /**
     * Текущий период ежемесячного опроса — месяц "yyyy-MM" по Москве;
     * у одноразового опроса периода нет (null)
     */
    public static String period(Quiz quiz, LocalDateTime now) {
        if (!Quiz.MONTHLY.equals(quiz.getRecurrence())) return null;
        return YearMonth.from(now).toString();
    }

    public String label() {
        return name().toLowerCase();
    }

}
