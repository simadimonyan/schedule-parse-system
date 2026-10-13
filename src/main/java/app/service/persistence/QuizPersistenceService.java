package app.service.persistence;

import app.controller.exceptions.ConflictException;
import app.controller.exceptions.ServiceException;
import app.repository.dao.ApplicationRepository;
import app.repository.dao.QuizRepository;
import app.repository.dao.SubmissionRepository;
import app.repository.models.dto.api.quiz.request.QuizRequest;
import app.repository.models.dto.api.quiz.request.SubmitRequest;
import app.repository.models.dto.api.quiz.response.OptionResult;
import app.repository.models.dto.api.quiz.response.QuestionResult;
import app.repository.models.dto.api.quiz.response.QuizResultsResponse;
import app.repository.models.dto.api.quiz.unit.AnswerUnit;
import app.repository.models.dto.api.quiz.unit.QuestionUnit;
import app.repository.models.dto.api.quiz.unit.SubmitUnit;
import app.repository.models.entity.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
public class QuizPersistenceService {

    static final int MAX_TEXT_LENGTH = 2000;
    static final int MAX_RESPONDENT_LENGTH = 256;
    static final int DEFAULT_SCALE = 5;
    static final int MAX_SCALE = 10;
    static final String DEFAULT_COMPLETION_MESSAGE = "Спасибо! Ваши ответы приняты.";

    private final QuizRepository quizRepository;
    private final SubmissionRepository submissionRepository;
    private final ApplicationRepository applicationRepository;

    @Autowired
    public QuizPersistenceService(
        QuizRepository quizRepository,
        SubmissionRepository submissionRepository,
        ApplicationRepository applicationRepository
    ) {
        this.quizRepository = quizRepository;
        this.submissionRepository = submissionRepository;
        this.applicationRepository = applicationRepository;
    }

    // ---------------------------------------------------------------- чтение

