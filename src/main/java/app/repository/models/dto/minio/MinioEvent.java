package app.repository.models.dto.minio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinioEvent {

    @JsonProperty("EventName")
    private String eventName;

    @JsonProperty("Key")
    private String key;

    @JsonProperty("Records")
    private List<Record> records;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record {

        private S3 s3;
        private Source source;
        private String awsRegion;
        private String eventName;
        private String eventTime;
        private String eventSource;
        private String eventVersion;
        private UserIdentity userIdentity;
        private ResponseElements responseElements;
        private RequestParameters requestParameters;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class S3 {
            private Bucket bucket;
            private S3Object object;
            private String configurationId;
            private String s3SchemaVersion;

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Bucket {
                private String arn;
                private String name;
                private OwnerIdentity ownerIdentity;

                @Data
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class OwnerIdentity {
                    private String principalId;
                }
            }

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class S3Object {
                private String key;
                private String eTag;
                private long size;
                private String sequencer;
                private String contentType;
                private UserMetadata userMetadata;

                @Data
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class UserMetadata {
                    private String contentType;
                }
            }
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Source {
            private String host;
            private String port;
            private String userAgent;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class UserIdentity {
            private String principalId;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ResponseElements {
            private String xAmzId2;
            private String xAmzRequestId;
            private String xMinioDeploymentId;
            private String xMinioOriginEndpoint;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RequestParameters {
            private String region;
            private String principalId;
            private String sourceIPAddress;
        }

    }

}
