package com.tersesystems.demo.services

import akka.actor.ActorSystem
import com.tersesystems.demo.greeting.GreetingService

import scala.concurrent.ExecutionContext

trait ServicesModule {
  import com.softwaremill.macwire._

  def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  lazy val greetingService: GreetingService = wire[GreetingService]
}
