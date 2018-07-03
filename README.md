# play-ocaps-demo

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

It does this by setting up a gatekeeper actor, which narrows the original finder so it can only find by locale, and then returns a revocable proxy around it.  The capability is then sealed, and sent back to the sender.  After a set period of time, the gatekeeper will revoke the capability.  In the event of an actor terminating unexpectedly, the `RevokeOnActorDeath` actor will revoke the capability automatically.

The `GreetingActor` starts off with no capabilities at all.  Instead, it starts off with a reference to the gatekeeper, and the ability to request capabilities if they have expired.

Whenever the actor uses the `finder` capability, it must check for revocation.  If the capability has been revoked, then it aborts and asks for a new one.  Unfulfilled messages are on the stack until it can find a valid capability.
 
One thing to note is that the actor makes use of an `ocaps.Brand` to ensure that the capability is not leaked.  The actor sends the sealer function with the request, and the gatekeeper then sends a sealed box back with the response.  The `brand` has an `unapply` method that can be used in pattern matching: `case brand((locale, finder)) =>` to transparently unseal the boxed value.

### Why Not Use ActorRef as Capabilities?

The actor model is a natural fit for capabilities, it's a natural question to ask why capabilities are being passed at the class level, rather than at the Actor level.

The reason is that Akka Actors are primarily concerned with concurrency, and actors are a [concurrency primitive rather than a security primitive](https://en.wikipedia.org/wiki/Object-capability_model#Relationship_of_the_object-capability_model_and_the_Actor_model).   As such, you can use `ActorSelection` to reference any actor by name, bypassing the capability ruleset.

Also, it's easy to use ActorRef in an actor context, but awkward to use Actors outside of that context, and ActorRef is opaque.  

Finally, capabilities implemented as classes are compile time safe, which is always nice.

### Why use Dynamic Sealing?

Dynamic sealing is a "best practice" to prevent unintentional leakage of capabilities.  When a capability is passed from the gatekeeper to the greeting actor, the capability is sent as a sealed `(Locale, Reader)` tuple.  Only the greeting actor has the unsealer, so no other class can get at that tuple.

This is necessary because Akka allows snooping on the [event bus](https://doc.akka.io/docs/akka/2.5/event-bus.html) that carries messages, particularly on [dead letters](https://doc.akka.io/docs/akka/2.5/event-bus.html#dead-letters).  Using a sealer guarantees that no other actor can use the capability, even if the actor dies and it goes to dead letter subscribers.

Is it necessary in this example?  Well, yes and no.  No, from the perspective of "it wouldn't go in this example."  Yes, from the perspective of [secure by default](https://en.wikipedia.org/wiki/Secure_by_default) design, and also because dynamic sealing is useful in several contexts.  For example, a publish/subscribe model can use dynamic sealing to safely pass sensitive information even if only some of the recipients have the unsealer.