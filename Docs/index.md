# Tutorial: KotlinBootReactiveLabs

This project showcases a **reactive system** built with *Apache Pekko Typed* actors in **Kotlin**.
The **MainStageActor** acts like a conductor, starting up key actors for web sessions and user events.
**SupervisorActor** ensures child actors remain stable by restarting them if they fail.
**HelloActor** is a simple *greeter* that responds with “Kotlin.”
**BulkProcessor** gathers items in batches before flushing them all at once.
*HelloPersistentDurableStateActor* remembers its state across restarts, unlike the *HelloStateActor* which only keeps in-memory data.
Meanwhile, **UserSessionManagerActor** manages WebSocket connections, and **CounselorManagerActor** organizes virtual counseling rooms and participants.


- [English Documentation](./eng/index.md)
- [Korean Documentation](./kr/index.md)
