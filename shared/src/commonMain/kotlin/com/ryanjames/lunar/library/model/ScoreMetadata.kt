package com.ryanjames.lunar.library.model

import kotlinx.serialization.Serializable

const val SCORE_METADATA_SCHEMA_ID = "score-metadata"
const val SCORE_METADATA_SCHEMA_VERSION = "1.0"

@Serializable
data class ScoreMetadata(
    val schemaId: String = SCORE_METADATA_SCHEMA_ID,
    val schemaVersion: String = SCORE_METADATA_SCHEMA_VERSION,
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val alternativeTitles: List<String> = emptyList(),
    val composer: ScoreMetadataComposer = ScoreMetadataComposer(),
    val catalogueNumber: String = "",
    val opusNumber: String = "",
    val workNumber: String = "",
    val genre: String = "",
    val form: String = "",
    val stylePeriod: String = "",
    val instrumentation: List<String> = emptyList(),
    val key: String = "",
    val timeSignature: String = "",
    val tempoMarkings: List<String> = emptyList(),
    val movements: List<String> = emptyList(),
    val yearComposed: Int? = null,
    val publisher: String = "",
    val editor: String = "",
    val arranger: String = "",
    val edition: String = "",
    val language: String = "",
    val pageCount: Int? = null,
    val durationSeconds: Int? = null,
    val difficulty: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val source: ScoreMetadataSource = ScoreMetadataSource(),
)

@Serializable
data class ScoreMetadataComposer(
    val name: String = "",
    val birthYear: Int? = null,
    val deathYear: Int? = null,
)

@Serializable
data class ScoreMetadataSource(
    val filename: String = "",
    val fileType: String = "pdf",
    val url: String = "",
)
