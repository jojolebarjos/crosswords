package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._

object Application extends Controller {

  var current_crossword = new Crossword();

  def index = Action {
    Ok(views.html.index("Crossword", current_crossword))
  }

  case class CellsList(cells: List[String])

  val cellsForm = Form(
    mapping(
      "cells" -> list(text)
    )(CellsList.apply)(CellsList.unapply)
  )

  def submit = Action { implicit request =>
    val (cells) = cellsForm.bindFromRequest.get
    current_crossword.saveCells(cells.cells)
    
    Ok(views.html.index("Crossword2", current_crossword))
  }
}