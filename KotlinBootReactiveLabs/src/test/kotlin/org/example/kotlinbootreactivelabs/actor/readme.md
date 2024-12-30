# Project Overview

This project provides examples of implementing and testing actors in Kotlin using Pekko, an open-source cooldown version of Akka. The implemented code and test code are linked with related documents.

It operates within Spring Boot, and the test code uses Junit5.

## Prerequisites

### Integration with SpringBoot

- https://wiki.webnori.com/pages/viewpage.action?pageId=93946878

### [`Infra`](../../../../../../test/docker/)

- Configures database devices and DDL for the actor persistence CQRS part.

## Main Test Files

### [`HelloActorTest.kt`](core/HelloActorTest.kt)

- Describes and tests a basic actor that responds with "Kotlin" when a "Hello" event is triggered.
- Doc: https://pekko.apache.org/docs/pekko/current/typed/actor-lifecycle.html

### [`DiscoveryTest.kt`](discovery/DiscoveryTest.kt)

- Describes and tests how to access actor references (ActorRef) available locally and how to use the Receptionist to find actors available remotely and in a cluster.
- Doc: https://pekko.apache.org/docs/pekko/current/typed/actor-discovery.html

### [`HelloStateActorTest.kt`](state/HelloStateActorTest.kt)

- Describes and tests an actor that defines a state, changes the state value in response to events, and queries the state value.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/interaction-patterns.html#typed-scheduling

### [`TimerActorTest.kt`](timer/basic/TimerActorTest.kt)

- Describes and tests an actor with a built-in timer using its own scheduler functionality.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/interaction-patterns.html#typed-scheduling

### [`SupervisorActorTest.kt`](supervisor/SupervisorActorTest.kt)

- Describes and tests how an actor supports fault tolerance, where a parent actor manages the lifecycle of child actors and handles exceptions of child actors.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/fault-tolerance.html

### [`HelloRouterTest.kt`](router/HelloRouterTest.kt)

- Describes and tests how an actor can be configured for parallel and distributed processing through a router, providing various router devices.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/routers.html

### [`HelloStateStoreActorTest.kt`](state/store/HelloStateStoreActorTest.kt)

- Describes and tests how an actor can connect to a state store to save and restore its state. This sample uses a custom R2DBC connection.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/stash.html

### [`HelloPersistentDurableStateActorTest.kt`](persistent/durable/HelloPersistentDurableStateActorTest.kt)

- Describes and tests how an actor's state persistence can be custom-made or use provided plugins. Due to the cooldown version, it is recommended to configure persistence devices customarily as plugins may be delayed (plugin test results: usable after 2.7x).
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/index-persistence-durable-state.html

### [`ClusterTest.kt`](cluster/ClusterTest.kt)

- Describes and tests how an application with locally written actors can be configured as a cluster with just pekko.conf settings, ensuring that only one actor operates within the cluster, and actors can be used as if they were in-memory within a single application without knowing their location.
- The advantage is that you can design the event (domain) first and then attach the infrastructure later to expand. Although multi-threaded/network devices are utilized, the actor system's toolkit eliminates the need for direct programming.
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/cluster-singleton.html
- Doc: https://pekko.apache.org/docs/pekko/1.1/typed/cluster-sharding.html

## Additional Reference Links

This version is based on Akka (2.6x) and is compatible with Pekko (1.2).

- [Akka with Kotlin](https://wiki.webnori.com/display/AKKA/AKKA.Kotlin)