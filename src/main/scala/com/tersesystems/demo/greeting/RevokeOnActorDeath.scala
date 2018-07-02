package com.tersesystems.demo.greeting

import akka.actor.{Actor, ActorRef, Props, Terminated}
import ocaps.Revoker

/**
 * Revokes a capability when an actor has stopped unexpectedly.
 *
 * @param ref
 * @param revoker
 */
class RevokeOnActorDeath(ref: ActorRef, revoker: Revoker) extends Actor {
  override def preStart(): Unit = {
    context.watch(ref)
  }
  override def receive: Receive = {
    case Terminated =>
      revoker.revoke()
  }
}

object RevokeOnActorDeath {
  def props(ref: ActorRef, revoker: Revoker): Props = Props(new RevokeOnActorDeath(ref, revoker))
}
