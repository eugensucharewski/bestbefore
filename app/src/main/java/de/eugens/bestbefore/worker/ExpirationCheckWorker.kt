package de.eugens.bestbefore.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.eugens.bestbefore.MainActivity
import de.eugens.bestbefore.R
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.products.domain.model.Product
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@HiltWorker
class ExpirationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ProductRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val threshold = settingsRepository.getExpirationThresholdFlow().first()
        val products = repository.getProducts()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        val changedProducts = products.filter { product ->
            val statusYesterday = getStatusForDate(product, yesterday, threshold)
            val statusToday = getStatusForDate(product, today, threshold)

            statusYesterday == ProductStatus.GREEN && (statusToday == ProductStatus.YELLOW || statusToday == ProductStatus.RED)
        }

        if (changedProducts.isNotEmpty()) {
            showNotification(changedProducts)
        }

        return Result.success()
    }

    private fun parseDate(dateStr: String): LocalDate? {
        val formats = listOf("dd.MM.yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "d.M.yyyy", "yyyy/MM/dd")
        for (format in formats) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(format))
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun getStatusForDate(product: Product, date: LocalDate, threshold: Int): ProductStatus {
        val expiryDate = parseDate(product.expirationDate) ?: return ProductStatus.GREEN
        val daysUntil = ChronoUnit.DAYS.between(date, expiryDate)
        return when {
            daysUntil < 0 -> ProductStatus.RED
            daysUntil <= threshold -> ProductStatus.YELLOW
            else -> ProductStatus.GREEN
        }
    }

    private fun showNotification(products: List<Product>) {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "expiration_alerts"

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.expiration_alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FILTER_TYPE", "EXPIRED_AND_UPCOMING")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (products.size == 1) {
            context.getString(R.string.product_status_changed, products[0].name)
        } else {
            context.getString(R.string.multiple_products_status_changed, products.size)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.expiration_alert_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }

    enum class ProductStatus {
        GREEN, YELLOW, RED
    }
}
