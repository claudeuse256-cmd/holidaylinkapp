package org.holidaylink.app.ui

import org.holidaylink.app.data.AppSettings

/**
 * Light-weight translation for the app's interface.
 *
 * The screens were written with English text in the code, so instead of rewriting every
 * screen, [tr] looks an English phrase up in the table of the language chosen in
 * Settings › Language and falls back to the English text when there is no translation yet.
 * It reads the language setting, so the screens redraw as soon as the language is changed.
 *
 * To translate more, add the phrase to the three tables below and wrap the text in the
 * screen with tr("…"). The Swahili, Luganda and French wording should be checked by a native
 * speaker before release.
 */
fun tr(english: String): String {
    val table = when (AppSettings.state.language) {
        "sw" -> SWAHILI
        "lg" -> LUGANDA
        "fr" -> FRENCH
        else -> return english
    }
    return table[english] ?: english
}

private val SWAHILI = mapOf(
    "Chats" to "Mazungumzo", "Status" to "Hali", "Settings" to "Mipangilio", "Calls" to "Simu",
    "Message" to "Ujumbe", "Cancel" to "Ghairi", "Close" to "Funga", "Save" to "Hifadhi",
    "Share" to "Shiriki", "Remove" to "Ondoa", "Delete" to "Futa", "Done" to "Imekamilika",
    "Not now" to "Si sasa", "Try again" to "Jaribu tena", "Retry" to "Jaribu tena",
    "Reply" to "Jibu", "Edit" to "Hariri", "Continue" to "Endelea", "Add" to "Ongeza",
    "Send" to "Tuma", "Keep" to "Weka", "Unblock" to "Ondoa kizuizi", "Search chats" to "Tafuta mazungumzo",
    "Loading…" to "Inapakia…", "View profile" to "Tazama wasifu", "Stickers" to "Vibandiko",
    "My stickers" to "Vibandiko vyangu", "Emoji" to "Emoji", "Update available" to "Sasisho linapatikana",
    "Update now" to "Sasisha sasa", "Download update" to "Pakua sasisho", "Recheck" to "Angalia tena",
    "New chat" to "Mazungumzo mapya", "Language" to "Lugha", "App language" to "Lugha ya programu",
    "Set tag" to "Weka lebo", "Tag" to "Lebo", "Report" to "Ripoti", "Search" to "Tafuta",
    "Starred messages" to "Ujumbe uliotiwa nyota", "Your status" to "Hali yako", "Uploading…" to "Inapakia…"
)

private val LUGANDA = mapOf(
    "Chats" to "Okwogera", "Status" to "Embeera", "Settings" to "Enteekateeka", "Calls" to "Essimu",
    "Message" to "Obubaka", "Cancel" to "Sazaamu", "Close" to "Ggalawo", "Save" to "Tereka",
    "Share" to "Gabana", "Remove" to "Ggyawo", "Delete" to "Sangula", "Done" to "Kiwedde",
    "Not now" to "Si kati", "Try again" to "Ddamu ogezeeko", "Retry" to "Ddamu ogezeeko",
    "Reply" to "Ddamu", "Edit" to "Kyusa", "Continue" to "Weyongereyo", "Add" to "Gattako",
    "Send" to "Weereza", "Keep" to "Kuuma", "Unblock" to "Ggyawo ekizibu", "Search chats" to "Noonya okwogera",
    "Loading…" to "Kitikkiddwa…", "View profile" to "Laba ebikukwatako", "Stickers" to "Sitikka",
    "My stickers" to "Sitikka zange", "Emoji" to "Emoji", "Update available" to "Okuvugurura kuliwo",
    "Update now" to "Vugurura kati", "Download update" to "Kuguza okuvugurura", "Recheck" to "Ddamu olabe",
    "New chat" to "Okwogera okuggya", "Language" to "Olulimi", "App language" to "Olulimi lwa app",
    "Set tag" to "Teekawo akabonero", "Tag" to "Akabonero", "Report" to "Waaza", "Search" to "Noonya",
    "Starred messages" to "Obubaka obulina emmunyeenye", "Your status" to "Embeera yo", "Uploading…" to "Kitikkiddwa…"
)

private val FRENCH = mapOf(
    "Chats" to "Discussions", "Status" to "Statut", "Settings" to "Paramètres", "Calls" to "Appels",
    "Message" to "Message", "Cancel" to "Annuler", "Close" to "Fermer", "Save" to "Enregistrer",
    "Share" to "Partager", "Remove" to "Retirer", "Delete" to "Supprimer", "Done" to "Terminé",
    "Not now" to "Pas maintenant", "Try again" to "Réessayer", "Retry" to "Réessayer",
    "Reply" to "Répondre", "Edit" to "Modifier", "Continue" to "Continuer", "Add" to "Ajouter",
    "Send" to "Envoyer", "Keep" to "Garder", "Unblock" to "Débloquer", "Search chats" to "Rechercher des discussions",
    "Loading…" to "Chargement…", "View profile" to "Voir le profil", "Stickers" to "Autocollants",
    "My stickers" to "Mes autocollants", "Emoji" to "Emoji", "Update available" to "Mise à jour disponible",
    "Update now" to "Mettre à jour", "Download update" to "Télécharger la mise à jour", "Recheck" to "Revérifier",
    "New chat" to "Nouvelle discussion", "Language" to "Langue", "App language" to "Langue de l'application",
    "Set tag" to "Définir une étiquette", "Tag" to "Étiquette", "Report" to "Signaler", "Search" to "Rechercher",
    "Starred messages" to "Messages importants", "Your status" to "Votre statut", "Uploading…" to "Envoi…"
)
