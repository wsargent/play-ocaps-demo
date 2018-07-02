package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.tersesystems.demo.greeting.GreetingRepository.Finder
import ocaps.{Revocable, Revoker}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class GreetingService(system: ActorSystem)(implicit executionContext: ExecutionContext) {

  private[this] implicit val timeout: Timeout = Timeout(100.millis)

  private[this] val greetingRepository: GreetingRepository = new GreetingRepository()

  private[this] val rootFinder = GreetingRepository.Access().finder(greetingRepository)

  private[this] val gatekeeper = system.actorOf(Props(classOf[Gatekeeper]), "gatekeeper")

  private[this] val greeter = system.actorOf(GreetingActor.props(gatekeeper), "greeter")

  def greet(locale: Locale, time: ZonedDateTime): Future[String] = {
    (greeter ? GreetingActor.Greet(locale, time)).mapTo[String]
  }

  private class Gatekeeper extends Actor {
    private var revokerMap: Map[ActorRef, Revoker] = Map()

    private def revocableFinder(finder: Finder) = {
      // val Revocable(revocableFinder, revoker) = ocaps.macros.revocable[Finder](finder)
      val Revocable(revocableFinder, revoker) = Revocable(finder) { thunk =>
        new Finder {
          override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = thunk().find(locale, zonedDateTime)
          override def toString: String = s"""RevocableFinder(${thunk()})"""
        }
      }
      revokerMap += sender() -> revoker
      system.actorOf(RevokeOnActorDeath.props(sender(), revoker))
      revocableFinder
    }

    private[this] def restrictedFinder(localeRestriction: Locale) = new Finder {
      override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = {
        if (locale.getLanguage.equals(localeRestriction.getLanguage)) {
          rootFinder.find(locale, zonedDateTime)
        } else {
          None
        }
      }

      override def toString: String = s"""Finder($localeRestriction)"""
    }

    override def receive: Receive = {
      case GreetingActor.RequestFinder(locale, sealer) =>
        sender() ! sealer(locale, revocableFinder(restrictedFinder(locale)))
      case Gatekeeper.RevokeAll =>
        revokerMap.mapValues(_.revoke())
    }
  }

  object Gatekeeper {
    case object RevokeAll
  }
}