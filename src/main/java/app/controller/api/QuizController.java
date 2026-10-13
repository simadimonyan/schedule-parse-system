package app.controller.api;

import app.controller.exceptions.ConflictException;
import app.controller.exceptions.ServiceException;
import app.repository.models.dto.api.auth.AdminToken;
import app.repository.models.dto.api.quiz.request.QuizExportRequest;
import app.repository.models.dto.api.quiz.request.QuizImportRequest;
import app.repository.models.dto.api.quiz.request.QuizRequest;
import app.repository.models.dto.api.quiz.request.SubmitRequest;
import app.repository.models.dto.api.quiz.response.QuizResponse;
import app.repository.models.dto.api.quiz.response.QuizResultsResponse;
import app.repository.models.dto.api.quiz.response.QuizzesResponse;
import app.repository.models.dto.api.quiz.response.SubmitResponse;
import app.repository.models.dto.mappers.QuizMapper;
import app.repository.models.entity.Quiz;
import app.repository.models.entity.QuizStatus;
import app.security.AdminAccess;
import app.service.persistence.QuizPersistenceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Опросы с обратной связью. Клиенты (приложение, боты) получают активные опросы
 * и отправляют анкеты; администратор создаёт, правит, удаляет опросы и смотрит итоги.
 * Администратор — служебный ключ в теле (боты) или диспетчер кабинета ИСУЭР
 * с токеном в заголовке X-User-Token (см. {@link AdminAccess})
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/quizzes")
@SecurityRequirement(name = "Authorization")
public class QuizController {

    private final QuizPersistenceService persistenceService;
    private final QuizMapper quizMapper;
    private final AdminAccess adminAccess;

    public QuizController(QuizPersistenceService persistenceService, QuizMapper quizMapper, AdminAccess adminAccess) {
        this.persistenceService = persistenceService;
        this.quizMapper = quizMapper;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuizResponse> quiz(@PathVariable("id") Long id) {
        log.info("GET Запрос: /api/v1/quizzes/{}", id);
        Quiz quiz = persistenceService.getPublishedQuiz(id);
        return ResponseEntity.ok(quizMapper.toQuizResponse(quiz, persistenceService.countApplications(id)));
    }

    /** Опросы, открытые сейчас: для всех и для группы; с respondent — без уже пройденных */
    @GetMapping("/search")
    public ResponseEntity<QuizzesResponse> search(
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "respondent", required = false) String respondent
    ) {
        log.info("GET Запрос: /api/v1/quizzes/search?group={}", group);
        List<Quiz> quizzes = persistenceService.getActiveQuizzes(group, respondent);
        return ResponseEntity.ok(quizMapper.toQuizzesResponse(quizzes, persistenceService.countApplications(quizzes.stream().map(Quiz::getId).toList())));
    }

    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(@RequestBody SubmitRequest submit) throws ServiceException, ConflictException {
        log.info("POST Запрос: /api/v1/quizzes/submit (опрос {})", submit.quizId());
        Quiz quiz = persistenceService.persistSubmit(submit);
        return ResponseEntity.ok(new SubmitResponse(quiz.getId(), persistenceService.completionMessage(quiz)));
    }

    /** Все опросы, включая черновики и завершённые */
    @PostMapping("/admin/search")
    public ResponseEntity<?> adminSearch(
            @RequestBody(required = false) AdminToken token,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) {
        log.info("POST Запрос: /api/v1/quizzes/admin/search");
        if (!isAdmin(token, userToken)) return forbidden();
        List<Quiz> quizzes = persistenceService.getAllQuizzes();
        return ResponseEntity.ok(quizMapper.toQuizzesResponse(quizzes, persistenceService.countApplications(quizzes.stream().map(Quiz::getId).toList())));
    }

