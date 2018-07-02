# play-ocap-demo

This is a simple Play application in Scala that demonstrates the use of object capabilities, using the `ocaps` library.

You can read `ocaps` documentation at https://wsargent.github.io/ocaps.

## Running

```bash
sbt run
```

will start a server up on http://localhost:9000.  

## Architecture

Doing a "GET /" will cause the `GreetingController` to call the `GreetingService`:

```scala
def index: Action[AnyContent] = Action.async { implicit request =>
  val locale = messagesApi.preferred(request).lang.locale
  val zoneId = ZoneId.systemDefault()
  val time = ZonedDateTime.now(zoneId)
  greetingService.greet(locale, time).map { greeting =>
    Ok(greeting)
  }
}
```

The `GreetingService` has access to the `GreetingRepository`, which contains all of the greetings.  However, it does not hand out all the greetings to just anyone.  Instead, it keeps things to itself, and only hands out capabilities that are revocable.

It does this by setting up a gatekeeper actor, which narrows the original finder so it can only find by locale, and then returns a revocable proxy around it.  The capability is then sealed, and sent back to the sender:

```scala
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

    private def revocableFinder(finder: Finder) = {
      // val Revocable(revocableFinder, revoker) = ocaps.macros.revocable[Finder](finder)
      val Revocable(revocableFinder, revoker) = Revocable(finder) { thunk =>
        new Finder {
          override def find(locale: Locale, zonedDateTime: ZonedDateTime): Option[Greeting] = thunk().find(locale, zonedDateTime)
          override def toString: String = s"""RevocableFinder(${thunk()})"""
        }
      }
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
    }
  }
}
```

In the event of an actor terminating unexpectedly, the `RevokeOnActorDeath` actor will revoke the capability automatically.

The `GreetingActor` starts off with no capabilities at all.  Instead, it starts off with a reference to the gatekeeper, and the ability to request capabilities if they have expired.

Whenever the actor uses the `finder` capability, it must check for revocation.  If the capability has been revoked, then it aborts and asks for a new one.  Unfulfilled messages are on the stack until it can find a valid capability.

```scala
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
```
 
One thing to note is that the actor makes use of an `ocaps.Brand` to ensure that the capability is not leaked.  The actor sends the sealer function with the request, and the gatekeeper then sends a sealed box back with the response.  The `brand` has an `unapply` method that can be used in pattern matching: `case brand((locale, finder)) =>` to transparently unseal the boxed value.