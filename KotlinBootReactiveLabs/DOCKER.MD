## Ndev Build

``` shell
docker build -f Dockerfile -t neo-kotlin-labs:dev .
```

## Ndev Run

``` shell
docker run -p 8080:8080 --name neo-kotlin-labs neo-kotlin-labs:dev
```


## Spring Boot Multi Run for Cluster

### Standalone

``` shell
./gradlew bootRun -PserverPort=9090 -PclusterConfig=standalone
```

### Seed Node
``` shell
./gradlew bootRun -PserverPort=9090 -PclusterConfig=lighthouse
```

### Node1
``` shell
./gradlew bootRun -PserverPort=9091 -PclusterConfig=cluster1
```

### Node2
``` shell
./gradlew bootRun -PserverPort=9092 -PclusterConfig=cluster2
```