package controllers

import play.twirl.api.Html

/**
 * Created by Greg on 15.03.2015.
 */
class Crossword(height: Int, width: Int, definitions: List[String], cells: List[Tuple4[Boolean, Boolean, Int, String]]) {

  // Default value
  val definition_default = List("ça", "La")
  val cell_default = List(Tuple4(false, false, 0, ""),Tuple4(true, true, 1, "L"), Tuple4(false, false, 0, ""),
    Tuple4(true, true, 0, "ç"), Tuple4(true, false, 0, "a"), Tuple4(false, false, 0, ""),
    Tuple4(false, false, 0, ""), Tuple4(false, false, 0, ""), Tuple4(false, false, 0, ""))

  // Variables
  var cellsInput: List[String] = List.fill(height * width)(" ")

  // Tuple3(Is valid cell, Is first letter, Num definition, Solution char, Submitted char)
  def this() = this(3, 3, List("ça", "La"), List(Tuple4(false, false, 0, ""),Tuple4(true, true, 1, "L"), Tuple4(false, false, 0, ""),
    Tuple4(true, true, 0, "ç"), Tuple4(true, false, 0, "a"), Tuple4(false, false, 0, ""),
    Tuple4(false, false, 0, ""), Tuple4(false, false, 0, ""), Tuple4(false, false, 0, "")))

  def getCellNumber(i: Int, j:Int): Int = j + (i*width)

  def getCellDefaultValue(i: Int, j:Int): String = {
    val cellNumber = getCellNumber(i, j)
    val cellInfo = cells(cellNumber)
    if (cellInfo._1 && cellInfo._2) {
      (cellInfo._3 + 1).toString
    } else {
      " "
    }
  }

  def isCellText(i: Int, j: Int): Boolean = {
    val cellNumber = getCellNumber(i, j)
    val cellInfo = cells(cellNumber)
    cellInfo._1
  }

  def isFirstWordCell(i: Int, j: Int): Boolean = {
    val cellNumber = getCellNumber(i, j)
    val cellInfo = cells(cellNumber)
    cellInfo._1 && cellInfo._2
  }

  def saveCells(cellsList: List[String]) = {
    cellsInput = List()
    var j = 0
    for (i <- 0 to cells.size - 1) {
      if (cells(i)._1) {
        cellsInput = cellsList(j) :: cellsInput
        j += 1
      } else {
        cellsInput = " " :: cellsInput
      }
    }

    cellsInput = cellsInput.reverse
  }

  def isCellCorrect(i: Int, j: Int): Boolean = {
    val cellNumber = getCellNumber(i, j)
    val cellInfo = cells(cellNumber)
    if (cellInfo._1) {
      cellsInput(cellNumber).equals(cellInfo._4)
    } else {
      true
    }
  }

  // Empty is when the cell contains " " or nothing
  def isCellEmpty(i: Int, j: Int): Boolean = {
    val cellNumber = getCellNumber(i, j)
    val cellInfo = cellsInput(cellNumber)
    cellInfo.equals("") || cellInfo.eq(" ")
  }

  // Check before if empty
  def getCellValue(i: Int, j: Int): String = {
    val cellNumber = getCellNumber(i, j)
    cellsInput(cellNumber)
  }

  def getHeight = height
  def getWidth = width
  def getDefinitions = definitions
}
