package org.example.kotlinbootreactivelabs.ws.actor.chat

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.AskPattern
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.javadsl.Receive
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingEvent
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingGroup
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingRequestInfo
import org.example.kotlinbootreactivelabs.ws.actor.chat.router.CounselingRouter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


sealed class CounselorManagerCommand
data class CreateCounselor(val name: String, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class CreateRoom(val roomName: String, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class RequestCounseling(val roomName: String, val skillInfo:CounselingRequestInfo, val personalRoomActor: ActorRef<PersonalRoomCommand>, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class GetCounselor(val name: String, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class GetCounselorRoom(val roomName: String, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class UpdateRoutingRule(val newRoutingRule: CounselingRouter, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
data class EvaluateRoutingRule(val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()
// Observer
data class AddObserverCounselor(val roomName: String, val observerName: String, val replyTo: ActorRef<CounselorManagerResponse>) : CounselorManagerCommand()


sealed class CounselorManagerResponse
data class CounselorCreated(val name: String) : CounselorManagerResponse()
data class ErrorResponse(val message: String) : CounselorManagerResponse()
data class CounselorFound(val name: String, val actorRef: ActorRef<CounselorCommand>) : CounselorManagerResponse()
data class CounselorRoomFound(val roomName: String, val actorRef: ActorRef<CounselorRoomCommand>) : CounselorManagerResponse()
data class CounselorManagerSystemResponse(val message: String) : CounselorManagerResponse()

class CounselorManagerActor private constructor(
    context: ActorContext<CounselorManagerCommand>
) : AbstractBehavior<CounselorManagerCommand>(context) {

    companion object {
        fun create(): Behavior<CounselorManagerCommand> {
            return Behaviors.setup { context -> CounselorManagerActor(context) }
        }
    }

    private var lastAssignedGroupIndex = 0  // For 더미테스트 RoundRobin

    private val eventLog = mutableListOf<CounselingEvent>()

    private val counselors = mutableMapOf<String, ActorRef<CounselorCommand>>()
    private val counselorRooms = mutableMapOf<String, ActorRef<CounselorRoomCommand>>()
    private var routingRule:CounselingRouter = CounselingRouter(
        counselingGroups = listOf(
            CounselingGroup(
                hashCodes = arrayOf("skill-1-0-0", "skill-1-0-1", "skill-1-0-2", "skill-1-0-3", "skill-1-0-4"),
                availableCounselors = mutableListOf(),
                lastAssignmentTime = System.currentTimeMillis(),
                availableSlots = 10
            ),
            CounselingGroup(
                hashCodes = arrayOf("skill-2-0-0", "skill-2-0-1", "skill-2-0-2", "skill-2-0-3", "skill-2-0-4"),
                availableCounselors = mutableListOf(),
                lastAssignmentTime = System.currentTimeMillis(),
                availableSlots = 10
            ),
            CounselingGroup(
                hashCodes = arrayOf("skill-3-0-0", "skill-3-0-1", "skill-3-0-2", "skill-3-0-3", "skill-3-0-4"),
                availableCounselors = mutableListOf(),
                lastAssignmentTime = System.currentTimeMillis(),
                availableSlots = 10
            )
        )
    )

    override fun createReceive(): Receive<CounselorManagerCommand> {
        return newReceiveBuilder()
            .onMessage(CreateCounselor::class.java, this::onCreateCounselor)
            .onMessage(CreateRoom::class.java, this::onCreateRoom)
            .onMessage(RequestCounseling::class.java, this::onRequestCounseling)
            .onMessage(GetCounselor::class.java, this::onGetCounselor)
            .onMessage(GetCounselorRoom::class.java, this::onGetCounselorRoom)
            .onMessage(UpdateRoutingRule::class.java, this::onUpdateRoutingRule)
            .onMessage(EvaluateRoutingRule::class.java, this::onEvaluateRoutingRule)
            .onMessage(AddObserverCounselor::class.java, this::onAddObserverCounselor)
            .build()
    }


    private fun onCreateCounselor(command: CreateCounselor): Behavior<CounselorManagerCommand> {
        if (counselors.containsKey(command.name)) {
            command.replyTo.tell(ErrorResponse("Counselor ${command.name} already exists."))
        } else {
            val counselorActor = context.spawn(CounselorActor.create(command.name), command.name)
            counselors[command.name] = counselorActor

            //For Dummy Test
            // Add the counselor to the next group in a round-robin manner
            val group = routingRule.counselingGroups[lastAssignedGroupIndex]
            (group.availableCounselors as MutableList).add(counselorActor)
            lastAssignedGroupIndex = (lastAssignedGroupIndex + 1) % routingRule.counselingGroups.size


            command.replyTo.tell(CounselorCreated(command.name))
        }
        return this
    }

    private fun onCreateRoom(command: CreateRoom): Behavior<CounselorManagerCommand> {
        if (counselorRooms.containsKey(command.roomName)) {
            command.replyTo.tell(ErrorResponse("Counselor ${command.roomName} already exists."))
        } else {
            val counselorRoomActor = context.spawn(CounselorRoomActor.create(command.roomName), command.roomName)
            counselorRooms[command.roomName] = counselorRoomActor
            command.replyTo.tell(CounselorCreated(command.roomName))
        }
        return this
    }

    private fun onRequestCounseling(command: RequestCounseling): Behavior<CounselorManagerCommand> {
        // 상담분배로직
        val group = routingRule.findHighestPriorityGroup(command.skillInfo.generateHashCode())
        val availableCounselor = group?.findNextAvailableCounselor()

        if (availableCounselor == null) {
            //command.replyTo.tell(ErrorResponse("No available counselors."))
            context.log.error("No available counselors.")
            return this
        }

        // Decrease available slots
        group.decreaseAvailableSlots()

        // Create Room - 가용상담원이 있을대만 방생성진행
        val roomName = command.roomName
        val counselorRoomActor = context.spawn(CounselorRoomActor.create(roomName), roomName)
        counselorRooms[roomName] = counselorRoomActor

        // Invite PersonalRoomActor
        //counselorRoomActor.tell(InvitePersonalRoomActor(command.personalRoomActor, context.self.narrow() ))
        AskPattern.ask(
            counselorRoomActor,
            { replyTo: ActorRef<CounselorRoomResponse> -> InvitePersonalRoomActor(command.personalRoomActor, replyTo) },
            Duration.ofSeconds(3),
            context.system.scheduler()
        ).thenAccept { res2 ->
            if (res2 is CounselorRoomResponse) {
                // # 상담방 연결 Core 이벤트
                // 고객방에 상담방 연결
                command.personalRoomActor.tell(SetCounselorRoom(counselorRoomActor))
                command.personalRoomActor.tell(SendTextMessage("Invitation to counseling room completed."))

                // 상담방에 가용 상담원 연결
                counselorRoomActor.tell(AssignCounselor(availableCounselor))

                // 고객 <- 상담방 -> 상담원 연결 수립

                // 상담원에게 할당된 상담방정보 추가
                availableCounselor.tell(AssignRoom(roomName, command.personalRoomActor, counselorRoomActor))


                // Log the successful connection event
                val event = CounselingEvent(
                    eventType = "CounselingConnectionSuccess",
                    counselorName = availableCounselor.path().name(),
                    roomName = command.roomName,
                    timestamp = System.currentTimeMillis(),
                    groupId = group.id
                )
                eventLog.add(event)
            }
        }

        command.replyTo.tell(CounselorRoomFound(roomName, counselorRoomActor))

        return this
    }

    private fun onUpdateRoutingRule(command: UpdateRoutingRule): Behavior<CounselorManagerCommand> {
        routingRule = command.newRoutingRule

        // Reassign available counselors to the new routing rule
        var index = 0
        val counselorList = counselors.values.toList()
        routingRule.counselingGroups.forEach { group ->
            //group.availableCounselors.clear()
            group.availableCounselors = mutableListOf()
            repeat(group.availableSlots) {
                if (index < counselorList.size) {
                    (group.availableCounselors as MutableList).add(counselorList[index])
                    index++
                }
            }
        }

        command.replyTo.tell(CounselorManagerSystemResponse("Routing rule updated successfully."))
        return this
    }

    private fun onGetCounselor(command: GetCounselor): Behavior<CounselorManagerCommand> {
        var counselorActor = counselors[command.name]
        if(counselorActor == null) {
            command.replyTo.tell(ErrorResponse("Counselor ${command.name} not found."))
        }

        command.replyTo.tell(counselorActor?.let { CounselorFound(command.name, it) })
        return this
    }

    private fun onGetCounselorRoom(command: GetCounselorRoom): Behavior<CounselorManagerCommand> {

        var counselorRoomActor = counselorRooms[command.roomName]
        if(counselorRoomActor == null) {
            counselorRoomActor = context.spawn(CounselorRoomActor.create(command.roomName), command.roomName)
            counselorRooms[command.roomName] = counselorRoomActor
        }

        command.replyTo.tell(counselorRoomActor?.let { CounselorRoomFound(command.roomName, it) })
        return this
    }

    private fun onEvaluateRoutingRule(command: EvaluateRoutingRule): Behavior<CounselorManagerCommand> {
        val evaluationReport = StringBuilder("Evaluation Report:\n")
        routingRule.counselingGroups.forEachIndexed { index, group ->
            evaluationReport.append("Group[${group.id}] ${group.hashCodes.joinToString(", ")} availableSlots:${group.availableSlots}  available counselors: ${group.availableCounselors.size}\n")
        }

        evaluationReport.append("========== Events ===========:\n")
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        eventLog.forEach { event ->
            val formattedTimestamp = formatter.format(Instant.ofEpochMilli(event.timestamp))
            evaluationReport.append("EventType: ${event.eventType}, GroupId: ${event.groupId}, RoomName: ${event.roomName}, CounselorName: ${event.counselorName}, Timestamp: $formattedTimestamp\n")
        }

        command.replyTo.tell(CounselorManagerSystemResponse(evaluationReport.toString()))
        return this
    }

    private fun onAddObserverCounselor(command: AddObserverCounselor): Behavior<CounselorManagerCommand> {
        val counselorRoomActor = counselorRooms[command.roomName]
        val observer = counselors[command.observerName]
        return if (counselorRoomActor != null && observer != null) {
            counselorRoomActor.tell(InviteObserver(observer))
            command.replyTo.tell(CounselorManagerSystemResponse("Observer counselor ${command.observerName} added to room: ${command.roomName}"))
            this
        } else {
            val errorMessage = when {
                counselorRoomActor == null -> "Room ${command.roomName} not found."
                observer == null -> "Observer counselor ${command.observerName} not found."
                else -> "Unknown error."
            }
            command.replyTo.tell(ErrorResponse(errorMessage))
            this
        }
    }
}