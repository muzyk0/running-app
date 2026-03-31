FROM golang:1.26.1-alpine AS build

WORKDIR /src

COPY go.mod ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/running-app-backend ./cmd/server

FROM alpine:3.22

RUN adduser -D -u 10001 runningapp

WORKDIR /app

COPY --from=build /out/running-app-backend /usr/local/bin/running-app-backend

ENV RUNNING_APP_HTTP_ADDR=:8080
EXPOSE 8080

USER runningapp

ENTRYPOINT ["running-app-backend"]
