package com.example.ui.sos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sos.SosGrant
import com.example.ui.components.RokkhonBottomSheet
import com.example.ui.components.RokkhonEmptyState
import com.example.ui.components.enterOnce
import com.example.ui.components.springClick
import com.example.ui.icons.UIcons
import com.example.ui.theme.RokkhonPalette
import com.example.ui.viewmodel.PrivateSosViewModel

/** People who added you to their SOS list. You can leave any of them. */
@Composable
fun SosAccessScreen(
    vm: PrivateSosViewModel,
    bn: Boolean,
    onBack: () -> Unit,
    onOpenHistory: (SosGrant) -> Unit
) {
    BackHandler(onBack = onBack)
    val toMe by vm.grantsToMe.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf<SosGrant?>(null) }

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            SosTopBar(title = t(bn, "যারা আমাকে যোগ করেছেন", "Who added me"), onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                if (toMe.isEmpty()) {
                    item {
                        RokkhonEmptyState(icon = UIcons.People, title = t(bn, "এখনও কেউ নেই", "Nobody yet"), body = "", testTag = "added_me_empty")
                    }
                }
                items(toMe, key = { it.ownerUid }) { g ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(RokkhonPalette.Subtle)
                            .padding(14.dp)
                            .enterOnce()
                            .testTag("added_me_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(g.ownerName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.ownerName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RokkhonPalette.Ink, maxLines = 1)
                            Text(
                                listOfNotNull(
                                    if (g.perms.location) t(bn, "লোকেশন", "Location") else null,
                                    if (g.perms.audio) t(bn, "অডিও", "Audio") else null
                                ).joinToString(" · ").ifEmpty { t(bn, "অ্যালার্ট", "Alerts") },
                                fontSize = 12.5.sp, color = RokkhonPalette.Muted
                            )
                        }
                        if (g.perms.history) {
                            Icon(UIcons.History, contentDescription = t(bn, "আগের SOS", "Past SOS"), tint = RokkhonPalette.Ink,
                                modifier = Modifier.size(36.dp).clip(CircleShape).springClick { onOpenHistory(g) }.padding(8.dp))
                        }
                        Icon(UIcons.Logout, contentDescription = t(bn, "সরে আসুন", "Leave"), tint = RokkhonPalette.Ink,
                            modifier = Modifier.size(36.dp).clip(CircleShape).springClick { leaving = g }.padding(8.dp).testTag("invite_leave"))
                    }
                }
            }
        }
    }

    leaving?.let { g ->
        RokkhonBottomSheet(onDismiss = { leaving = null }, title = g.ownerName, testTag = "leave_sheet") {
            PrimaryButton(t(bn, "তালিকা থেকে সরে আসুন", "Leave their list"), danger = true, testTag = "leave_confirm", onClick = { vm.leave(g); leaving = null })
            Spacer(Modifier.height(8.dp))
            SecondaryButton(t(bn, "থাকুক", "Stay"), onClick = { leaving = null }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 44.dp, dark: Boolean = false) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (dark) RokkhonPalette.Primary else RokkhonPalette.Chip),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, color = if (dark) Color.White else RokkhonPalette.Ink)
    }
}

