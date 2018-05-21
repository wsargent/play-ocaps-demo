package com.tersesystems.demo

import com.tersesystems.demo.greeting.GreetingModule
import play.api.ApplicationLoader.Context
import play.api._
import play.api.i18n._
import play.filters.HttpFiltersComponents
import play.api.routing.Router
import play.api.routing.sird._

class MyApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = new MyComponents(context).application
}

class MyComponents(context: Context) extends BuiltInComponentsFromContext(context)
  with GreetingModule
  with I18nComponents
  with HttpFiltersComponents {

  // set up logger
  LoggerConfigurator(context.environment.classLoader).foreach {
    _.configure(context.environment, context.initialConfiguration, Map.empty)
  }

  override val router: Router = Router.from {
    case GET(p"/") => greetingController.index
  }
}

