package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.Actor

object GreetingActor {
  case class Greet(locale: Locale, zonedDateTime: ZonedDateTime)
}

class GreetingActor(finder: GreetingRepository.Finder) extends Actor {
  import GreetingActor._

  override def receive: Receive = {
    case Greet(locale, zonedDateTime) =>
      sender() ! greetingMessage(locale, zonedDateTime)
  }

  private def greetingMessage(locale: Locale, zonedDateTime: ZonedDateTime): String = {
    finder.find(locale, zonedDateTime).map(_.message).getOrElse(s"No greeting found for ${self.path.name} actor!")
  }
}