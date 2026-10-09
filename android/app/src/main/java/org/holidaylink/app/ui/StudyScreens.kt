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
import androidx.compose.material.icons.filled.Quiz
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
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.FirebaseBackend

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
    category: String,
    likes: List<String>,
    comments: List<Map<String, String>>,
    liked: Boolean,
    onLike: () -> Unit,
    onComment: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
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
                        QuizCard(
                            title = (q["title"] as? String).orEmpty(),
                            desc = (q["description"] as? String).orEmpty(),
                            prize = (q["prize"] as? Number)?.toLong() ?: 0L,
                            count = questions.size,
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
        QuizPlayDialog(
            title = (q["title"] as? String).orEmpty(),
            timeLimit = ((q["timeLimit"] as? Number)?.toLong() ?: 60L).coerceIn(10, 600),
            questions = ((q["questions"] as? List<Map<String, Any?>>).orEmpty()),
            onDismiss = { playing = null },
            onFinish = { correct, total ->
                playing = null
                scope.launch {
                    runCatching { awardQuiz(ids[i], correct, total) }
                        .onSuccess { snackbar.showSnackbar("You scored $correct/$total.") }
                        .onFailure { snackbar.showSnackbar("Score saved locally only.") }
                }
            }
        )
    }
}

private suspend fun awardQuiz(quizId: String, correct: Int, total: Int) {
    val uid = FirebaseBackend.uid.orEmpty()
    val db = FirebaseBackend.db
    val allCorrect = total > 0 && correct == total
    db.collection("quizzes").document(quizId)
        .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
    db.collection("quizPayments").add(
        mapOf("userId" to uid, "quizId" to quizId, "score" to "$correct/$total", "timestamp" to Timestamp.now())
    ).await()
    val points = (correct * 10).toLong()
    val userRef = db.collection("users").document(uid)
    val snap = userRef.get().await()
    userRef.update(
        mapOf(
            "points" to ((snap.getLong("points") ?: 0) + points),
            "quizzesWon" to ((snap.getLong("quizzesWon") ?: 0) + if (allCorrect) 1 else 0)
        )
    ).await()
    if (allCorrect) {
        val prize = 0L
        db.collection("transactions").add(
            mapOf("userId" to uid, "type" to "Quiz Reward", "amount" to prize, "timestamp" to Timestamp.now())
        ).await()
        db.collection("notifications").add(
            mapOf(
                "userId" to uid, "title" to "Quiz Reward",
                "message" to "Perfect score! +$points points.", "read" to false,
                "timestamp" to Timestamp.now()
            )
        ).await()
    }
}

@Composable
private fun QuizCard(title: String, desc: String, prize: Long, count: Int, onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "Quiz" }, fontWeight = FontWeight.Bold)
            if (desc.isNotBlank()) Text(desc.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("$count questions" + if (prize > 0) " • Prize UGX $prize" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onStart, enabled = count > 0) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Start") }
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
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("papers") }

    LaunchedEffect(tab) {
        loading = papers.isEmpty()
        try {
            val snap = FirebaseBackend.db.collection(if (tab == "papers") "papers" else "guides")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100).get().await()
            @Suppress("UNCHECKED_CAST")
            papers = snap.documents.map { it.data.orEmpty() as Map<String, Any?> }
            error = ""
        } catch (e: Exception) {
            error = "Couldn't load. Check your internet."
        } finally {
            loading = false
        }
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
            when {
                loading -> LoadingState(label = "Loading…")
                error.isNotBlank() && papers.isEmpty() -> ErrorState(error)
                papers.isEmpty() -> EmptyState(icon = Icons.Default.Description, title = "Nothing here yet", subtitle = "New uploads appear here.")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(papers.indices.toList()) { i ->
                        val p = papers[i]
                        val title = (p["title"] as? String).orEmpty().ifBlank { "Untitled" }
                        val meta = listOfNotNull(
                            p["subject"] as? String, p["class"] as? String,
                            (p["year"] as? Number)?.toString(), p["board"] as? String
                        ).joinToString(" • ")
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    val url = (p["url"] as? String ?: p["driveUrl"] as? String).orEmpty()
                                    if (url.isNotBlank()) {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uid = FirebaseBackend.uid.orEmpty()

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
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)
                        ) {
                            Text((e["title"] as? String).orEmpty().ifBlank { "Event" }, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text((e["description"] as? String).orEmpty().take(200), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(e["date"] as? String, e["location"] as? String).joinToString(" • ") +
                                    " • ${parts.size} going",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                enabled = !joined,
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            FirebaseBackend.db.collection("events").document(ids[i])
                                                .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(uid)).await()
                                            reload++
                                        }.onFailure { snackbar.showSnackbar("Couldn't join.") }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (joined) "Joined" else "Join") }
                        }
                    }
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
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await().documents.map { it.data.orEmpty() as Map<String, Any?> }
            @Suppress("UNCHECKED_CAST")
            transactions = FirebaseBackend.db.collection("transactions").whereEqualTo("userId", uid)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50).get().await().documents.map { it.data.orEmpty() as Map<String, Any?> }
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
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() }.take(9) }, label = { Text("Amount (UGX)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Mobile money number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MTN", "Airtel").forEach { p ->
                    FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text(p) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = (amount.toLongOrNull() ?: 0) > 0 && phone.length >= 9,
                onClick = {
                    scope.launch {
                        runCatching {
                            val me = FirebaseBackend.loadProfile(uid)
                            FirebaseBackend.db.collection("withdrawals").add(
                                mapOf(
                                    "userId" to uid, "userName" to (me?.name ?: ""),
                                    "amount" to (amount.toLongOrNull() ?: 0),
                                    "phone" to phone, "provider" to provider,
                                    "status" to "pending", "timestamp" to Timestamp.now()
                                )
                            ).await()
                        }.onSuccess {
                            amount = ""; phone = ""
                            snackbar.showSnackbar("Withdrawal requested. Admin approves it.")
                            load()
                        }.onFailure { snackbar.showSnackbar("Couldn't send that request.") }
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
