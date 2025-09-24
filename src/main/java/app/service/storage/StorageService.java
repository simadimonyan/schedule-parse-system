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
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
public class StorageService {

    private final MinioClient client;

    @Autowired
    public StorageService(MinioClient client) {
        this.client = client;
        setupWebhook();
    }

    public InputStream getObjectByName(String fileName) {
        try {
//            Iterable<Result<Item>> results =
//                    client.listObjects(ListObjectsArgs.builder()
//                            .bucket("schedule").maxKeys(1).build());
//
//            Item object = results.iterator().next().get();

            return client.getObject(GetObjectArgs.builder()
                    .bucket("schedule").object(fileName).build());
        } catch (Exception e) {
            System.out.println("Exception occurred while downloading: " + e);
            return null;
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
