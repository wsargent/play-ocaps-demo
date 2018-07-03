package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.tersesystems.demo.greeting.GreetingRepository.Finder
import com.tersesystems.demo.greeting.GreetingService.Gatekeeper
import ocaps.{Revocable, Revoker}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class GreetingService(system: ActorSystem, greetingRepository: GreetingRepository)(implicit executionContext: ExecutionContext) {

  private implicit val timeout: Timeout = Timeout(100.millis)

  private val gatekeeper = {
    val rootFinder = GreetingRepository.Access().finder(greetingRepository)
    system.actorOf(Gatekeeper.props(rootFinder), "gatekeeper")
  }

  private val greeter = system.actorOf(GreetingActor.props(gatekeeper), "greeter")

  def greet(locale: Locale, time: ZonedDateTime): Future[String] = {
    (greeter ? GreetingActor.Greet(locale, time)).mapTo[String]
  }
}

object GreetingService {

  class Gatekeeper(rootFinder: Finder) extends Actor {
    private var revokerMap: Map[ActorRef, Revoker] = Map()

    override def receive: Receive = {
      case GreetingActor.RequestFinder(locale, sealer) =>
        sender() ! sealer(locale, revocableFinder(restrictedFinder(locale)))
      case Gatekeeper.RevokeAll =>
        revokerMap.mapValues(_.revoke())
    }

    private def revocableFinder(finder: Finder) = {
      // usually we use a macro, but use the explicit version here so we can print
      // out the capability chain in the toString()
      // val Revocable(revocableFinder, revoker) = ocaps.macros.revocable[Finder](finder)
      val Revocable(revocableFinder, revoker) = Revocable(finder) { thunk =>
        new Finder {
          override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = thunk().find(locale, zonedDateTime)
          override def toString: String = s"""RevocableFinder(${thunk()})"""
        }
      }
      revokerMap += sender() -> revoker
      context.system.actorOf(RevokeOnActorDeath.props(sender(), revoker))
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
  }

  object Gatekeeper {
    def props(finder: Finder): Props = Props(new Gatekeeper(finder))

    case object RevokeAll
  }
}