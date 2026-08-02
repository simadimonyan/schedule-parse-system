package app.service.storage;

import io.minio.*;
import io.minio.messages.EventType;
import io.minio.messages.Item;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
public class StorageService {

    private static final String BUCKET = "schedule";

    private final MinioClient client;

    @Autowired
    public StorageService(MinioClient client) {
        this.client = client;
        setupWebhook();
    }

    public InputStream getObjectByName(String fileName) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(BUCKET).object(objectKey(fileName)).build());
        } catch (Exception e) {
            log.error("Не удалось получить файл {} из бакета {}", fileName, BUCKET, e);
            return null;
        }
    }

    /**
     * Имя объекта внутри бакета.
     *
     * <p>Вебхук MinIO присылает ключ вместе с бакетом — {@code schedule/файл.xlsx}, — а
     * запрос на разбор приходит с одним именем файла. Обрезается только первый сегмент, и
     * только если он совпал с бакетом: имя файла само может содержать слэш.
     */
    private static String objectKey(String fileName) {
        if (fileName == null) return null;

        String prefix = BUCKET + "/";
        return fileName.startsWith(prefix) ? fileName.substring(prefix.length()) : fileName;
    }

    /**
     * Последний загруженный файл расписания или {@code null}, если бакет пуст.
     *
     * <p>Выбирается по времени изменения, а не по имени: файлы называют по курсу и форме
     * обучения, и лексикографический порядок к порядку загрузки отношения не имеет.
     */
    public String latestObjectName() {
        try {
            String newestName = null;
            ZonedDateTime newestAt = null;

            for (Result<Item> result : client.listObjects(
                    ListObjectsArgs.builder().bucket(BUCKET).build())) {

                Item item = result.get();
                if (item.isDir()) continue;

                ZonedDateTime modifiedAt = item.lastModified();
                if (newestAt == null || (modifiedAt != null && modifiedAt.isAfter(newestAt))) {
                    newestAt = modifiedAt;
                    newestName = item.objectName();
                }
            }

            if (newestName == null) {
                log.error("Бакет {} пуст — файл расписания не загружен", BUCKET);
                return null;
            }

            log.info("В бакете {} взят последний файл: {} ({})", BUCKET, newestName, newestAt);
            return newestName;
        } catch (Exception e) {
            log.error("Не удалось перечислить файлы бакета {}", BUCKET, e);
            return null;
        }
    }

    /** Есть ли такой файл в бакете: разбор несуществующего лучше отклонить сразу. */
    public boolean exists(String fileName) {
        try {
            client.statObject(StatObjectArgs.builder()
                    .bucket(BUCKET).object(objectKey(fileName)).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setupWebhook() {
        try {
            NotificationConfiguration notification = new NotificationConfiguration();

            // MinIO SQS (Simple Queue Service) query
            List<QueueConfiguration> queueConfigurationList = new LinkedList<>();
            QueueConfiguration queueConfiguration = new QueueConfiguration();
            queueConfiguration.setQueue("arn:minio:sqs::1:webhook");

            List<EventType> eventTypeList = new LinkedList<>();
            eventTypeList.add(EventType.OBJECT_CREATED_ANY);
            queueConfiguration.setEvents(eventTypeList);
            queueConfiguration.setPrefixRule("schedule");
            queueConfiguration.setSuffixRule("xlsx");

            queueConfigurationList.add(queueConfiguration);
            notification.setQueueConfigurationList(queueConfigurationList);

            client.setBucketNotification(
                SetBucketNotificationArgs.builder()
                    .bucket("schedule")
                    .config(notification)
                    .build()
            );
        }
        catch (Exception e) {
            log.error(e.toString());
        }

    }

}
