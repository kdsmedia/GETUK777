package application.query.sportbook

import application.IQuery

/** Anonymous SDK bootstrap: which SDK to load and its init payload, no player required. */
data object InitSportbookQuery : IQuery<SportbookInit>

data class SportbookInit(
    val integration: String,
    val data: Map<String, String>
)
