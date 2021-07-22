#!/usr/bin/env bash

docker-compose stop && \
yes | docker-compose rm && \
sbt ";project courierService; docker:publishLocal; project orderAssignmentService; docker:publishLocal" && \
docker-compose up --remove-orphans -d && \
sbt ";run"