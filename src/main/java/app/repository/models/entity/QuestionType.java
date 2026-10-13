package app.repository.models.entity;

/**
 * Тип вопроса определяет, как на него отвечают:
 * выбор одного или нескольких вариантов, свободный текст или оценка по шкале
 */
public enum QuestionType {

    /** Один вариант из списка */
    SINGLE,

    /** Несколько вариантов из списка */
    MULTIPLE,

    /** Свободный ответ текстом */
    TEXT,

    /** Оценка числом от 1 до scale */
    RATING;

    public boolean hasOptions() {
        return this == SINGLE || this == MULTIPLE;
    }

}
