package com.tersesystems.demo.greeting

import com.tersesystems.demo.services.ServicesModule
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._

trait GreetingModule extends ServicesModule {

  import com.softwaremill.macwire._

  def langs: Langs

  def controllerComponents: ControllerComponents

  def messagesApi: MessagesApi

  lazy val greetingRepository: GreetingRepository = wire[GreetingRepository]

  lazy val greetingController: GreetingController = wire[GreetingController]
}