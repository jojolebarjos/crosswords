package crosswords.data.mine

import java.io.{FileWriter, IOException, File}
import scala.io.Source
import java.io.File
import crosswords.util._
import play.api.libs.json.{Json, JsObject}

object CrosswordPuzzleGame {
  
  val source = "Crossword Puzzle Game"
  val language = "eng"
  var title = ""
  var url = ""
  val date = "2015-03-16"
  
  var crossword_type = ""
  var grid_size = 0

  // (clue_index -> (x, y)): Map[Int,(Int, Int)]
  var clues_coordinates = Map[Int, (Int, Int)]()

  // ((x, y) -> character): Map[(Int, Int),String]
  var solution_coordinates = Map[(Int, Int),String]()

  val pattern_whole_cases = """(\d*)(&nbsp)(;?)""".r
  val sol_case_pattern = """(25>)(\w|&nbsp)(</TD)""".r
  val clue_pattern = """(\d+)\. (.+)(.<BR>)""".r
  val title_pattern = """<title>(.+)</title>""".r
  val crossword_identification_pattern = """(.+) (\d+) [(](.+)[)]""".r

  val base_input_path = "/home/matteo/Scuola/Big_Data/Project/data/html/"
  val base_output_path = "/home/matteo/Scuola/Big_Data/Project/data/json/"

  def get_sol_character(sol_coord_x: Int, sol_coord_y: Int, map: Map[(Int, Int), String]): String = {
    map.get((sol_coord_x, sol_coord_y)) match {
      case Some(s) => s
      case None => "?"
    }
  }

  def get_clue_coordinates(clue_index: Int, map: Map[Int, (Int, Int)]): (Int, Int) = {
    map.get(clue_index) match {
      case Some(s) => s
      case None => (-1, -1)
    }
  }
  
  // This is not really scala-like but it does the job
  def get_word_solution(clue_index: Int, direction: String): String  = {
    val clue_coord = get_clue_coordinates(clue_index, clues_coordinates)
    var clue_solution = ""
    var x = clue_coord._1
    var y = clue_coord._2
    var continue = true
    while (continue) {
      val char = get_sol_character(x, y, solution_coordinates)
      if (char == "none" || y >= grid_size || x >= grid_size) {
        continue = false
      } else {
        clue_solution += char
        if (direction == "East") {
          x = x + 1
        } else {
          y = y + 1
        }
      }
    }
    clue_solution
  }
  
  def initialize_crossword(lines: String) = {
    title = title_pattern.findAllMatchIn(lines).map { x => x.group(1) }.toList.mkString
    val number_and_type = crossword_identification_pattern.findAllMatchIn(title).map { x => (x.group(2), x.group(3)) }.toList(0)
    val crossword_number = number_and_type._1
    number_and_type._2 match {
      case "Extra Small Grid" => crossword_type = "gt"; grid_size = 12
      case "Small Grid" => crossword_type = "gs"; grid_size = 14
      case "Medium Grid" => crossword_type = "gm"; grid_size = 16
      case "Large Grid" => crossword_type = "gl"; grid_size = 18
    }
    url = "http://www.crosswordpuzzlegames.com/puzzles/"+crossword_type+"_"+crossword_number+".html"
  }
  
  def parse(puzzle_filepath: String, solution_filepath: String) = {
    val puz_file = new File(puzzle_filepath)
    val sol_file = new File(solution_filepath)
    val puz_lines = Source.fromFile(puz_file).mkString
    val sol_lines = Source.fromFile(sol_file).mkString
    
    initialize_crossword(puz_lines)
    
    val grid_clues_indexes = pattern_whole_cases.findAllMatchIn(puz_lines).map { x => if (x.group(1) != "") x.group(1).toInt else 0 }.toList
    val grid_solution = sol_case_pattern.findAllMatchIn(sol_lines).map { x => if(x.group(2) == "&nbsp") "none" else x.group(2) }.toList
    val grid_coordinates = List.range(0, grid_size*grid_size).map { x => (x%grid_size, x/grid_size) }
    
    // (clue_index -> (x, y)): Map[Int,(Int, Int)]
    clues_coordinates =  grid_clues_indexes.zip(grid_coordinates).filter(x => x._1 != 0).map(y => y._1 -> y._2).toMap
    
    // ((x, y) -> character): Map[(Int, Int),String]
    solution_coordinates = grid_coordinates.zip(grid_solution).toMap
    
    val orizontal_clues_lines = Text.bound(puz_lines, """<B>ACROSS</B>""", """<B>DOWN</B>""")
    val orizontal_clues = clue_pattern.findAllMatchIn(orizontal_clues_lines).map { x => (x.group(1).toInt, x.group(2), "East") }.toList
    
    val vertical_clues_lines = Text.bound(puz_lines, """<B>DOWN</B>""", """</TABLE></CENTER>""")
    val vertical_clues = clue_pattern.findAllMatchIn(vertical_clues_lines).map { x => (x.group(1).toInt, x.group(2), "South") }.toList
    
    // (clue_index, clue_definition, direction): List[(Int, String, String)]
    val clues = orizontal_clues ::: vertical_clues
    
    val words = Json.obj(
      "words" -> clues.map{case x => Json.obj(
        "word" -> get_word_solution(x._1, x._3),
        "clue" -> x._2,
        "x" -> get_clue_coordinates(x._1, clues_coordinates)._1,
        "y" -> get_clue_coordinates(x._1, clues_coordinates)._2,
        "dir" -> x._3
      )}
    )
    
    // write to json
    var json = Json.obj()
    json += "source" -> Json.toJson(source)
    json += "language" -> Json.toJson(language)
    json += "title" -> Json.toJson(title)
    json += "url" -> Json.toJson(url)
    json += "date" -> Json.toJson(date)
    json ++= words

    val json_printable = Json.prettyPrint(json)
    val jsonFile = new File(puzzle_filepath.replace("html", "json") )
    val out = new FileWriter(jsonFile)
    out.write(json_printable)
    out.close()
  }
  
  def main(args: Array[String]): Unit = {
    println("\n>> Start parsing\n")
    val number_of_files = 32400
      for( index <- 1 to number_of_files){
        val puzzle_filepath = base_input_path + "crosswordpuzzlegames_puzzle_" + index + ".html"
        val solution_filepath = base_input_path + "crosswordpuzzlegames_solution_" + index + ".html"
        println(">> Parsing " + puzzle_filepath)
        parse(puzzle_filepath, solution_filepath)
      }
    println("\n>> Done\n")
  }
}
