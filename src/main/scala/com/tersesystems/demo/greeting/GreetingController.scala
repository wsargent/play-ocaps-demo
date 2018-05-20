package com.tersesystems.demo.greeting

import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.concurrent.ExecutionContext

class GreeterController(greetingService: GreetingService,
                        messagesApi: MessagesApi,
                        cc: ControllerComponents)(implicit executionContext: ExecutionContext)
  extends AbstractController(cc) {

  def index = Action.async { implicit request =>
    val locale = messagesApi.preferred(request).lang.locale
    greetingService.greet(locale).map { greeting =>
      Ok(greeting)
    }
  }

}

