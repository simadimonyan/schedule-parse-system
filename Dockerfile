FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y bash wget unzip \
    && rm -rf /var/lib/apt/lists/*

# Скачиваем и устанавливаем Gradle
RUN wget https://services.gradle.org/distributions/gradle-8.7-bin.zip -O /tmp/gradle.zip \
    && unzip /tmp/gradle.zip -d /opt/ \
    && rm /tmp/gradle.zip \
    && ln -s /opt/gradle-8.7 /opt/gradle

# Устанавливаем переменные среды
ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

COPY . /service/schedule-parse-service
WORKDIR /service/schedule-parse-service

# Запуск сборки и запускаем Spring Boot приложение
CMD ["gradle", "bootRun"]