    /**
     * Итоги опроса: распределения ответов, средние оценки, свободные ответы.
     * period=yyyy-MM — только анкеты этого месяца (для ежемесячных опросов), без него — все
     */
    @PostMapping("/{id}/results")
    public ResponseEntity<?> results(
            @PathVariable("id") Long id,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "period", required = false) String period,
            @RequestBody(required = false) AdminToken token,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) throws ServiceException {
        log.info("POST Запрос: /api/v1/quizzes/{}/results?group={}&period={}", id, group, period);
        if (!isAdmin(token, userToken)) return forbidden();
        QuizResultsResponse results = persistenceService.getResults(id, group, period);
        return ResponseEntity.ok(results);
    }

    /**
     * Выгрузка опросов в переносимый JSON (format "isuer.quizzes", version 1):
     * без id вопросов и вариантов и без анкет. ids пусто — все опросы
     */
    @PostMapping("/admin/export")
    public ResponseEntity<?> export(
            @RequestBody(required = false) QuizExportRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) {
        log.info("POST Запрос: /api/v1/quizzes/admin/export");
        if (!isAdmin(request == null ? null : new AdminToken(request.token()), userToken)) return forbidden();
        List<Quiz> quizzes = persistenceService.getQuizzesForExport(request == null ? null : request.ids());
        return ResponseEntity.ok(quizMapper.toExportResponse(quizzes, QuizStatus.now()));
    }

    /**
     * Загрузка документа экспорта: всегда создаёт новые опросы, всё или ничего —
     * при ошибке ничего не создаётся, 400 «Опрос N: причина». draft=true — все черновиками
     */
    @PostMapping("/admin/import")
    public ResponseEntity<?> importQuizzes(
            @RequestParam(value = "draft", required = false, defaultValue = "false") boolean draft,
            @RequestBody QuizImportRequest request,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) throws ServiceException {
        log.info("POST Запрос: /api/v1/quizzes/admin/import?draft={}", draft);
        if (!isAdmin(new AdminToken(request.token()), userToken)) return forbidden();
        List<Quiz> created = persistenceService.importQuizzes(request, draft);
        return ResponseEntity.ok(quizMapper.toQuizzesResponse(created, Map.of()));
    }

    @PostMapping("/publish")
    public ResponseEntity<?> publish(
            @RequestBody QuizRequest quiz,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) throws ServiceException {
        log.info("POST Запрос: /api/v1/quizzes/publish");
        if (!isAdmin(quiz.token(), userToken)) return forbidden();
        Quiz saved = persistenceService.publishQuiz(quiz);
        return ResponseEntity.status(HttpStatus.CREATED).body(quizMapper.toQuizResponse(saved, 0L));
    }

    @PatchMapping("/edit/{id}")
    public ResponseEntity<?> edit(
            @PathVariable("id") Long id,
            @RequestBody QuizRequest quiz,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) throws ServiceException, ConflictException {
        log.info("PATCH Запрос: /api/v1/quizzes/edit/{}", id);
        if (!isAdmin(quiz.token(), userToken)) return forbidden();
        Quiz saved = persistenceService.editQuiz(id, quiz);
        return ResponseEntity.ok(quizMapper.toQuizResponse(saved, persistenceService.countApplications(id)));
    }

    @DeleteMapping("/remove/{id}")
    public ResponseEntity<?> remove(
            @PathVariable("id") Long id,
            @RequestBody(required = false) AdminToken token,
            @RequestHeader(value = AdminAccess.HEADER, required = false) String userToken
    ) {
        log.info("DELETE Запрос: /api/v1/quizzes/remove/{}", id);
        if (!isAdmin(token, userToken)) return forbidden();
        persistenceService.removeQuiz(id);
        return ResponseEntity.ok("Опрос удалён");
    }

    /** Опросами управляет учебный отдел */
    private boolean isAdmin(AdminToken token, String userToken) {
        return adminAccess.allowed(token, userToken, AdminAccess.DISPATCHER);
    }

    private ResponseEntity<String> forbidden() {
        log.warn("Попытка доступа к административным функциям - доступ отказан");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ отказан");
    }

}