    public Quiz getQuiz(Long id) {
        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) throw new EntityNotFoundException("База не содержит опроса по id: " + id);
        return quiz;
    }

    /** Опрос для клиентов: черновики от них скрыты */
    public Quiz getPublishedQuiz(Long id) {
        Quiz quiz = getQuiz(id);
        if (!Boolean.TRUE.equals(quiz.getPublished())) throw new EntityNotFoundException("База не содержит опроса по id: " + id);
        return quiz;
    }

    public List<Quiz> getAllQuizzes() {
        return quizRepository.findAllByOrderByIdDesc();
    }

    /**
     * Опросы, которые сейчас можно пройти: для всех и для указанной группы.
     * С respondent из списка убираются уже пройденные им опросы
     */
    public List<Quiz> getActiveQuizzes(String group, String respondent) {
        LocalDateTime now = QuizStatus.now();
        List<Quiz> quizzes = new ArrayList<>();
        for (Quiz quiz : quizRepository.findAllByPublishedTrueOrderByIdDesc()) {
            if (QuizStatus.of(quiz, now) == QuizStatus.ACTIVE && matchGroup(quiz, group) != null) quizzes.add(quiz);
        }
        if (respondent == null || respondent.isBlank() || quizzes.isEmpty()) return quizzes;

        Map<String, Long> byHash = new HashMap<>();
        for (Quiz quiz : quizzes) byHash.put(respondentHash(quiz.getId(), respondent), quiz.getId());
        Set<Long> answered = new HashSet<>(submissionRepository.findQuizIdsByRespondentHashes(byHash.keySet()));
        quizzes.removeIf(q -> answered.contains(q.getId()));
        return quizzes;
    }

    public Map<Long, Long> countApplications(Collection<Long> quizIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (quizIds.isEmpty()) return counts;
        for (Object[] row : submissionRepository.countByQuizIds(quizIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public long countApplications(Long quizId) {
        return submissionRepository.countByQuizId(quizId);
    }

    // ---------------------------------------------------------------- управление

    @Transactional(rollbackOn = Exception.class)
    public Quiz publishQuiz(QuizRequest request) throws ServiceException {
        Quiz quiz = new Quiz();
        LocalDateTime now = QuizStatus.now();
        quiz.setCreatedAt(now);
        quiz.setUpdatedAt(now);
        quiz.setPublished(request.published() == null || request.published());

        if (request.questions() == null || request.questions().isEmpty()) throw new ServiceException("В опросе должен быть хотя бы один вопрос");
        applyMeta(quiz, request, true);
        replaceQuestions(quiz, request.questions());

        Quiz saved = quizRepository.save(quiz);
        log.info("Создан опрос {} «{}», вопросов: {}", saved.getId(), saved.getName(), saved.getQuestions().size());
        return saved;
    }

    /**
     * Правка опроса: null-поля остаются прежними.
     * Вопросы можно заменить только пока на опрос никто не ответил — иначе итоги разъедутся с анкетами.
     * Ошибки здесь проверяемые, а jakarta-транзакция по умолчанию на них не откатывается — отсюда rollbackOn
     */
    @Transactional(rollbackOn = Exception.class)
    public Quiz editQuiz(Long id, QuizRequest request) throws ServiceException, ConflictException {
        Quiz quiz = getQuiz(id);
        if (request.questions() != null) {
            if (request.questions().isEmpty()) throw new ServiceException("В опросе должен быть хотя бы один вопрос");
            if (submissionRepository.countByQuizId(id) > 0) {
                throw new ConflictException("На опрос уже отвечали — вопросы менять нельзя, создайте новый опрос");
            }
        }

        applyMeta(quiz, request, false);
        if (request.published() != null) quiz.setPublished(request.published());
        if (request.questions() != null) replaceQuestions(quiz, request.questions());

        quiz.setUpdatedAt(QuizStatus.now());
        Quiz saved = quizRepository.save(quiz);
        log.info("Изменён опрос {} «{}»", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void removeQuiz(Long id) {
        Quiz quiz = getQuiz(id);
        // ответы ссылаются на вопросы и варианты, поэтому уходят первыми
        applicationRepository.deleteByQuizId(id);
        submissionRepository.deleteByQuizId(id);
        quizRepository.delete(quiz);
        log.info("Удалён опрос {} «{}»", id, quiz.getName());
    }

    private void applyMeta(Quiz quiz, QuizRequest request, boolean creating) throws ServiceException {
        if (creating || request.name() != null) {
            String name = trimToNull(request.name());
            if (name == null) throw new ServiceException("У опроса должно быть название");
            if (name.length() > 255) throw new ServiceException("Название опроса длиннее 255 символов");
            quiz.setName(name);
        }
        if (creating || request.description() != null) quiz.setDescription(trimToNull(request.description()));
        if (creating || request.completionMessage() != null) quiz.setCompletionMessage(trimToNull(request.completionMessage()));

        if (request.groups() != null) {
            Set<String> groups = new HashSet<>();
            for (String g : request.groups()) {
                String group = trimToNull(g);
                if (group != null) groups.add(group);
            }
            quiz.getGroups().clear();
            quiz.getGroups().addAll(groups);
        }

        if (creating || request.startDate() != null) quiz.setStartDate(parseDate(request.startDate(), false));
        if (creating || request.endDate() != null) quiz.setEndDate(parseDate(request.endDate(), true));
        if (quiz.getStartDate() != null && quiz.getEndDate() != null && quiz.getEndDate().isBefore(quiz.getStartDate())) {
            throw new ServiceException("Дата окончания опроса раньше даты начала");
        }
    }

    private void replaceQuestions(Quiz quiz, List<QuestionUnit> units) throws ServiceException {
        List<Question> questions = new ArrayList<>();
        int position = 0;
        for (QuestionUnit unit : units) {
            position++;
            if (unit == null) throw new ServiceException("Вопрос %d пустой".formatted(position));
            String label = trimToNull(unit.label());
            if (label == null) throw new ServiceException("У вопроса %d нет текста".formatted(position));

            QuestionType type;
            try {
                type = QuestionType.valueOf(Objects.requireNonNullElse(unit.type(), "").trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ServiceException("Вопрос %d: неизвестный тип «%s», допустимы SINGLE, MULTIPLE, TEXT, RATING".formatted(position, unit.type()));
            }

            Question question = new Question();
            question.setQuiz(quiz);
            question.setPosition(position);
            question.setLabel(label);
            question.setDescription(trimToNull(unit.description()));
            question.setType(type);
            question.setRequired(unit.required() == null || unit.required());

            if (type == QuestionType.RATING) {
                int scale = unit.scale() == null ? DEFAULT_SCALE : unit.scale();
                if (scale < 2 || scale > MAX_SCALE) throw new ServiceException("Вопрос %d: шкала должна быть от 2 до %d".formatted(position, MAX_SCALE));
                question.setScale(scale);
            }

            if (type.hasOptions()) {
                List<AnswerUnit> options = unit.answers() == null ? List.of() : unit.answers();
                int optionPosition = 0;
                for (AnswerUnit option : options) {
                    String optionLabel = option == null ? null : trimToNull(option.label());
                    if (optionLabel == null) throw new ServiceException("Вопрос %d: пустой вариант ответа".formatted(position));
                    Answer answer = new Answer();
                    answer.setQuestion(question);
                    answer.setPosition(++optionPosition);
                    answer.setLabel(optionLabel);
                    question.getAnswers().add(answer);
                }
                if (question.getAnswers().size() < 2) throw new ServiceException("Вопрос %d: нужно хотя бы два варианта ответа".formatted(position));
            }
            questions.add(question);
        }
        quiz.getQuestions().clear();
        quiz.getQuestions().addAll(questions);
    }

    // ---------------------------------------------------------------- анкеты

    /** Принимает анкету целиком или отклоняет её с объяснением, что не так */
    @Transactional(rollbackOn = Exception.class)
    public Quiz persistSubmit(SubmitRequest request) throws ServiceException, ConflictException {
        if (request.quizId() == null) throw new ServiceException("Не указан опрос");
        Quiz quiz = getPublishedQuiz(request.quizId());

        QuizStatus status = QuizStatus.of(quiz, QuizStatus.now());
        if (status == QuizStatus.SCHEDULED) throw new ConflictException("Опрос ещё не начался");
        if (status == QuizStatus.CLOSED) throw new ConflictException("Опрос завершён");

        String respondent = trimToNull(request.respondent());
        if (respondent == null) throw new ServiceException("Не указан респондент");
        if (respondent.length() > MAX_RESPONDENT_LENGTH) throw new ServiceException("Слишком длинный идентификатор респондента");

        String group = trimToNull(request.group());
        String matchedGroup = matchGroup(quiz, group);
        if (matchedGroup == null) throw new ServiceException("Опрос проводится не для этой группы");

        String hash = respondentHash(quiz.getId(), respondent);
        if (submissionRepository.existsByQuizIdAndRespondentHash(quiz.getId(), hash)) {
            throw new ConflictException("Вы уже прошли этот опрос");
        }

        Submission submission = new Submission();
        submission.setQuiz(quiz);
        submission.setRespondentHash(hash);
        submission.setGroup(matchedGroup.isEmpty() ? group : matchedGroup);
        submission.setCreatedAt(QuizStatus.now());
        submission.getApplications().addAll(toApplications(quiz, submission, request.answers()));

        submissionRepository.saveAndFlush(submission);
        log.info("Принята анкета опроса {} (группа {}), ответов: {}", quiz.getId(), submission.getGroup(), submission.getApplications().size());
        return quiz;
    }

    public String completionMessage(Quiz quiz) {
        return quiz.getCompletionMessage() == null ? DEFAULT_COMPLETION_MESSAGE : quiz.getCompletionMessage();
    }

    private List<Application> toApplications(Quiz quiz, Submission submission, List<SubmitUnit> units) throws ServiceException {
        if (units == null || units.isEmpty()) throw new ServiceException("Анкета пустая");

        Map<Long, Question> questions = new LinkedHashMap<>();
        for (Question q : quiz.getQuestions()) questions.put(q.getId(), q);

        Map<Long, List<SubmitUnit>> byQuestion = new LinkedHashMap<>();
        for (SubmitUnit unit : units) {
            if (unit == null || unit.questionId() == null) throw new ServiceException("Ответ без вопроса");
            if (!questions.containsKey(unit.questionId())) throw new ServiceException("Вопрос %d не из этого опроса".formatted(unit.questionId()));
            byQuestion.computeIfAbsent(unit.questionId(), k -> new ArrayList<>()).add(unit);
        }

        List<Application> applications = new ArrayList<>();
        for (Question question : questions.values()) {
            List<SubmitUnit> answers = byQuestion.getOrDefault(question.getId(), List.of());
            int number = question.getPosition() == null ? 0 : question.getPosition();
            if (answers.isEmpty()) {
                if (Boolean.TRUE.equals(question.getRequired())) throw new ServiceException("Нет ответа на обязательный вопрос %d «%s»".formatted(number, question.getLabel()));
                continue;
            }
            if (question.getType() != QuestionType.MULTIPLE && answers.size() > 1) {
                throw new ServiceException("На вопрос %d можно дать только один ответ".formatted(number));
            }

            switch (question.getType()) {
                case SINGLE, MULTIPLE -> {
                    Map<Long, Answer> options = new HashMap<>();
                    for (Answer a : question.getAnswers()) options.put(a.getId(), a);
                    Set<Long> chosen = new HashSet<>();
                    for (SubmitUnit unit : answers) {
                        Answer option = unit.answerId() == null ? null : options.get(unit.answerId());
                        if (option == null) throw new ServiceException("Вопрос %d: такого варианта ответа нет".formatted(number));
                        if (!chosen.add(option.getId())) throw new ServiceException("Вопрос %d: вариант выбран дважды".formatted(number));
                        applications.add(application(submission, question, option, null));
                    }
                }
                case TEXT -> {
                    String text = trimToNull(answers.get(0).value());
                    if (text == null) {
                        if (Boolean.TRUE.equals(question.getRequired())) throw new ServiceException("Нет ответа на обязательный вопрос %d «%s»".formatted(number, question.getLabel()));
                        continue;
                    }
                    if (text.length() > MAX_TEXT_LENGTH) throw new ServiceException("Вопрос %d: ответ длиннее %d символов".formatted(number, MAX_TEXT_LENGTH));
                    applications.add(application(submission, question, null, text));
                }
                case RATING -> {
                    int scale = question.getScale() == null ? DEFAULT_SCALE : question.getScale();
                    int rating;
                    try {
                        rating = Integer.parseInt(Objects.requireNonNullElse(answers.get(0).value(), "").trim());
                    } catch (NumberFormatException e) {
                        throw new ServiceException("Вопрос %d: оценка должна быть числом от 1 до %d".formatted(number, scale));
                    }
                    if (rating < 1 || rating > scale) throw new ServiceException("Вопрос %d: оценка должна быть числом от 1 до %d".formatted(number, scale));
                    applications.add(application(submission, question, null, String.valueOf(rating)));
                }
            }
        }
        if (applications.isEmpty()) throw new ServiceException("Анкета пустая");
        return applications;
    }

    private Application application(Submission submission, Question question, Answer answer, String value) {
        Application application = new Application();
        application.setSubmission(submission);
        application.setQuestion(question);
        application.setAnswer(answer);
        application.setValue(value);
        return application;
    }

    // ---------------------------------------------------------------- итоги

    /** Сводка ответов по каждому вопросу; group — только анкеты этой группы */
    @Transactional
    public QuizResultsResponse getResults(Long id, String group) {
        Quiz quiz = getQuiz(id);
        String filter = trimToNull(group);

        Map<Long, List<Application>> byQuestion = new HashMap<>();
        Set<Long> submissions = new HashSet<>();
        for (Application a : applicationRepository.findAllForResults(id)) {
            if (filter != null && !filter.equalsIgnoreCase(a.getSubmission().getGroup())) continue;
            submissions.add(a.getSubmission().getId());
            byQuestion.computeIfAbsent(a.getQuestion().getId(), k -> new ArrayList<>()).add(a);
        }

        List<QuestionResult> results = new ArrayList<>();
        for (Question question : quiz.getQuestions()) {
            results.add(questionResult(question, byQuestion.getOrDefault(question.getId(), List.of())));
        }
        return new QuizResultsResponse(quiz.getId(), quiz.getName(), QuizStatus.of(quiz, QuizStatus.now()).label(), filter, (long) submissions.size(), results);
    }

    static QuestionResult questionResult(Question question, List<Application> applications) {
        long answered = applications.stream().map(a -> a.getSubmission().getId()).distinct().count();
        List<OptionResult> options = new ArrayList<>();
        Double average = null;
        List<String> texts = new ArrayList<>();

        switch (question.getType()) {
            case SINGLE, MULTIPLE -> {
                Map<Long, Long> counts = new HashMap<>();
                for (Application a : applications) counts.merge(a.getAnswer().getId(), 1L, Long::sum);
                for (Answer option : question.getAnswers()) {
                    long count = counts.getOrDefault(option.getId(), 0L);
                    options.add(new OptionResult(option.getId(), option.getLabel(), count, share(count, answered)));
                }
            }
            case RATING -> {
                int scale = question.getScale() == null ? DEFAULT_SCALE : question.getScale();
                long[] counts = new long[scale + 1];
                long sum = 0;
                for (Application a : applications) {
                    int rating = Integer.parseInt(a.getValue());
                    if (rating >= 1 && rating <= scale) counts[rating]++;
                    sum += rating;
                }
                for (int r = 1; r <= scale; r++) options.add(new OptionResult(null, String.valueOf(r), counts[r], share(counts[r], answered)));
                if (answered > 0) average = Math.round(sum * 100.0 / applications.size()) / 100.0;
            }
            case TEXT -> {
                for (Application a : applications) texts.add(a.getValue());
            }
        }
        return new QuestionResult(question.getId(), question.getLabel(), question.getType().name(), answered, options, average, texts);
    }

    private static Double share(long count, long total) {
        return total == 0 ? 0.0 : Math.round(count * 1000.0 / total) / 1000.0;
    }

    // ---------------------------------------------------------------- помощники

    /**
     * Подходит ли группа под аудиторию опроса. Возвращает название группы так,
     * как оно записано в опросе; "" — опрос для всех; null — не подходит
     */
    static String matchGroup(Quiz quiz, String group) {
        if (quiz.getGroups() == null || quiz.getGroups().isEmpty()) return "";
        if (group == null) return null;
        String wanted = group.trim();
        for (String g : quiz.getGroups()) {
            if (g.equalsIgnoreCase(wanted)) return g;
        }
        return null;
    }

    /** Хеш вместо идентификатора: повтор ловится, а кто ответил — из базы не узнать */
    static String respondentHash(Long quizId, String respondent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((quizId + ":" + respondent.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "yyyy-MM-dd" (начало или конец дня) либо "yyyy-MM-ddTHH:mm[:ss]"; пусто — без границы */
    static LocalDateTime parseDate(String value, boolean endOfDay) throws ServiceException {
        String date = trimToNull(value);
        if (date == null) return null;
        try {
            if (date.length() == 10) {
                LocalDate day = LocalDate.parse(date);
                return endOfDay ? day.atTime(LocalTime.of(23, 59, 59)) : day.atStartOfDay();
            }
            return LocalDateTime.parse(date);
        } catch (DateTimeParseException e) {
            throw new ServiceException("Неверная дата «%s», ожидается yyyy-MM-dd или yyyy-MM-ddTHH:mm".formatted(value));
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
