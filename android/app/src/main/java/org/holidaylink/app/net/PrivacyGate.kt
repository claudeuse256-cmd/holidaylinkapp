package org.holidaylink.app.net

/**
 * Applies someone else's privacy choices when THEIR profile is shown to you.
 *
 * The settings screen only saved the choices (users/{uid}.privacy_*); nothing read them back, so
 * "Nobody can see my last seen / photo / About" changed nothing for the other side. Every place
 * that turns another user's document into something on screen now asks this gate first, so a
 * change takes effect the next time the other person's profile is read (live lists update at once).
 *
 * "My contacts" means people the owner already has a conversation with: the app cannot read
 * anyone's address book, and a chat is the closest shared relationship it can see.
 * NOTE: this is enforced by the app. The data itself is still readable by a modified client,
 * which only a Firestore security-rules change (private sub-document) can fully prevent.
 */
object PrivacyGate {

    /** People the signed-in user has a direct conversation with (filled from the chat list). */
    val contacts: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** Last seen / online ("lastSeen"), profile picture ("profilePhoto"), "about". */
    fun canSee(data: Map<String, Any?>, ownerId: String, key: String): Boolean {
        val me = FirebaseBackend.uid
        if (me == null || me == ownerId) return true
        return when (Audience.from(data["privacy_$key"] as? String)) {
            Audience.EVERYONE -> true
            Audience.CONTACTS -> ownerId in contacts
            Audience.NOBODY -> false
        }
    }

    /** Phone number: everyone / my contacts / nobody / only the people the owner selected. */
    fun canSeePhone(data: Map<String, Any?>, ownerId: String): Boolean {
        val me = FirebaseBackend.uid
        if (me == null || me == ownerId) return true
        return when (data["privacy_phone"] as? String) {
            "nobody" -> false
            "contacts" -> ownerId in contacts
            "selected" -> (data["privacy_phoneOnly"] as? List<*>)?.contains(me) == true
            else -> true
        }
    }
}
