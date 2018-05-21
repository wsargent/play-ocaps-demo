package com.tersesystems.demo.greeting

import java.time.{ZoneId, ZonedDateTime}

import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.concurrent.ExecutionContext

class GreetingController(greetingService: GreetingService,
                        messagesApi: MessagesApi,
                        cc: ControllerComponents)(implicit executionContext: ExecutionContext)
  extends AbstractController(cc) {

  def index: Action[AnyContent] = Action.async { implicit request =>
    val locale = messagesApi.preferred(request).lang.locale
    val zoneId = ZoneId.systemDefault()
    val time = ZonedDateTime.now(zoneId)
    greetingService.greet(locale, time).map { greeting =>
      Ok(greeting)
    }
  }

}

