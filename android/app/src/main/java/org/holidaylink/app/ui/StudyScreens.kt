package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Imgbb
// ==================================================================== feed

/** Daily Feed / Home Feed: website posters collection with likes + comments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(api: Api, onBack: () -> Unit) {
    var posts by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf("all") }
    val scope = rememberCoroutineScope()
    val uid = FirebaseBackend.uid.orEmpty()

    LaunchedEffect(reload) {
        loading = posts.isEmpty()
        try {
            val snap = FirebaseBackend.db.collection("posters")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).get().await()
            ids = snap.documents.map { it.id }
            @Suppress("UNCHECKED_CAST")
            posts = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
            error = ""
        } catch (e: Exception) {
            error = "Couldn't load the feed. Check your internet."
        } finally {
            loading = false
        }
    }

    fun like(postId: String, liked: Boolean) {
        scope.launch {
            runCatching {
                val ref = FirebaseBackend.db.collection("posters").document(postId)
                if (liked) ref.update("likes", com.google.firebase.firestore.FieldValue.arrayRemove(uid)).await()
                else ref.update("likes", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
                reload++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Feed") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "news" to "News", "entertainment" to "Fun", "announcement" to "Notices").forEach { (v, l) ->
                    FilterChip(selected = tab == v, onClick = { tab = v }, label = { Text(l) })
                }
            }
            when {
                loading -> LoadingState(label = "Loading feed…")
                error.isNotBlank() && posts.isEmpty() -> ErrorState(error) { reload++ }
                posts.isEmpty() -> EmptyState(icon = Icons.Default.Newspaper, title = "No posts yet", subtitle = "New announcements land here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val shown = posts.indices.filter { i ->
                        @Suppress("UNCHECKED_CAST")
                        val data = posts[i]
                        val cat = (data["category"] as? String ?: "announcement").lowercase()
                        tab == "all" || cat == tab
                    }
                    if (shown.isEmpty()) item { Text("Nothing in this tab yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(shown, key = { ids[it] }) { i ->
                        @Suppress("UNCHECKED_CAST")
                        val data = posts[i]
                        FeedCard(
                            title = (data["title"] as? String).orEmpty(),
                            body = (data["message"] as? String ?: data["content"] as? String).orEmpty(),
                            image = (data["image"] as? String ?: data["imageUrl"] as? String),
                            videoUrl = (data["videoUrl"] as? String),
                            moreDetails = (data["moreDetails"] as? String),
                            category = (data["category"] as? String ?: "announcement"),
                            likes = ((data["likes"] as? List<String>).orEmpty()),
                            comments = ((data["comments"] as? List<Map<String, String>>).orEmpty()),
                            liked = ((data["likes"] as? List<String>).orEmpty()).contains(uid),
                            onLike = { like(ids[i], ((data["likes"] as? List<String>).orEmpty()).contains(uid)) },
                            onComment = { text ->
                                scope.launch {
                                    runCatching {
                                        val me = FirebaseBackend.loadProfile(uid)
                                        val entry = mapOf(
                                            "name" to (me?.name ?: "Someone"),
                                            "userId" to uid,
                                            "text" to text.take(500),
                                            "timestamp" to java.time.Instant.now().toString()
                                        )
                                        FirebaseBackend.db.collection("posters").document(ids[i])
                                            .update("comments", com.google.firebase.firestore.FieldValue.arrayUnion(entry)).await()
                                        reload++
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(
    title: String,
    body: String,
    image: String?,
    videoUrl: String?,
    moreDetails: String?,
    category: String,
    likes: List<String>,
    comments: List<Map<String, String>>,
    liked: Boolean,
    onLike: () -> Unit,
    onComment: (String) -> Unit
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)
    ) {
        Text(category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        if (title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        if (!image.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            RemoteImage(url = image, modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)))
        }
        if (!videoUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(videoUrl))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Watch video")
            }
        }
        if (!moreDetails.isNullOrBlank()) {
            TextButton(onClick = { showMore = true }) { Text("See more") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Like",
                    tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${likes.size}", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(16.dp))
            Text(
                "${comments.size} comments",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open = !open }
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$body"),
                                "Share post"
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            comments.takeLast(5).forEach { c ->
                Text(
                    "${c["name"] ?: "Someone"}: ${c["text"].orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(500) },
                    placeholder = { Text("Comment…") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (draft.isNotBlank()) {
                        onComment(draft.trim())
                        draft = ""
                    }
                }) { Text("Send") }
            }
        }
    }
    if (showMore && !moreDetails.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text(title.ifBlank { "Details" }) },
            text = { Text(moreDetails) },
            confirmButton = { TextButton(onClick = { showMore = false }) { Text("Close") } }
        )
    }
}

// ==================================================================== quizzes

/** Quizzes: website quizzes collection with timed play + rewards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizzesScreen(api: Api, onBack: () -> Unit) {
    var quizzes by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uid = FirebaseBackend.uid.orEmpty()

    LaunchedEffect(Unit) {
        try {
            val snap = FirebaseBackend.db.collection("quizzes")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await()
            ids = snap.documents.map { it.id }
            @Suppress("UNCHECKED_CAST")
            quizzes = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
                .filter { it["active"] != false }
        } catch (e: Exception) {
            error = "Couldn't load quizzes. Check your internet."
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Quizzes") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingState(label = "Loading quizzes…")
                error.isNotBlank() && quizzes.isEmpty() -> ErrorState(error)
                quizzes.isEmpty() -> EmptyState(icon = Icons.Default.Quiz, title = "No quizzes yet", subtitle = "New quizzes appear here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(quizzes.indices.toList(), key = { ids[it] }) { i ->
                        @Suppress("UNCHECKED_CAST")
                        val q = quizzes[i]
                        val questions = (q["questions"] as? List<Map<String, Any?>>).orEmpty()
                        @Suppress("UNCHECKED_CAST")
                        val played = ((q["participants"] as? List<String>).orEmpty()).contains(uid)
                        val paid = (q["isPaid"] as? Boolean) == true
                        QuizCard(
                            title = (q["title"] as? String).orEmpty(),
                            desc = (q["description"] as? String).orEmpty(),
                            prize = (q["prize"] as? Number)?.toLong() ?: 0L,
                            count = questions.size,
                            played = played,
                            paid = paid,
                            fee = (q["entryFee"] as? Number)?.toLong() ?: 0L,
                            onStart = { playing = i }
                        )
                    }
                }
            }
        }
    }

    playing?.let { i ->
        @Suppress("UNCHECKED_CAST")
        val q = quizzes[i]
        @Suppress("UNCHECKED_CAST")
        val already = ((q["participants"] as? List<String>).orEmpty()).contains(uid)
        val prize = (q["prize"] as? Number)?.toLong() ?: 0L
        val paid = (q["isPaid"] as? Boolean) == true
        val fee = (q["entryFee"] as? Number)?.toLong() ?: 0L
        var entryOk by remember(i) { mutableStateOf(!paid) }
        var entryBusy by remember(i) { mutableStateOf(false) }
        var txId by remember(i) { mutableStateOf("") }
        var shotUri by remember(i) { mutableStateOf<android.net.Uri?>(null) }
        val context = LocalContext.current
        LaunchedEffect(i, paid) {
            if (!paid) return@LaunchedEffect
            entryOk = runCatching {
                FirebaseBackend.db.collection("quizPayments")
                    .whereEqualTo("userId", uid).whereEqualTo("quizId", ids[i])
                    .whereEqualTo("status", "approved").limit(1).get().await().isEmpty.not()
            }.getOrDefault(false)
        }
        if (paid && !entryOk) {
            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri -> shotUri = uri }
            AlertDialog(
                onDismissRequest = { playing = null },
                title = { Text("Entry fee UGX $fee") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This quiz needs an approved payment. Attach the receipt screenshot and your transaction ID.")
                        OutlinedTextField(value = txId, onValueChange = { txId = it.take(60) }, label = { Text("Transaction ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (shotUri == null) "Attach receipt screenshot" else "Screenshot attached ✓")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = txId.isNotBlank() && shotUri != null && !entryBusy,
                        onClick = {
                            entryBusy = true
                            scope.launch {
                                runCatching {
                                    val bytes = MediaPicker.imageJpegBytes(context, shotUri!!)
                                        ?: throw ApiException("That screenshot couldn't be read.")
                                    val url = Imgbb.uploadJpeg(bytes)
                                    FirebaseBackend.db.collection("quizPayments").add(
                                        mapOf(
                                            "userId" to uid, "quizId" to ids[i],
                                            "entryFee" to fee, "screenshotUrl" to url,
                                            "transactionId" to txId.trim(), "mode" to "manual",
                                            "status" to "pending", "timestamp" to Timestamp.now()
                                        )
                                    ).await()
                                }.onSuccess {
                                    snackbar.showSnackbar("Payment sent. Play opens once approved.")
                                    playing = null
                                }.onFailure {
                                    snackbar.showSnackbar((it as? ApiException)?.message ?: "Couldn't send payment.")
                                }
                                entryBusy = false
                            }
                        }
                    ) { Text("Submit") }
                },
                dismissButton = { TextButton(onClick = { playing = null }) { Text("Cancel") } }
            )
            return@let
        }
        QuizPlayDialog(
            title = (q["title"] as? String).orEmpty(),
            timeLimit = ((q["timeLimit"] as? Number)?.toLong() ?: 10L).coerceIn(10, 600),
            questions = ((q["questions"] as? List<Map<String, Any?>>).orEmpty()),
            onDismiss = { playing = null },
            onFinish = { correct, total ->
                playing = null
                scope.launch {
                    runCatching { awardQuiz(ids[i], correct, total, prize, already) }
                        .onSuccess { rewarded ->
                            snackbar.showSnackbar(
                                if (rewarded) "You scored $correct/$total — rewarded!"
                                else "You scored $correct/$total."
                            )
                        }
                        .onFailure { snackbar.showSnackbar("Couldn't save that score.") }
                }
            }
        )
    }
}

/**
 * Website endQuiz parity: first play only. Score >= 50% with a prize pays
 * balance += prize, points += 10, quizzesWon += 1 + Quiz Reward transaction
 * + notification. Replay earns nothing.
 */
