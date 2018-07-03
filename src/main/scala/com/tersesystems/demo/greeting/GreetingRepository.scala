package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.time.temporal.ChronoField.CLOCK_HOUR_OF_DAY
import java.time.temporal.TemporalQuery
import java.util.Locale

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
    val isNight: TemporalQuery[Boolean] = _.get(CLOCK_HOUR_OF_DAY) <= 20

    if (date.query(isMorning)) {
      "morning"
    } else if (date.query(isDay)) {
      "day"
    } else if (date.query(isAfternoon)) {
      "afternoon"
    } else if (date.query(isNight)) {
      "night"
    } else {
      "unknown"
    }
  }

  private def find(locale: Locale, date: ZonedDateTime): Option[Greeting] = {
    val timeOfDay = findTimeOfDay(date)

    // See https://www.playframework.com/documentation/2.6.x/ScalaI18N
    implicit val lang: Lang = Lang(locale)
    messagesApi.translate(timeOfDay, Seq.empty) match  {
      case Some(message) =>
        logger.info(s"Found message $message found for lang $lang, timeOfDay = $timeOfDay")
        Some(Greeting(message, timeOfDay))
      case None =>
        logger.error(s"No message found for lang $lang, timeOfDay = $timeOfDay")
        None
    }
  }

  private object capabilities {
    val finder: Finder = new Finder() {
      override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = {
        GreetingRepository.this.find(locale, zonedDateTime: ZonedDateTime)
      }
    }
  }

}

object GreetingRepository {

  trait Finder {
    def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting]
  }

  class Access private {
    def finder(repo: GreetingRepository): Finder = repo.capabilities.finder
  }

  object Access {
    private val instance = new Access()

    def apply(): Access =  instance
  }

}
