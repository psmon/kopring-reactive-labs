package org.example.kotlinbootreactivelabs.ws.actor.chat.router

import org.apache.pekko.actor.typed.ActorRef
import org.example.kotlinbootreactivelabs.ws.actor.chat.CounselorCommand
import java.util.Random
import java.util.UUID

data class CounselingEvent(
    val eventType: String,
    var groupId: String,
    val roomName: String,
    val counselorName: String,
    val timestamp: Long
)

data class CounselingRequestInfo(
    val skillCode1: Int,
    val skillCode2: Int,
    val skillCode3: Int
) {
    companion object {
        val ALL = CounselingRequestInfo(0, 0, 0)
    }

    fun generateHashCode(): String {
        return "skill-$skillCode1-$skillCode2-$skillCode3"
    }
}

data class CounselingGroup(
    val id: String = UUID.randomUUID().toString(),
    val hashCodes: Array<String>,
    // TODO : 상담원이 가용상태가 아닐때 실시간으로 제거 ( 별도로직 필요 )
    var availableCounselors: List<ActorRef<CounselorCommand>>,
    val lastAssignmentTime: Long,
    var availableSlots: Int = 100
){
    private var lastAssignedCounselorIndex = 0

    fun findNextAvailableCounselor(): ActorRef<CounselorCommand>? {
        if (availableCounselors.isEmpty()) return null

        val counselor = availableCounselors[lastAssignedCounselorIndex]
        lastAssignedCounselorIndex = (lastAssignedCounselorIndex + 1) % availableCounselors.size

        return counselor
    }

    fun decreaseAvailableSlots() {
        if (availableSlots > 0) {
            availableSlots -= 1
        }
    }

}

data class CounselingRouter(
    val counselingGroups: List<CounselingGroup>
){
    fun findHighestPriorityGroup(generateHashCode: String): CounselingGroup? {
        return counselingGroups
            .filter { it.availableSlots > 0 }
            .maxByOrNull { calculateWeight(it) }
    }

    private fun calculateWeight(group: CounselingGroup): Int {
        val skillWeight = (group.hashCodes.size - group.hashCodes.indexOfFirst { it.isNotEmpty() }) * 1000
        val lastAssignmentWeight = (System.currentTimeMillis() - group.lastAssignmentTime).toInt()
        val availableSlotsWeight = group.availableSlots * 100
        return skillWeight + lastAssignmentWeight + availableSlotsWeight
    }
}

fun generateRandomSkillInfo(): CounselingRequestInfo {
    val random = Random()
    val skillCode1 = random.nextInt(4) // Generates a random number between 0 and 3
    val skillCode2 = random.nextInt(4)
    val skillCode3 = random.nextInt(4)
    return CounselingRequestInfo(skillCode1, skillCode2, skillCode3)
}