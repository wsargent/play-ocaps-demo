package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.SupervisorStrategy.{Escalate, Resume}
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import com.tersesystems.demo.greeting.GreetingRepository._
import ocaps.{Brand, RevokedException}
import ocaps.Brand.Sealer

import scala.util.{Failure, Success, Try}

object GreetingActor {
  case class Greet(locale: Locale, zonedDateTime: ZonedDateTime)

  case class RequestFinder(locale: Locale, sealer: Sealer[(Locale, Finder[Try])])

  def props(gatekeeper: ActorRef): Props = {
    Props(classOf[GreetingActor], gatekeeper)
  }
}

class GreetingActor(gatekeeper: ActorRef) extends Actor {
  import GreetingActor._
  import scala.concurrent.duration._

  private val logger = org.slf4j.LoggerFactory.getLogger("application")

  private[this] val brand = Brand.create[(Locale, Finder[Try])](s"Brand(${self.path.name})")

  private[this] var finderMap: Map[Locale, Finder[Try]] = Map()

  private[this] var greeterStack: List[(ActorRef, Greet)] = List()

  override def receive: Receive = greetReceive orElse finderReceive

  override val supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(loggingEnabled = true, maxNrOfRetries = 10, withinTimeRange = 1.minute) {
      case e: RevokedException =>
        logger.error("Revoked hit supervisor", e)
        Resume
      case t =>
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }

  def greetReceive: Receive = {
    case g@Greet(locale, zonedDateTime) =>
      finderMap.get(locale) match {
        case None =>
          logger.info(s"Locale $locale not found, going to ask gatekeeper for it.")
          gatekeeper ! RequestFinder(locale, brand.sealer)
          greeterStack = greeterStack :+ (sender() -> g)

        case Some(finder) =>
          logger.info(s"Locale $locale has a finder $finder, using it.")

          greetingMessage(finder, locale, zonedDateTime) match {
            case Success(message) =>
              sender() ! message
            case Failure(ex) =>
              logger.error(s"Finder $finder failed!", ex)
              finderMap -= locale // remove old finder
              gatekeeper ! RequestFinder(locale, brand.sealer) // ask for new finder
              greeterStack = greeterStack :+ (sender() -> g) // stash message.
          }
      }
  }

  def finderReceive: Receive = {
    case brand((locale, finder)) =>
      logger.info(s"New finder $finder received for locale $locale")

      // Go through the stack and see if we can go back through these.
      var safeToRemove = List[(ActorRef, Greet)]()
      greeterStack.foreach { case (person, greet) =>
        if (locale == greet.locale) {
          greetingMessage(finder, locale, greet.zonedDateTime).foreach { message =>
            person ! message
            safeToRemove = safeToRemove :+ (person -> greet)
          }
        }
      }
      finderMap += (locale -> finder)
      greeterStack = greeterStack.diff(safeToRemove)
  }

  private def greetingMessage(finder: Finder[Try], locale: Locale, zonedDateTime: ZonedDateTime): Try[String] = {
    finder.find(locale, zonedDateTime).map { maybeGreeting =>
      maybeGreeting.map(_.message).getOrElse(s"No greeting found for ${self.path.name} actor!")
    }
  }
}