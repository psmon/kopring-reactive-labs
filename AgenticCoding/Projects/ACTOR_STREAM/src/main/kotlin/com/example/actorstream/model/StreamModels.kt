package com.example.actorstream.model

sealed interface StreamCommand

data class ProcessText(val text: String) : StreamCommand
data class StreamResult(
    val originalText: String,
    val words: List<String>,
    val wordCount: Int,
    val numberSum: Int
) : StreamCommand

object StreamCompleted : StreamCommand