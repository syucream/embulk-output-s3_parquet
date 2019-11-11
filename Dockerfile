FROM openjdk:8u232-jre

ENV VERSION 0.9.19

RUN curl -L https://dl.bintray.com/embulk/maven/embulk-${VERSION}.jar -o embulk.jar && \
    chmod +x embulk.jar

RUN ./embulk.jar gem install embulk-output-s3_parquet

ADD example example

CMD ./embulk.jar run example/config_nested.yml
