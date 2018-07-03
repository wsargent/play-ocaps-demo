package com.tersesystems.demo.greeting

import java.time.ZonedDateTime
import java.util.Locale

import akka.actor.SupervisorStrategy.{Escalate, Resume}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.tersesystems.demo.greeting.GreetingRepository._
import com.tersesystems.demo.greeting.GreetingService.Gatekeeper
import ocaps.{Revocable, RevokedException, Revoker}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class GreetingService(system: ActorSystem, greetingRepository: GreetingRepository)(implicit executionContext: ExecutionContext) {

  private implicit val timeout: Timeout = Timeout(100.millis)

  private val gatekeeper = {
    system.actorOf(Gatekeeper.props(greetingRepository), Gatekeeper.name)
  }

  private val greeter = system.actorOf(GreetingActor.props(gatekeeper), "greeter")

  def greet(locale: Locale, time: ZonedDateTime): Future[String] = {
    (greeter ? GreetingActor.Greet(locale, time)).mapTo[String]
  }
}

object GreetingService {

  class Gatekeeper(greetingRepository: GreetingRepository) extends Actor with Timers {

    import Gatekeeper._

    private val logger = org.slf4j.LoggerFactory.getLogger("application")

    private var revokerMap: Map[Locale, Revoker] = Map()

    private var rootFinder: Finder[Id] = _

    override def preStart(): Unit = {
      rootFinder = GreetingRepository.Access(self).finder(greetingRepository)
    }

    override def postStop(): Unit = {
      revokerMap.mapValues(_.revoke())
    }

    override def receive: Receive = {
      case GreetingActor.RequestFinder(locale, sealer) =>
        val finder = createFinder(locale)

        // Mess with the capability so it's only good for a few seconds...
        revokeAfter(locale, 5.seconds)
        sender() ! sealer(locale, finder)

      case Revoke(locale) =>
        revokerMap.get(locale).foreach { revoker =>
          logger.info(s"Revoking finder for locale $locale")
          revoker.revoke()
        }
        revokerMap -= locale
    }

    private def revokeAfter(locale: Locale, duration: FiniteDuration): Unit = {
      timers.startSingleTimer(TickKey, Revoke(locale), duration)
    }

    private def createFinder(locale: Locale): Finder[Try] = {
      def restrictedFinder(localeRestriction: Locale): Finder[Id] = new Finder[Id] {
        override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = {
          if (locale.getLanguage.equals(localeRestriction.getLanguage)) {
            rootFinder.find(locale, zonedDateTime)
          } else {
            None
          }
        }

        override def toString: String = s"""Finder($localeRestriction)"""
      }

      def revocableFinder(finder: Finder[Id]) = {
        // usually we use a macro, but use the explicit version here so we can print
        // out the capability chain in the toString()
        // val Revocable(revocableFinder, revoker) = ocaps.macros.revocable[Finder](finder)
        val Revocable(revocableFinder, revoker) = Revocable(finder) { thunk =>
          new Finder[Id] {
            override def find(l: Locale, zdt: ZonedDateTime): Id[Option[Greeting]] = thunk().find(l, zdt)
            override def toString: String = s"""RevocableFinder(Finder($locale))"""
          }
        }
        revokerMap += locale -> revoker
        context.system.actorOf(RevokeOnActorDeath.props(sender(), revoker))

        // Use a Try here so we don't throw RevokedException around
        new Finder[Try] {
          override def find(locale: Locale, zonedDateTime: ZonedDateTime): Try[Option[Greeting]] = {
            Try(revocableFinder.find(locale, zonedDateTime))
          }
          override def toString: String = s"""TryFinder($revocableFinder)"""
        }
      }

      revocableFinder(restrictedFinder(locale))
    }

    override val supervisorStrategy: SupervisorStrategy =
      OneForOneStrategy(loggingEnabled = true, maxNrOfRetries = 10, withinTimeRange = 1.minute) {
        case e: RevokedException =>
          logger.error("Gatekeeper: Revoked hit supervisor", e)
          Resume
        case t =>
          super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
      }
  }


  object Gatekeeper {
    val name: String = "gatekeeper"

    def props(greetingRepository: GreetingRepository): Props = Props(new Gatekeeper(greetingRepository))

    case class Revoke(locale: Locale)

    private case object TickKey
  }
}