#!/usr/bin/env bash

sbt ";project courierService; docker:publishLocal; project orderAssignmentService; docker:publishLocal" && \
docker-compose up --remove-orphans