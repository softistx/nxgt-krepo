package com.softistx.material.school.data

import kotlin.time.Instant

data class Message(
    val id: Int,
    val fromMe: Boolean,
    val text: String,
    val at: Instant,
    val attachment: String? = null,
)

data class Conversation(
    val id: Int,
    val title: String,
    val subtitle: String,
    val online: Boolean,
    val unread: Int,
    val messages: List<Message>,
    val studentId: Int? = null,
) {
    val last: Message get() = messages.last()
}

object Conversations {
    val all: List<Conversation> =
        listOf(
            Conversation(
                1, "Claire Garcia", "Mother of Amara Garcia, 7A", true, 2,
                listOf(
                    Message(1, false, "Good morning, could Amara retake Friday's algebra quiz? She was at the dentist.", ago(300)),
                    Message(2, true, "Of course. She can sit it on Thursday at lunch in R101.", ago(240)),
                    Message(3, false, "Thank you! Attaching the dentist's note for the file.", ago(35), "dentist-note.pdf"),
                    Message(4, false, "Is there anything she should revise beforehand?", ago(30)),
                ),
                studentId = 1,
            ),
            Conversation(
                2, "Staff room", "14 teachers", false, 0,
                listOf(
                    Message(5, false, "Reminder: marks for term 1 are due on Friday.", ago(1500)),
                    Message(6, true, "Mine are in for 7A and 8B, finishing 7B tonight.", ago(1400)),
                    Message(7, false, "Great, thanks Elena.", ago(1380)),
                ),
            ),
            Conversation(
                3, "Daniel Costa", "Father of Kofi Costa, 7B", false, 1,
                listOf(
                    Message(8, false, "Kofi says the project deadline moved. Is that right?", ago(2900)),
                    Message(9, true, "Yes, it is now 6 November. The brief is on the portal.", ago(2800)),
                    Message(10, false, "Perfect, see you at the parent evening.", ago(2700)),
                ),
                studentId = 9,
            ),
            Conversation(
                4, "Principal Moreau", "Leadership", true, 0,
                listOf(
                    Message(11, false, "Can you cover Mr. Brandt's 8A physics class on Thursday?", ago(4300)),
                    Message(12, true, "Yes, period 3 is free for me.", ago(4200)),
                ),
            ),
            Conversation(
                5, "Priya Torres", "Student, 8B", false, 0,
                listOf(
                    Message(13, false, "Miss, I handed my homework in late because the portal was down.", ago(5800)),
                    Message(14, true, "No problem, I will not count it as late.", ago(5700)),
                ),
                studentId = 25,
            ),
        )

    fun byId(id: Int): Conversation? = all.firstOrNull { it.id == id }
}
