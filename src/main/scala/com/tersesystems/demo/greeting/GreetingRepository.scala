package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.time.temporal.ChronoField.CLOCK_HOUR_OF_DAY
import java.time.temporal.TemporalQuery
import java.util.Locale

import com.tersesystems.demo.greeting.GreetingService.Gatekeeper
import play.api.i18n.{Lang, MessagesApi}

class GreetingRepository(messagesApi: MessagesApi) {

  import GreetingRepository._

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  private def findTimeOfDay(date: ZonedDateTime): String = {
    val isMorning: TemporalQuery[Boolean] = _.get(CLOCK_HOUR_OF_DAY) < 12
    val isDay: TemporalQuery[Boolean] = _.get(CLOCK_HOUR_OF_DAY) == 12
    val isAfternoon: TemporalQuery[Boolean] = time => {
      def hour = time.get(CLOCK_HOUR_OF_DAY)
      hour > 12 && hour < 20
    }
    val isNight: TemporalQuery[Boolean] = _.get(CLOCK_HOUR_OF_DAY) >= 20

    if (date.query(isMorning)) "morning"
    else if (date.query(isDay)) "day"
    else if (date.query(isAfternoon)) "afternoon"
    else if (date.query(isNight)) "night"
    else {
      logger.error(s"Cannot find time, date = $date, CLOCK_HOUR_OF_DAY = ${date.get(CLOCK_HOUR_OF_DAY)}")
      "unknown"
    }
  }

  private def find(locale: Locale, date: ZonedDateTime): Option[Greeting] = {
    val timeOfDay = findTimeOfDay(date)

    // See https://www.playframework.com/documentation/2.6.x/ScalaI18N
    implicit val lang: Lang = Lang(locale)
    messagesApi.translate(timeOfDay, Seq.empty) match {
      case Some(message) =>
        logger.info(s"Found message $message found for lang $lang, timeOfDay = $timeOfDay")
        Some(Greeting(message, timeOfDay))
      case None =>
        logger.error(s"No message found for lang $lang, timeOfDay = $timeOfDay")
        None
    }
  }

  private object capabilities {
    val finder: Finder[Id] = (locale: Locale, zonedDateTime: ZonedDateTime) => {
      GreetingRepository.this.find(locale, zonedDateTime)
    }
  }

}

object GreetingRepository {
  type Id[A] = A

  trait Finder[F[_]] {
    def find(locale: Locale, zonedDateTime: ZonedDateTime): F[Option[Greeting]]
  }

  class Access private {
    def finder(repo: GreetingRepository): Finder[Id] = repo.capabilities.finder
  }

  object Access {
    private val instance = new Access()

    // We can't prevent someone from calling this, but we can at least
    // use a whitelist to prevent confused/lazy programmers from using it
    // indiscriminately.
    def apply(caller: akka.actor.ActorRef): Access = {
      if (caller.path.name == Gatekeeper.name) {
        instance
      } else {
        throw new IllegalAccessException(s"Wrong actor: ${caller.path.name}")
      }
    }
  }

}
