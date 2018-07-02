package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.{Actor, ActorRef, Props}
import com.tersesystems.demo.greeting.GreetingRepository.Finder
import ocaps.Brand.Sealer
import ocaps.{Brand, RevokedException}

object GreetingActor {
  case class Greet(locale: Locale, zonedDateTime: ZonedDateTime)

  case class RequestFinder(locale: Locale, sealer: Sealer[(Locale, Finder)])

  def props(gatekeeper: ActorRef): Props = {
    Props(classOf[GreetingActor], gatekeeper)
  }
}

class GreetingActor(gatekeeper: ActorRef) extends Actor {
  import GreetingActor._

  private val logger = org.slf4j.LoggerFactory.getLogger("application")

  private[this] val brand = Brand.create[(Locale, Finder)](s"Brand for actor $self")

  private[this] var finderMap: Map[Locale, Finder] = Map()

  private[this] var greeterStack: List[(ActorRef, Greet)] = List()

  override def receive: Receive = greetReceive orElse finderReceive

  def greetReceive: Receive = {
    case g@Greet(locale, zonedDateTime) =>
      finderMap.get(locale) match {
        case None =>
          logger.info(s"Locale $locale not found, going to ask gatekeeper for it.")
          gatekeeper ! RequestFinder(locale, brand.sealer)
          greeterStack = greeterStack :+ (sender() -> g)

        case Some(finder) =>
          logger.info(s"Locale $locale has a finder $finder, using it.")

          try {
            val message = greetingMessage(finder, locale, zonedDateTime)
            sender() ! message
          } catch {
            case revoked: RevokedException =>
              logger.error(s"Finder $finder was revoked!", revoked)
              gatekeeper ! RequestFinder(locale, brand.sealer)
              greeterStack = greeterStack :+ (sender() -> g)
          }
      }
  }

  def finderReceive: Receive = {
    case brand((locale, finder)) =>
      logger.info(s"New finder $finder received for locale $locale")

      // Go through the stack and see if we can go back through these.
      val matching = greeterStack.find{  case (_, greet) => locale == greet.locale }

      var safeToRemove = List[(ActorRef, Greet)]()
      try {
        matching.foreach { case (person, greet) =>
          val message = greetingMessage(finder, locale, greet.zonedDateTime)
          person ! message
          safeToRemove = safeToRemove :+ (person -> greet)
        }

        // If we got through the entire list without a revocation, then keep it for later.
        finderMap += (locale -> finder)
      } catch {
        case revoked: RevokedException =>
          logger.error(s"Finder $finder was revoked!", revoked)
          gatekeeper ! RequestFinder(locale, brand.sealer)
      } finally {
        greeterStack = greeterStack.diff(safeToRemove)
      }
  }

  private def greetingMessage(finder: Finder, locale: Locale, zonedDateTime: ZonedDateTime): String = {
    finder.find(locale, zonedDateTime).map(_.message).getOrElse(s"No greeting found for ${self.path.name} actor!")
  }
}