FROM ubuntu:latest

RUN apt-get update && \
    apt-get install -y openjdk-21-jdk && \
    apt-get clean;

RUN ldconfig
RUN java -version
