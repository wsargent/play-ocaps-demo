package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.tersesystems.demo.greeting.GreetingRepository.Finder

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class GreetingService(system: ActorSystem)(implicit executionContext: ExecutionContext) {

  private implicit val timeout: Timeout = Timeout(50.millis)

  private val greetingRepository: GreetingRepository = new GreetingRepository()

  // Can find any greeting
  private val rootFinder = GreetingRepository.Access().finder(greetingRepository)

  // Can only find french greetings
  private val frenchFinder = new Finder {
    override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = {
      if (locale.getLanguage().equals(Locale.FRENCH.getLanguage)) {
        rootFinder.find(locale, zonedDateTime)
      } else {
        None
      }
    }
  }

  // Can only find english greetings
  private val englishFinder = new Finder {
    override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = {
      if (locale.getLanguage().equals(Locale.ENGLISH.getLanguage)) {
        rootFinder.find(locale, zonedDateTime)
      } else {
        None
      }
    }
  }

  private val frenchGreeter = system.actorOf(Props(classOf[GreetingActor], frenchFinder), "frenchGreeter")
  private val englishGreeter = system.actorOf(Props(classOf[GreetingActor], englishFinder), "englishGreeter")

  def greet(locale: Locale, time: ZonedDateTime): Future[String] = {
    locale.getLanguage match {
      case english if english.equals(Locale.ENGLISH.getLanguage) =>
        (englishGreeter ? GreetingActor.Greet(locale, time)).mapTo[String]
      case french if french.equals(Locale.FRENCH.getLanguage) =>
        (frenchGreeter ? GreetingActor.Greet(locale, time)).mapTo[String]
    }
    //(frenchGreeter ? GreetingActor.Greet(locale, time)).mapTo[String]
  }

}