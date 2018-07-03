package com.tersesystems.demo.services

import akka.actor.ActorSystem
import com.tersesystems.demo.greeting.{GreetingRepository, GreetingService}

import scala.concurrent.ExecutionContext

trait ServicesModule {
  import com.softwaremill.macwire._

  def actorSystem: ActorSystem

  implicit def executionContext: ExecutionContext

  def greetingRepository: GreetingRepository

  lazy val greetingService: GreetingService = wire[GreetingService]
}
