package com.tersesystems.demo.greeting

import java.util.Locale

import akka.actor.Actor

object GreeterActor {
  case class Greet(locale: Locale)
  final case class WhoToGreet(who: String)
}

class GreeterActor extends Actor {
  import GreeterActor._

  override def receive = {
    case Greet(locale) if locale.getLanguage == Locale.ENGLISH.getLanguage =>
      sender() ! "Hello world"
    case Greet(locale) =>
      sender() ! s"Hello world (unknown locale $locale)"
  }
}