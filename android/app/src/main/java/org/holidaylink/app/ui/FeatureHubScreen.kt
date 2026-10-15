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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.Imgbb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * HolidayLink's non-messaging feature center. Admin tools intentionally do not appear here.
 * Public website functionality is opened inside the app through WebFeatureScreen while the
 * native Firebase migration is completed feature-by-feature.
 */
data class HolidayFeature(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: List<Color>,
    val route: String
)

private data class HolidaySection(val title: String, val features: List<HolidayFeature>)

// One entry per destination. (Older builds listed "Daily Feed" and "Homework" that both
// silently opened Feed/AI again, so the hub had two buttons for one page.)
private val sections = listOf(
    HolidaySection(
        "Stay updated",
        listOf(
            HolidayFeature("Home Feed", "Announcements and the latest HolidayLink updates", Icons.Default.Newspaper, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:feed"),
            HolidayFeature("Events", "Discover and join HolidayLink events", Icons.Default.CalendarMonth, listOf(Color(0xFF10B981), Color(0xFF06B6D4)), "native:events")
        )
    ),
    HolidaySection(
        "Study",
        listOf(
            HolidayFeature("Quizzes", "Test yourself and track your progress", Icons.Default.Quiz, listOf(Color(0xFF8B5CF6), Color(0xFF2563EB)), "native:quizzes"),
            HolidayFeature("Past Papers", "Find revision papers by subject and year", Icons.Default.Description, listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), "native:papers"),
            HolidayFeature("AI Homework Help", "Ask HolidayLink AI for study help", Icons.Default.SmartToy, listOf(Color(0xFF06B6D4), Color(0xFF7C3AED)), "native:ai"),
            HolidayFeature("Rankings", "See quiz points and student rankings", Icons.Default.Leaderboard, listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), "native:rankings")
        )
    ),
    HolidaySection(
        "Messaging",
        listOf(
            HolidayFeature("Messages", "Open the full HolidayLink messaging experience", Icons.Default.Chat, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:chats"),
            HolidayFeature("Calls", "Open your audio and video call history", Icons.Default.Chat, listOf(Color(0xFF06B6D4), Color(0xFF2563EB)), "native:calls"),
            HolidayFeature("Starred messages", "Keep and revisit messages you starred", Icons.Default.Star, listOf(Color(0xFFF59E0B), Color(0xFFFACC15)), "native:starred"),
            HolidayFeature("Status", "Updates that vanish after 24 hours", Icons.Default.Schedule, listOf(Color(0xFF10B981), Color(0xFF06B6D4)), "native:status")
        )
    ),
    HolidaySection(
        "Store & more",
        listOf(
            HolidayFeature("Store", "HolidayLink plans, packs and available items", Icons.Default.ShoppingBag, listOf(Color(0xFF22C55E), Color(0xFF06B6D4)), "native:store"),
            HolidayFeature("Wallet", "Balance, withdrawals and transactions", Icons.Default.AccountBalanceWallet, listOf(Color(0xFF10B981), Color(0xFF22C55E)), "native:wallet"),
            HolidayFeature("Invite Friends", "Share HolidayLink with your friends", Icons.Default.PersonAdd, listOf(Color(0xFF2563EB), Color(0xFF7C3AED)), "native:invite")
        )
    )
)

