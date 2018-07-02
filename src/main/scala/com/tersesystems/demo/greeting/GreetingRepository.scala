package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.time.temporal.{ChronoField, TemporalAccessor, TemporalQuery}
import java.util.Locale

class GreetingRepository {
  import GreetingRepository._

  private val englishGreetings = Seq(
    Greeting(message = "Good morning!", name = "morning"),
    Greeting(message = "Good day!", name = "day"),
    Greeting(message = "Good afternoon!", name = "afternoon"),
    Greeting(message = "Good evening!", name = "evening")
  )

  private val frenchGreetings = Seq(
    Greeting(message = "Bonjour!", name = "morning"),
    Greeting(message = "Bonjour!", name = "day"),
    Greeting(message = "Bon apre-midi!", name = "afternoon"),
    Greeting(message = "Bon nuit!", name = "evening")
  )

  private val isMorning = new TemporalQuery[Boolean] {
    override def queryFrom(time: TemporalAccessor): Boolean = {
      if (time.get(ChronoField.CLOCK_HOUR_OF_DAY) < 12) true else false
    }
  }

  private val isDay = new TemporalQuery[Boolean] {
    override def queryFrom(time: TemporalAccessor): Boolean = {
      if (time.get(ChronoField.CLOCK_HOUR_OF_DAY) == 12) true else false
    }
  }

  private val isAfternoon = new TemporalQuery[Boolean] {
    override def queryFrom(time: TemporalAccessor): Boolean = {
      val hour = time.get(ChronoField.CLOCK_HOUR_OF_DAY)
      if (hour > 12 && hour < 20) true else false
    }
  }

  private val isNight = new TemporalQuery[Boolean] {
    override def queryFrom(time: TemporalAccessor): Boolean = {
      val hour = time.get(ChronoField.CLOCK_HOUR_OF_DAY)
      if (hour <= 20) true else false
    }
  }

  private def find(locale: Locale, date: ZonedDateTime): Option[Greeting] = {
    def findTime(greetings: Seq[Greeting]): Option[Greeting] = {
      if (date.query(isMorning)) {
         greetings.find(_.name == "morning")
      } else if (date.query(isDay)) {
         greetings.find(_.name == "day")
      } else if (date.query(isDay)) {
         greetings.find(_.name == "afternoon")
      } else if (date.query(isNight)) {
         greetings.find(_.name == "night")
      } else {
        None
      }
    }

    if (locale.getLanguage.equals(Locale.ENGLISH.getLanguage)) {
      findTime(englishGreetings)
    } else if (locale.getLanguage.equals(Locale.FRENCH.getLanguage)) {
      findTime(frenchGreetings)
    } else {
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
    def apply(): Access = instance
  }
}