private suspend fun awardQuiz(quizId: String, correct: Int, total: Int, prize: Long, alreadyPlayed: Boolean): Boolean {
    val uid = FirebaseBackend.uid.orEmpty()
    val db = FirebaseBackend.db
    if (alreadyPlayed) return false
    val passed = total > 0 && correct * 2 >= total
    db.collection("quizzes").document(quizId)
        .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
    if (!passed) return false
    val userRef = db.collection("users").document(uid)
    val snap = userRef.get().await()
    userRef.update(
        mapOf(
            "points" to ((snap.getLong("points") ?: 0) + 10),
            "quizzesWon" to ((snap.getLong("quizzesWon") ?: 0) + 1),
            "balance" to ((snap.getLong("balance") ?: 0) + prize)
        )
    ).await()
    if (prize > 0) {
        db.collection("transactions").add(
            mapOf(
                "userId" to uid, "type" to "Quiz Reward", "amount" to prize,
                "quizId" to quizId, "timestamp" to Timestamp.now()
            )
        ).await()
    }
    db.collection("notifications").add(
        mapOf(
            "userId" to uid, "title" to "Quiz Reward",
            "message" to "You scored $correct/$total: +10 points" + if (prize > 0) " and UGX $prize." else ".",
            "read" to false, "timestamp" to Timestamp.now()
        )
    ).await()
    return true
}

