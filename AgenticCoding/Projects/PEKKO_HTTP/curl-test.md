# Pekko HTTP API - cURL Test Guide

This document provides cURL commands to test all endpoints of the Pekko HTTP server.

## Prerequisites

Start the server:
```bash
./gradlew run
# Or use shadow JAR
./gradlew shadowJar
java -jar build/libs/pekko-http-server.jar
```

The server will start on `http://localhost:8080`

## 🌐 Swagger UI & API Documentation

```bash
# Open Swagger UI in browser
open http://localhost:8080/swagger-ui  # Mac
xdg-open http://localhost:8080/swagger-ui  # Linux
start http://localhost:8080/swagger-ui  # Windows

# Get OpenAPI specification
curl -X GET http://localhost:8080/api-docs | jq

# Alternative OpenAPI endpoint
curl -X GET http://localhost:8080/swagger.json | jq
```

## Health Check

```bash
# Health check
curl -X GET http://localhost:8080/health
```

## Hello Endpoints

### GET Default Hello
```bash
curl -X GET http://localhost:8080/api/hello
```

Expected response:
```json
{"message":"Pekko says hello to the World!"}
```

### GET Hello with Name
```bash
curl -X GET http://localhost:8080/api/hello/Alice
```

Expected response:
```json
{"message":"Pekko says hello to Alice!"}
```

### POST Hello with JSON Body
```bash
curl -X POST http://localhost:8080/api/hello \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob"}'
```

Expected response:
```json
{"message":"Pekko says hello to Bob!"}
```

### Special Name Responses
```bash
# Test special responses
curl -X GET http://localhost:8080/api/hello/pekko
curl -X GET http://localhost:8080/api/hello/akka
curl -X GET http://localhost:8080/api/hello/hello
```

## Event Endpoints

### Send Single Event
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "eventType": "click",
    "action": "button_click",
    "metadata": {
      "button": "submit",
      "page": "checkout"
    }
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "Event accepted for processing",
  "eventId": "uuid-here"
}
```

### Send Batch of Events
```bash
curl -X POST http://localhost:8080/api/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "userId": "user1",
      "eventType": "click",
      "action": "button_click",
      "metadata": {"button": "home"}
    },
    {
      "userId": "user2",
      "eventType": "view",
      "action": "page_view",
      "metadata": {"page": "products"}
    },
    {
      "userId": "user3",
      "eventType": "submit",
      "action": "form_submit",
      "metadata": {"form": "contact"}
    }
  ]'
```

### Get Event Statistics
```bash
curl -X GET http://localhost:8080/api/events/stats
```

Expected response:
```json
{
  "totalEvents": 4,
  "eventsPerType": {
    "click": 2,
    "view": 1,
    "submit": 1
  },
  "eventsPerUser": {
    "user123": 1,
    "user1": 1,
    "user2": 1,
    "user3": 1
  },
  "lastEventTime": "2024-01-01T12:00:00Z"
}
```

### Server-Sent Events Stream
```bash
# Connect to SSE stream (will keep connection open)
curl -X GET http://localhost:8080/api/events/stream \
  -H "Accept: text/event-stream"
```

This will open a persistent connection and stream event statistics every 2 seconds.

## WebSocket Testing

### Using wscat (install with: npm install -g wscat)
```bash
# Connect to WebSocket
wscat -c ws://localhost:8080/ws

# Or with user ID
wscat -c ws://localhost:8080/ws/user123

# Once connected, type messages and press Enter
> Hello from client!
< {"type":"server","content":"Echo: Hello from client!","sender":"server"}
```

### Using curl (WebSocket not fully supported, but can test connection)
```bash
# Test WebSocket upgrade
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
  http://localhost:8080/ws
```

### Broadcast Message to All WebSocket Clients
```bash
curl -X POST http://localhost:8080/api/broadcast \
  -H "Content-Type: application/json" \
  -d '{"message":"System announcement: Server maintenance at 10 PM"}'
```

## API Documentation

### Get OpenAPI Specification
```bash
curl -X GET http://localhost:8080/api-docs
```

### Alternative Swagger JSON endpoint
```bash
curl -X GET http://localhost:8080/swagger.json
```

### Access Swagger UI
Open in browser:
```
http://localhost:8080/swagger
```

## Testing Scenarios

### Load Test - Send Multiple Events
```bash
# Send 10 events in quick succession
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/events \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": \"user$i\",
      \"eventType\": \"test\",
      \"action\": \"load_test_$i\",
      \"metadata\": {\"iteration\": $i}
    }" &
done
wait
```

### Test Concurrent Hello Requests
```bash
# Send 5 concurrent hello requests
for name in Alice Bob Charlie Diana Eve; do
  curl -X GET "http://localhost:8080/api/hello/$name" &
done
wait
```

### Monitor Event Stream with Filtering
```bash
# Use jq to pretty-print SSE stream (install jq if needed)
curl -s -X GET http://localhost:8080/api/events/stream \
  -H "Accept: text/event-stream" | \
  while read -r line; do
    if [[ $line == data:* ]]; then
      echo "${line:5}" | jq '.'
    fi
  done
```

## Error Testing

### Invalid JSON
```bash
curl -X POST http://localhost:8080/api/hello \
  -H "Content-Type: application/json" \
  -d 'invalid json'
```

### Missing Required Fields
```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"userId": "test"}'
```

### Non-existent Endpoint
```bash
curl -X GET http://localhost:8080/api/nonexistent
```

## Performance Testing

### Using Apache Bench (ab)
```bash
# Test Hello endpoint performance
ab -n 1000 -c 10 http://localhost:8080/api/hello/

# Test Event posting performance
ab -n 100 -c 5 -p event.json -T application/json \
  http://localhost:8080/api/events
```

### Using hey (modern replacement for ab)
```bash
# Install: go install github.com/rakyll/hey@latest
hey -n 1000 -c 10 http://localhost:8080/api/hello
```

## Debugging Tips

### Verbose curl output
```bash
curl -v -X GET http://localhost:8080/api/hello
```

### Include response headers
```bash
curl -i -X GET http://localhost:8080/api/hello
```

### Save response to file
```bash
curl -X GET http://localhost:8080/api/events/stats -o stats.json
```

### Measure response time
```bash
curl -w "\nTime: %{time_total}s\n" -X GET http://localhost:8080/api/hello
```