package com.tersesystems.demo.greeting

import java.util.Locale

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.softwaremill.macwire.akkasupport._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class GreetingService(system: ActorSystem)(implicit executionContext: ExecutionContext) {

  private implicit val timeout: Timeout = Timeout(50.millis)

  private val greetingActor = wireActor[GreeterActor]("greeter")

  def greet(locale: Locale): Future[String] = {

    (greetingActor ? GreeterActor.Greet(locale)).mapTo[String]
  }

}