@Composable
private fun QuizCard(
    title: String,
    desc: String,
    prize: Long,
    count: Int,
    played: Boolean,
    paid: Boolean,
    fee: Long,
    onStart: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "Quiz" }, fontWeight = FontWeight.Bold)
            if (desc.isNotBlank()) Text(desc.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("$count questions")
                    if (prize > 0) append(" • Prize UGX $prize")
                    if (paid && fee > 0) append(" • Entry UGX $fee")
                    if (played) append(" • Played")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onStart, enabled = count > 0) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(if (played) "Replay" else "Start")
        }
    }
}

@Composable
private fun QuizPlayDialog(
    title: String,
    timeLimit: Long,
    questions: List<Map<String, Any?>>,
    onDismiss: () -> Unit,
    onFinish: (Int, Int) -> Unit
) {
    var index by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var correct by remember { mutableStateOf(0) }
    var left by remember { mutableStateOf(timeLimit) }
    LaunchedEffect(index) {
        picked = null
        while (left > 0) {
            delay(1000)
            left--
        }
        onFinish(correct, questions.size)
    }
    val q = questions.getOrNull(index) ?: return
    @Suppress("UNCHECKED_CAST")
    val options = (q["options"] as? List<String>).orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.ifBlank { "Quiz" }) },
        text = {
            Column {
                LinearProgressIndicator(progress = { left.toFloat() / timeLimit }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text("Q${index + 1}/${questions.size} • ${left}s left", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Text((q["question"] as? String).orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                options.forEachIndexed { oi, opt ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(
                                if (picked == oi) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable(enabled = picked == null) {
                                picked = oi
                                val answer = (q["correct"] as? Number)?.toInt() ?: -1
                                if (oi == answer) correct++
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = picked == oi, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(opt)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null,
                onClick = {
                    if (index + 1 >= questions.size) onFinish(correct, questions.size)
                    else index++
                }
            ) { Text(if (index + 1 >= questions.size) "Finish" else "Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Quit") } }
    )
}

// ==================================================================== papers

/** Past papers + guides: website papers collection, opened in a viewer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapersScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    var papers by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("papers") }
    var cat by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    LaunchedEffect(tab) {
        loading = papers.isEmpty()
        try {
            val snap = FirebaseBackend.db.collection(if (tab == "papers") "papers" else "guides")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).get().await()
            ids = snap.documents.map { it.id }
            @Suppress("UNCHECKED_CAST")
            papers = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
            error = ""
        } catch (e: Exception) {
            error = "Couldn't load. Check your internet."
        } finally {
            loading = false
        }
    }

    fun shareDoc(id: String, title: String) {
        runCatching {
            val link = "https://holidaylink.org/${if (tab == "papers") "papers" else "papers"}.html?${if (tab == "papers") "paper" else "guide"}=$id"
            context.startActivity(
                android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$link"),
                    "Share"
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val openViewer = viewing
    if (openViewer != null) {
        PaperViewerScreen(
            url = openViewer.first,
            title = openViewer.second,
            fileType = openViewer.third,
            onBack = { viewing = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == "papers") "Past Papers" else "Guides") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "papers", onClick = { tab = "papers"; papers = emptyList() }, label = { Text("Papers") })
                FilterChip(selected = tab == "guides", onClick = { tab = "guides"; papers = emptyList() }, label = { Text("Guides") })
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                placeholder = { Text(if (tab == "papers") "Search past papers" else "Search guides") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (tab == "papers") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "All", "uce" to "UCE", "uace" to "UACE", "mock" to "Mock").forEach { (v, l) ->
                        FilterChip(selected = cat == v, onClick = { cat = v }, label = { Text(l) })
                    }
                }
            }
            when {
                loading -> LoadingState(label = "Loading…")
                error.isNotBlank() && papers.isEmpty() -> ErrorState(error)
                papers.isEmpty() -> EmptyState(icon = Icons.Default.Description, title = "Nothing here yet", subtitle = "New uploads appear here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val q = search.trim().lowercase()
                    val shown = papers.indices.filter { i ->
                        val p = papers[i]
                        val catOk = tab != "papers" || cat == "all" ||
                            ((p["category"] as? String)?.lowercase() == cat)
                        val searchOk = q.isBlank() || listOfNotNull(
                            p["title"] as? String, p["subject"] as? String,
                            p["class"] as? String, p["board"] as? String,
                            p["category"] as? String, (p["year"] as? Number)?.toString()
                        ).any { it.lowercase().contains(q) }
                        catOk && searchOk
                    }
                    if (shown.isEmpty()) item { Text("Nothing in this category yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(shown, key = { ids[it] }) { i ->
                        val p = papers[i]
                        val title = (p["title"] as? String).orEmpty().ifBlank { "Untitled" }
                        val meta = listOfNotNull(
                            p["subject"] as? String, p["class"] as? String,
                            (p["year"] as? Number)?.toString(), p["board"] as? String,
                            (p["category"] as? String)?.uppercase()
                        ).joinToString(" • ")
                        val url = (p["fileUrl"] as? String ?: p["url"] as? String ?: p["driveUrl"] as? String).orEmpty()
                        val fileType = (p["fileType"] as? String).orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (fileType.isNotBlank()) Text(fileType.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewing = Triple(url, title, fileType) },
                                        enabled = url.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text("View") }
                                    OutlinedButton(
                                        onClick = {
                                            if (url.isNotBlank()) {
                                                val ok = org.holidaylink.app.media.MediaDownload.save(context, url, title)
                                                if (!ok) android.widget.Toast.makeText(context, "Couldn't start the download.", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = url.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text("Download") }
                                    OutlinedButton(
                                        onClick = { shareDoc(ids[i], title) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) { Text("Share") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================================================================== events

/** Events: website events collection with join + create. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(api: Api, onBack: () -> Unit) {
    var events by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var bookingFor by remember { mutableStateOf<Int?>(null) }
    var bookQty by remember { mutableStateOf("1") }
    var bookPhone by remember { mutableStateOf("") }
    var bookShot by remember { mutableStateOf<android.net.Uri?>(null) }
    var booking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uid = FirebaseBackend.uid.orEmpty()

    val bookPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> bookShot = uri }

    suspend fun submitBooking(i: Int) {
        booking = true
        try {
            val e = events[i]
            val price = ((e["price"] as? Number)?.toLong() ?: 0L)
            val qty = bookQty.toIntOrNull()?.coerceIn(1, 20) ?: 1
            val bytes = bookShot?.let { MediaPicker.imageJpegBytes(context, it) }
                ?: throw ApiException("Attach the payment screenshot.")
            val url = Imgbb.uploadJpeg(bytes)
            val me = FirebaseBackend.loadProfile(uid)
            val reference = java.util.UUID.randomUUID().toString()
            val ticketId = "TKT-" + reference.take(8).uppercase()
            FirebaseBackend.db.collection("bookings").document(reference).set(
                mapOf(
                    "type" to "event",
                    "eventId" to ids[i],
                    "eventTitle" to ((e["title"] as? String).orEmpty()),
                    "userId" to uid,
                    "userName" to (me?.name ?: ""),
                    "phone" to bookPhone.ifBlank { me?.phone ?: "" },
                    "quantity" to qty,
                    "totalAmount" to (price * qty),
                    "screenshotUrl" to url,
                    "ticketId" to ticketId,
                    "paymentMode" to "manual",
                    "status" to "pending",
                    "timestamp" to Timestamp.now()
                )
            ).await()
            FirebaseBackend.db.collection("events").document(ids[i])
                .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
            bookingFor = null
            bookQty = "1"; bookShot = null
            reload++
            snackbar.showSnackbar("Booked! Ticket $ticketId is pending approval.")
        } catch (ex: Exception) {
            snackbar.showSnackbar((ex as? ApiException)?.message ?: "Couldn't book that event.")
        } finally {
            booking = false
        }
    }

    suspend fun load() {
        loading = events.isEmpty()
        try {
            val snap = FirebaseBackend.db.collection("events")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).get().await()
            ids = snap.documents.map { it.id }
            @Suppress("UNCHECKED_CAST")
            events = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
                .filter { it["active"] != false }
            error = ""
        } catch (e: Exception) {
            error = "Couldn't load events. Check your internet."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(reload) { load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Events") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Button(onClick = { creating = !creating }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (creating) "Close" else "Create event")
            }
            if (creating) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it.take(1000) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = date, onValueChange = { date = it.take(60) }, label = { Text("Date") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = location, onValueChange = { location = it.take(120) }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val me = FirebaseBackend.loadProfile(uid)
                                    FirebaseBackend.db.collection("events").add(
                                        mapOf(
                                            "title" to title.trim(), "description" to desc.trim(),
                                            "date" to date.trim(), "location" to location.trim(),
                                            "category" to "general", "type" to "free", "price" to 0,
                                            "active" to true, "creatorId" to uid,
                                            "creatorName" to (me?.name ?: ""),
                                            "participants" to emptyList<String>(),
                                            "timestamp" to Timestamp.now()
                                        )
                                    ).await()
                                }.onSuccess {
                                    title = ""; desc = ""; date = ""; location = ""; creating = false
                                    reload++
                                    snackbar.showSnackbar("Event created.")
                                }.onFailure { snackbar.showSnackbar("Couldn't create that event.") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Publish event") }
                }
            }
            when {
                loading -> LoadingState(label = "Loading events…")
                error.isNotBlank() && events.isEmpty() -> ErrorState(error) { reload++ }
                events.isEmpty() -> EmptyState(icon = Icons.Default.Event, title = "No events", subtitle = "Create the first one above.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(events.indices.toList(), key = { ids[it] }) { i ->
                        @Suppress("UNCHECKED_CAST")
                        val e = events[i]
                        @Suppress("UNCHECKED_CAST")
                        val parts = (e["participants"] as? List<String>).orEmpty()
                        val joined = parts.contains(uid)
                        val paid = (e["type"] as? String) == "paid"
                        val price = ((e["price"] as? Number)?.toLong() ?: 0L)
                        val img = (e["image"] as? String)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)
                        ) {
                            if (!img.isNullOrBlank()) {
                                RemoteImage(url = img, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
                                Spacer(Modifier.height(8.dp))
                            }
                            Text((e["title"] as? String).orEmpty().ifBlank { "Event" }, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text((e["description"] as? String).orEmpty().take(200), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(
                                    e["date"] as? String,
                                    e["time"] as? String,
                                    e["location"] as? String,
                                    e["category"] as? String
                                ).joinToString(" • ") +
                                    " • ${parts.size} going" +
                                    if (paid && price > 0) " • UGX $price" else " • Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !joined,
                                onClick = {
                                    if (paid && price > 0) bookingFor = i
                                    else {
                                        scope.launch {
                                            runCatching {
                                                FirebaseBackend.db.collection("events").document(ids[i])
                                                    .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
                                                // Notify the creator, like the dashboard does.
                                                val creator = e["creatorId"] as? String
                                                if (!creator.isNullOrBlank() && creator != uid) {
                                                    val me = FirebaseBackend.loadProfile(uid)
                                                    FirebaseBackend.db.collection("notifications").add(
                                                        mapOf(
                                                            "userId" to creator, "title" to "New event join",
                                                            "message" to "${me?.name ?: "Someone"} joined ${e["title"]}.",
                                                            "read" to false, "timestamp" to Timestamp.now()
                                                        )
                                                    ).await()
                                                }
                                                reload++
                                            }.onFailure { snackbar.showSnackbar("Couldn't join.") }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (joined) "Joined" else if (paid && price > 0) "Book • UGX $price" else "Join free") }
                        }
                    }
                    item(key = "tickets-hdr") {
                        Text("My tickets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    item(key = "tickets") { MyTickets(uid = uid) }
                }
            }
        }
    }

    bookingFor?.let { i ->
        val e = events.getOrNull(i) ?: run { bookingFor = null; return@let }
        val price = ((e["price"] as? Number)?.toLong() ?: 0L)
        AlertDialog(
            onDismissRequest = { if (!booking) bookingFor = null },
            title = { Text("Book ${(e["title"] as? String).orEmpty().ifBlank { "event" }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UGX $price per ticket", color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = bookQty, onValueChange = { bookQty = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bookPhone, onValueChange = { bookPhone = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Payment phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { bookPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (bookShot == null) "Attach payment screenshot" else "Screenshot attached ✓")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !booking && bookShot != null, onClick = { scope.launch { submitBooking(i) } }) {
                    Text(if (booking) "Booking…" else "Book")
                }
            },
            dismissButton = { TextButton(enabled = !booking, onClick = { bookingFor = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MyTickets(uid: String) {
    var tickets by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uid) {
        runCatching {
            val snap = FirebaseBackend.db.collection("bookings")
                .whereEqualTo("userId", uid).whereEqualTo("type", "event")
                .limit(50).get().await()
            val docs = snap.documents
                .sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
            ids = docs.map { it.id }
            @Suppress("UNCHECKED_CAST")
            tickets = docs.map { it.data.orEmpty() as Map<String, Any?> }
        }
    }
    if (tickets.isEmpty()) {
        Text("No tickets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tickets.forEachIndexed { index, t ->
            val status = (t["status"] as? String) ?: "pending"
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text((t["ticketId"] as? String ?: "Ticket"), fontWeight = FontWeight.Bold)
                    Text(
                        "${(t["eventTitle"] as? String).orEmpty()} • ${status.replaceFirstChar { c -> c.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (status == "pending") {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                FirebaseBackend.db.collection("bookings").document(ids[index]).delete().await()
                                tickets = tickets.filterIndexed { j, _ -> j != index }
                                ids = ids.filterIndexed { j, _ -> j != index }
                            }.onFailure { snackbar.showSnackbar("Couldn't delete that ticket.") }
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ==================================================================== wallet

/** Wallet: balance, withdraw requests, transaction history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(api: Api, onBack: () -> Unit) {
    var balance by remember { mutableStateOf(0L) }
    var withdrawals by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("MTN") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uid = FirebaseBackend.uid.orEmpty()

    suspend fun load() {
        loading = balance == 0L && withdrawals.isEmpty() && transactions.isEmpty()
        try {
            val user = FirebaseBackend.db.collection("users").document(uid).get().await()
            balance = user.getLong("balance") ?: 0L
            @Suppress("UNCHECKED_CAST")
            withdrawals = FirebaseBackend.db.collection("withdrawals").whereEqualTo("userId", uid)
                .limit(50).get().await().documents
                .sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
                .map { it.data.orEmpty() as Map<String, Any?> }
            @Suppress("UNCHECKED_CAST")
            transactions = FirebaseBackend.db.collection("transactions").whereEqualTo("userId", uid)
                .limit(50).get().await().documents
                .sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
                .map { it.data.orEmpty() as Map<String, Any?> }
        } catch (e: Exception) {
            snackbar.showSnackbar("Couldn't load wallet. Check your internet.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Wallet") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("UGX $balance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Withdraw", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Amount (UGX, min 5,000)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) }, label = { Text("Mobile money number (07…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MTN", "Airtel").forEach { p ->
                    FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text(p) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = (amount.toLongOrNull() ?: 0) >= 5000 && phone.length == 10 && phone.startsWith("07"),
                onClick = {
                    scope.launch {
                        runCatching {
                            val amt = amount.toLongOrNull() ?: 0
                            if (amt > balance) throw ApiException("Insufficient balance.")
                            val me = FirebaseBackend.loadProfile(uid)
                            FirebaseBackend.db.collection("withdrawals").add(
                                mapOf(
                                    "userId" to uid, "userName" to (me?.name ?: ""),
                                    "amount" to amt,
                                    "phone" to phone, "provider" to provider,
                                    "status" to "pending", "timestamp" to Timestamp.now()
                                )
                            ).await()
                            FirebaseBackend.db.collection("transactions").add(
                                mapOf(
                                    "userId" to uid, "type" to "Withdrawal Request",
                                    "amount" to -amt, "description" to "$provider $phone",
                                    "timestamp" to Timestamp.now()
                                )
                            ).await()
                        }.onSuccess {
                            amount = ""; phone = ""
                            snackbar.showSnackbar("Withdrawal requested. Admin approves it.")
                            load()
                        }.onFailure {
                            snackbar.showSnackbar((it as? ApiException)?.message ?: "Couldn't send that request.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Request withdrawal") }
            Spacer(Modifier.height(20.dp))
            Text("Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (loading) CircularProgressIndicator(Modifier.size(22.dp)) else if (transactions.isEmpty()) {
                Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                transactions.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text((t["type"] as? String) ?: "Transaction", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (t["timestamp"] as? Timestamp)?.toDate()?.toString().orEmpty().take(16),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("UGX ${t["amount"] ?: 0}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================================================================== invite

/** Invite friends: referral link, share, invite list with UGX 1,000 per activation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(api: Api, onBack: () -> Unit) {
    val context = LocalContext.current
    var invites by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val uid = FirebaseBackend.uid.orEmpty()
    val link = "https://holidaylink.org/signup.html?ref=$uid"

    LaunchedEffect(uid) {
        try {
            val snap = FirebaseBackend.db.collection("invites")
                .whereEqualTo("inviterId", uid).limit(200).get().await()
            @Suppress("UNCHECKED_CAST")
            invites = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
                .sortedByDescending { (it["timestamp"] as? Timestamp)?.seconds ?: 0 }
            error = ""
        } catch (e: Exception) {
            error = "Couldn't load invites. Check your internet."
        } finally {
            loading = false
        }
    }

    fun share(text: String, pkg: String? = null) {
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, text)
            if (pkg != null) intent.setPackage(pkg)
            context.startActivity(
                android.content.Intent.createChooser(intent, "Invite friends")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val activated = invites.count { (it["status"] as? String) == "activated" }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Invite Friends") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Share HolidayLink", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Friends join with your link. You earn UGX 1,000 for each friend that activates.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(link, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { share("Join me on HolidayLink: $link") }) { Text("Share") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { share("Join me on HolidayLink: $link", "com.whatsapp") },
                    modifier = Modifier.weight(1f)
                ) { Text("WhatsApp") }
                OutlinedButton(
                    onClick = { share("Join me on HolidayLink: $link", "com.facebook.katana") },
                    modifier = Modifier.weight(1f)
                ) { Text("Facebook") }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${invites.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Invited", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$activated", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Activated", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("UGX ${activated * 1000}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Earned", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                loading -> LoadingState(label = "Loading invites…")
                error.isNotBlank() && invites.isEmpty() -> ErrorState(error)
                invites.isEmpty() -> Text("No invites yet. Share your link above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> invites.forEach { inv ->
                    val active = (inv["status"] as? String) == "activated"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ((inv["invitedName"] as? String)?.takeIf { it.isNotBlank() }
                                    ?: (inv["invitedEmail"] as? String).orEmpty()).ifBlank { "Invitee" },
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                (inv["status"] as? String ?: "pending").replaceFirstChar { c -> c.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (active) Text("+UGX 1,000", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================================================================== rankings

/** Rankings: top students by quiz points. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(api: Api, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val snap = FirebaseBackend.db.collection("users")
                .orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await()
            @Suppress("UNCHECKED_CAST")
            rows = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
                .filter { ((it["points"] as? Number)?.toLong() ?: 0L) > 0 }
        } catch (e: Exception) {
            error = "Couldn't load rankings. Check your internet."
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rankings") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> LoadingState(label = "Loading rankings…")
                error.isNotBlank() && rows.isEmpty() -> ErrorState(error)
                rows.isEmpty() -> EmptyState(icon = Icons.Default.Leaderboard, title = "No rankings yet", subtitle = "Play quizzes to earn points.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows.indices.toList()) { i ->
                        val r = rows[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${i + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text((r["name"] as? String).orEmpty().ifBlank { "Student" }, fontWeight = FontWeight.Bold)
                                Text("@${(r["username"] as? String).orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${(r["points"] as? Number)?.toLong() ?: 0} pts", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
