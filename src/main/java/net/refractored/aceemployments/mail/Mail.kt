package net.refractored.aceemployments.mail

import com.earth2me.essentials.Console
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.stmt.QueryBuilder
import com.j256.ormlite.table.DatabaseTable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.SECTION_CHAR
import net.refractored.aceemployments.AceEmployments
import net.refractored.aceemployments.database.Database.Companion.mailDao
import net.refractored.aceemployments.serializers.ComponentSerializers
import net.refractored.aceemployments.serializers.LocalDateTimeSerializers
import net.refractored.aceemployments.util.MessageUtil
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.util.*

@DatabaseTable(tableName = "joblistings_mail")
data class Mail(
    @DatabaseField(id = true)
    val id: UUID,
    @DatabaseField
    var user: UUID,
    @DatabaseField(persisterClass = LocalDateTimeSerializers::class)
    var timeCreated: LocalDateTime,
    @DatabaseField(persisterClass = LocalDateTimeSerializers::class)
    var timeExpires: LocalDateTime,
    @DatabaseField(persisterClass = ComponentSerializers::class)
    var message: Component,
) {
    /**
     * This constructor should only be used for ORMLite
     */
    constructor() : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDateTime.now(),
        LocalDateTime.now().plusHours(AceEmployments.instance.config.getLong("Mail.ExpireTime")),
        MessageUtil.toComponent(""),
    )

    companion object {
        fun createMail(
            user: UUID,
            message: Component,
        ) {
            if (!AceEmployments.instance.config.getBoolean("Mail.Enabled")) return
            // If essentials is enabled, and config option is enabled, use essentials mail
            AceEmployments.instance.essentials?.let {
                if (!AceEmployments.instance.config.getBoolean("Essentials.UseEssentialsMail")) {
                    val essPlayer = it.users.getUser(user)
                    val expireTime =
                        if (AceEmployments.instance.config.getLong("Mail.ExpireTime") < 1L) {
                            0L
                        } else {
                            (System.currentTimeMillis() + (24 * 3600 * 1000 * AceEmployments.instance.config.getLong("Orders.MinOrdersTime", 48))) // Convert hours to milliseconds
                        }
                    it.mail.sendMail(
                        essPlayer,
                        Console.getInstance(),
                        // Why doesn't this take components? Kill me.
                        LegacyComponentSerializer.legacy(SECTION_CHAR).serialize(message),
                        expireTime,
                    )
                    return
                }
            }
            // Otherwise use my mailing system
            try {
                val mail = Mail()
                val expireTime: Long =
                    if (AceEmployments.instance.config.getLong("Mail.ExpireTime") < 1L) {
                        30L
                    } else {
                        AceEmployments.instance.config.getLong("Mail.ExpireTime")
                    }
                mail.user = user
                mail.message = message
                mail.timeCreated = LocalDateTime.now()
                mail.timeExpires = LocalDateTime.now().plusDays(expireTime)
                mailDao.create(mail)
            } catch (e: kotlin.UninitializedPropertyAccessException) {
                // Database not initialized - mail system requires database or Essentials
                AceEmployments.instance.logger.warning("Cannot create mail: Database not initialized. Please enable database or use Essentials mail.")
            } catch (e: Exception) {
                AceEmployments.instance.logger.warning("Failed to create mail: ${e.message}")
            }
        }

        fun purgeMail() {
            if (!AceEmployments.instance.config.getBoolean("Mail.Enabled")) return
            if (AceEmployments.instance.config.getLong("Mail.ExpireTime") < 1L) return
            AceEmployments.instance.essentials?.let {
                if (AceEmployments.instance.config.getBoolean("Essentials.UseEssentialsMail")) return
            }
            try {
                val queryBuilder: QueryBuilder<Mail, UUID> = mailDao.queryBuilder()
                val allMail = mailDao.query(queryBuilder.prepare())
                for (mail in allMail) {
                    if (LocalDateTime.now().isAfter(mail.timeExpires)) {
                        mailDao.delete(mail)
                    }
                }
            } catch (e: kotlin.UninitializedPropertyAccessException) {
                // Database not initialized - silently skip mail purging
                return
            } catch (e: Exception) {
                // Database not initialized or error accessing mail
                AceEmployments.instance.logger.warning("Failed to purge mail: ${e.message}")
            }
        }

        fun sendMail(player: Player) {
            if (!AceEmployments.instance.config.getBoolean("Mail.Enabled")) return
            try {
                val queryBuilder: QueryBuilder<Mail, UUID> = mailDao.queryBuilder()
                queryBuilder.where().eq("user", player.uniqueId)
                val allMail = mailDao.query(queryBuilder.prepare())
                if (allMail.isEmpty()) return
                for (mail in allMail) {
                    player.sendMessage(mail.message)
                    mailDao.delete(mail)
                }
            } catch (e: kotlin.UninitializedPropertyAccessException) {
                // Database not initialized - silently skip sending mail
                return
            } catch (e: Exception) {
                AceEmployments.instance.logger.warning("Failed to send mail to ${player.name}: ${e.message}")
            }
        }
    }
}
