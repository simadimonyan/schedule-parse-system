FROM bellsoft/liberica-runtime-container:jdk-21-glibc

# Устанавливаем bash и необходимые зависимости
RUN /bin/sh -c "if [ ! -f /usr/bin/bash ]; then apk add --no-cache bash; fi"

# Скачиваем и устанавливаем Gradle
RUN wget https://services.gradle.org/distributions/gradle-8.7-bin.zip -O /tmp/gradle.zip \
    && unzip /tmp/gradle.zip -d /opt/ \
    && rm /tmp/gradle.zip \
    && ln -s /opt/gradle-8.7 /opt/gradle

# Устанавливаем переменные среды
ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

COPY . /service
WORKDIR /service/schedule-parse-service

# Запуск сборки и запускаем Spring Boot приложение
CMD gradle bootRun