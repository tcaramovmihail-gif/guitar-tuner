package ru.eurovafel.guitartuner.ui.feedback

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val RELEASE_URL =
    "https://github.com/tcaramovmihail-gif/guitar-tuner/releases/latest"

/**
 * Обратная связь: оценка приложения и свободное мнение.
 *
 * Отправка идёт напрямую из приложения. Почтовое приложение не открывается,
 * адрес получателя и служебные поля пользователю не показываются.
 */
@Composable
fun FeedbackScreen(viewModel: FeedbackViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("feedback_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Обратная связь",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Оцените приложение и расскажите, что улучшить. Это займёт " +
                    "меньше минуты.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Насколько удобно приложение?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    (1..5).forEach { star ->
                        val selected = star <= state.rating
                        Text(
                            text = if (selected) "★" else "☆",
                            fontSize = 40.sp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier
                                .clickable(enabled = !state.sending) {
                                    viewModel.setRating(star)
                                }
                                .padding(horizontal = 6.dp)
                                .testTag("feedback_star_$star"),
                        )
                    }
                }

                Text(
                    text = when (state.rating) {
                        0 -> "Коснитесь звезды"
                        1 -> "Плохо"
                        2 -> "Так себе"
                        3 -> "Нормально"
                        4 -> "Хорошо"
                        5 -> "Отлично"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                HorizontalDivider()

                OutlinedTextField(
                    value = state.opinion,
                    onValueChange = viewModel::onOpinionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_opinion"),
                    label = { Text("Ваше мнение") },
                    placeholder = {
                        Text("Что нравится, что мешает, чего не хватает")
                    },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !state.sending,
                )

                Text(
                    text = "Можно без имени и контактов: отзыв анонимный.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.error != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = state.error.orEmpty(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Button(
                    onClick = viewModel::send,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_send"),
                    enabled = state.rating > 0 && !state.sending,
                ) {
                    Text(if (state.sending) "Отправляю…" else "Отправить")
                }
            }
        }

        if (state.sent) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("feedback_sent"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Спасибо!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Отзыв получен. Он поможет сделать приложение удобнее.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    TextButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.testTag("feedback_again"),
                    ) {
                        Text("Написать ещё")
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feedback_release"),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Последняя версия",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (versionName.isBlank()) {
                        "Свежий APK и список изменений — на GitHub."
                    } else {
                        "У вас версия $versionName. Свежий APK и список изменений — на GitHub."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_URL))
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_release_open"),
                ) {
                    Text("Открыть страницу релиза")
                }
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Text(
                text = "Гитарный тюнер · офлайн-first · без аккаунта",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