@Composable
fun FeatureHubScreen(
    onBack: () -> Unit,
    onOpen: (HolidayFeature) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Menu", subtitle = "Everything HolidayLink can do", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hero") { GlassHeroCard() }
            sections.forEach { section ->
                item(key = "hdr-${section.title}") {
                    Text(
                        section.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 1.dp)
                    )
                }
                items(section.features, key = { it.route + it.title }) { feature ->
                    FeatureCard(feature) { onOpen(feature) }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: HolidayFeature, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(feature.accent)),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(feature.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun StoreScreen(api: Api?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var products by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    var orders by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var buying by remember { mutableStateOf<Int?>(null) }
    var qty by remember { mutableStateOf("1") }
    var buyerName by remember { mutableStateOf("") }
    var buyerPhone by remember { mutableStateOf("") }
    var fulfilment by remember { mutableStateOf("pickup") }
    var shotUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var placing by remember { mutableStateOf(false) }
    val uid = FirebaseBackend.uid.orEmpty()

    DisposableEffect(Unit) {
        val regProducts = FirebaseBackend.db.collection("products")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100).addSnapshotListener { snap, e ->
                if (e != null) {
                    error = "Couldn't load store. Check your internet."
                } else {
                    @Suppress("UNCHECKED_CAST")
                    products = snap?.documents?.map { it.data.orEmpty() as Map<String, Any?> }.orEmpty()
                    ids = snap?.documents?.map { it.id }.orEmpty()
                    error = ""
                }
                loading = false
            }
        val regOrders = FirebaseBackend.db.collection("storeOrders")
            .whereEqualTo("userId", uid)
            .limit(50).addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                orders = snap?.documents
                    ?.sortedByDescending { it.safeTimestamp("timestamp")?.seconds ?: 0L }
                    ?.map { it.data.orEmpty() as Map<String, Any?> }
                    .orEmpty()
            }
        onDispose { regProducts.remove(); regOrders.remove() }
    }

    val shotPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> shotUri = uri }

    suspend fun placeOrder(i: Int) {
        placing = true
        try {
            val p = products[i]
            val unit = ((p["price"] as? Number)?.toLong()
                ?: (p["priceMinor"] as? Number)?.toLong()?.div(100) ?: 0L)
            val count = qty.toIntOrNull()?.coerceIn(1, 99) ?: 1
            var screenshotURL: String? = null
            shotUri?.let { uri ->
                val bytes = MediaPicker.imageJpegBytes(context, uri)
                    ?: throw ApiException("That screenshot couldn't be read.")
                screenshotURL = Imgbb.uploadJpeg(bytes)
            }
            val me = FirebaseBackend.loadProfile(uid)
            val reference = "HL-${System.currentTimeMillis()}"
            FirebaseBackend.db.collection("storeOrders").document(reference).set(
                mapOf(
                    "userId" to uid,
                    "userName" to buyerName.ifBlank { me?.name ?: "" },
                    "phone" to buyerPhone.ifBlank { me?.phone ?: "" },
                    "items" to listOf(
                        mapOf(
                            "productId" to ids[i],
                            "name" to ((p["title"] as? String ?: p["name"] as? String).orEmpty()),
                            "price" to unit,
                            "qty" to count
                        )
                    ),
                    "totalAmount" to (unit * count),
                    "fulfilment" to fulfilment,
                    "payment" to mapOf(
                        "amount" to (unit * count),
                        "currency" to ((p["currency"] as? String) ?: "UGX"),
                        "screenshotURL" to screenshotURL,
                        "status" to if (screenshotURL != null) "submitted" else "pending",
                        "timestamp" to Timestamp.now()
                    ),
                    "status" to "pending",
                    "timestamp" to Timestamp.now()
                )
            ).await()
            buying = null
            qty = "1"; shotUri = null
            snackbar.showSnackbar("Order $reference placed.")
        } catch (e: Exception) {
            snackbar.showSnackbar((e as? ApiException)?.message ?: "Couldn't place that order.")
        } finally {
            placing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Store", subtitle = "Plans • Packs • Updates live", onBack = onBack)
        when {
            loading -> LoadingState(label = "Loading store…")
            error.isNotBlank() && products.isEmpty() -> ErrorState(error)
            products.isEmpty() -> EmptyState(icon = Icons.Default.ShoppingBag, title = "Store is empty", subtitle = "New items from admin appear here automatically.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(products.indices.toList(), key = { ids[it] }) { i ->
                    val p = products[i]
                    val title = ((p["title"] as? String ?: p["name"] as? String).orEmpty()).ifBlank { "Item" }
                    val price = ((p["price"] as? Number)?.toLong()
                        ?: (p["priceMinor"] as? Number)?.toLong()?.div(100) ?: 0L)
                    val img = (p["image"] as? String ?: p["imageUrl"] as? String)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)
                    ) {
                        if (!img.isNullOrBlank()) {
                            RemoteImage(
                                url = img,
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(
                            ((p["description"] as? String).orEmpty()).take(140),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "UGX $price",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { buying = i }) { Text("Buy") }
                        }
                    }
                }
                if (orders.isNotEmpty()) {
                    item(key = "orders-hdr") {
                        Text(
                            "My orders (${orders.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(orders.indices.toList(), key = { "order-$it" }) { i ->
                        val o = orders[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("UGX ${o["totalAmount"] ?: 0} • ${(o["status"] as? String) ?: "pending"}")
                                Text(
                                    (o["timestamp"] as? Timestamp)?.toDate()?.toString().orEmpty().take(16),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    buying?.let { i ->
        AlertDialog(
            onDismissRequest = { if (!placing) buying = null },
            title = { Text("Buy ${((products[i]["title"] as? String ?: products[i]["name"] as? String).orEmpty()).ifBlank { "item" }}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Quantity") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = buyerName, onValueChange = { buyerName = it.take(80) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = buyerPhone, onValueChange = { buyerPhone = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Payment phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("pickup", "delivery").forEach { f ->
                            FilterChip(selected = fulfilment == f, onClick = { fulfilment = f }, label = { Text(f.replaceFirstChar { c -> c.uppercase() }) })
                        }
                    }
                    OutlinedButton(onClick = { shotPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (shotUri == null) "Payment screenshot (optional)" else "Screenshot attached ✓")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !placing, onClick = { scope.launch { placeOrder(i) } }) {
                    Text(if (placing) "Placing…" else "Place order")
                }
            },
            dismissButton = { TextButton(enabled = !placing, onClick = { buying = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GlassHeroCard(
    title: String = "More Than Just a Chat App",
    subtitle: String = "Learn • Connect • Grow with HolidayLink",
    icon: ImageVector = Icons.Default.AutoAwesome
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(
                listOf(
                    Color(0xFF0B3B70).copy(alpha = 0.90f),
                    Color(0xFF164E63).copy(alpha = 0.86f),
                    Color(0xFF4C1D95).copy(alpha = 0.88f)
                )
            )
        ).padding(20.dp)
    ) {
        Column {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color(0xFF67E8F9), modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.78f))
        }
    }
}

@Composable
private fun GlassTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.Default.ArrowBack, "Back")
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
