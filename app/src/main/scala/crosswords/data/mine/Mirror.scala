package crosswords.data.mine

import java.io.{File, FileWriter, IOException}
import java.util._

import crosswords.util._
import play.api.libs.json.{JsObject, Json}

import scala.io.Source

/**
 * Tools used to automatically data mine from <a href="http://www.mirror.co.uk/play/crosswords">Mirror</a>.
 *
 * @author Timothee Emery
 */
object Mirror {

  /*
  The crossword HTML page has the following format:
  (ref view-source:http://s3.mirror.co.uk/mirror/crosswords/mir_straight_20140401.html)

   <div class="row"><div id='cell-1-1' class='cell blank'></div><div id='cell-2-1' class='cell blank'></div><div id='cell-3-1' class='cell ' data-clueid='12' data-reverted='0'>
                    <span class='cell-number'>1</span>
                    <div class='cell-input' data-solution='E'></div>
                </div><div id='cell-4-1' class='cell blank'></div><div id='cell-5-1' class='cell ' data-clueid='13' data-reverted='0'>
                    <span class='cell-number'>2</span>
                    <div class='cell-input' data-solution='T'></div>
                </div><div id='cell-6-1' class='cell blank'></div><div id='cell-7-1' class='cell ' data-clueid='14' data-reverted='0'>
                    <span class='cell-number'>3</span>
                    <div class='cell-input' data-solution='S'></div>

    (...)

                <div id='cell-2-2' class='cell ' data-clueid='1' data-reverted='0'>

                    <div class='cell-input' data-solution='A'></div>
                </div><div id='cell-3-2' class='cell ' data-clueid='1-12' data-reverted='0'>

                    <div class='cell-input' data-solution='N'></div>
                </div><div id='cell-4-2' class='cell ' data-clueid='1' data-reverted='0'>

                    <div class='cell-input' data-solution='T'></div>
                </div>

     (...)

     <div class="clues">
                    <h2>ACROSS</h2>
                    <div class="clues-list across">
                        <ul class="clues-across">
                            <li id='clue-1' data-direction='across' data-x1='1' data-x2='12' data-y1='2' data-y2='2'
                    data-islink='0' data-linkid='clue-' data-cluenum='6'> <b>6</b> Quarrelsome

      (12) </li><li id='clue-2' data-direction='across' data-x1='1' data-x2='7' data-y1='4' data-y2='4'
                    data-islink='0' data-linkid='clue-' data-cluenum='8'> <b>8</b> Ask to marry

      (7) </li><li id='clue-3' data-direction='across' data-x1='9' data-x2='13' data-y1='4' data-y2='4'
                    data-islink='0' data-linkid='clue-' data-cluenum='9'> <b>9</b> Order given to dogs

      (5) </li><li id='clue-4' data-direction='across' data-x1='1' data-x2='4' data-y1='6' data-y2='6'
                    data-islink='0' data-linkid='clue-' data-cluenum='10'> <b>10</b> Chuck


      (...)

      <div class="clues">
                    <h2>DOWN</h2>
                    <div class="clues-list down">
                        <ul class="clues-down">
                            <li id='clue-12' data-direction='down' data-x1='3' data-x2='3' data-y1='1' data-y2='8'
                    data-islink='0' data-linkid='clue-' data-cluenum='1'> <b>1</b> Gives approval to

        (8) </li><li id='clue-13' data-direction='down' data-x1='5' data-x2='5' data-y1='1' data-y2='5'
                    data-islink='0' data-linkid='clue-' data-cluenum='2'> <b>2</b> Hooked claw

        (5) </li><li id='clue-14' data-direction='down' data-x1='7' data-x2='7' data-y1='1' data-y2='5'
                    data-islink='0' data-linkid='clue-' data-cluenum='3'> <b>3</b> Winter sportsman

       (5) </li><li id='clue-15' data-direction='down' data-x1='9' data-x2='9' data-y1='1' data-y2='7'
                     data-islink='0' data-linkid='clue-' data-cluenum='4'> <b>4</b> Aimless or rootless person

 */


  //Calendar
  private val _calendar = Calendar.getInstance()
  //Format used for browsing on the website
  private val _formatForBrowsing = new java.text.SimpleDateFormat("yyyyMMdd")
  //Format used for our application
  private val _formatDateForJson = new java.text.SimpleDateFormat("yyyy-MM-dd")
  //Base url for every categories of crosswords
  private val _root = "http://s3.mirror.co.uk/mirror/crosswords/mir_"
  //Path from crosswords/app to the locations where the data will be extracted
  private val _folder = "../data/mirror/"


  // --- REGEXP ---
  //Very useful website for testing out regExp : http://www.regexr.com

  //Begining of the segment of the html page where we will match our regexps that interests us
  //(really close to everything in the case of Mirror
  private val _begin = """<div class="puzzle-wrapper right-clue center">"""
  private val _end = """SharePuzzle.init"""

  //Small regexps to retrieve various informations for the json
  private val _titlePuzId = """var puzzleId = "([0-9]+)";""".r
  private val _category = """/mir_([^1]+)_201""".r
  private val _date = """_(20[0-9]+).html""".r

  //Regex to retrieve every case of the grid and its corresponding solution
  private val _solution =
    """<div id='cell-([0-9]+)-([0-9]+)' class='cell ' data-clueid='[^\']*' data-reverted='0'>\s*(|<span class='cell-number'>[0-9]+<\/span>)\s*<div class='cell-input' data-solution='(.)'><\/div>""".r

  /* Regex to retrieve the starting point of every words, their direction and the clue
  /  Sometimes (especially in the category "quizword") there is double or even triple clues
  /  where one clue is used for different words.
  */
  val _cluesSingle = """<li id='clue-[0-9]+' data-direction='(across|down)' data-x1='([0-9]+)' data-x2='([0-9]+)' data-y1='([0-9]+)' data-y2='([0-9]+)'\s*data-islink='0' data-linkid='clue-' data-cluenum='[0-9]+'> <b>[^<]+</b> ([^<]+)""".r
  val _cluesDouble = """<li id='clue-[0-9]+' data-direction='(across|down)-(across|down)' data-x1='([0-9]+)-([0-9]+)'\s*data-x2='([0-9]+)-([0-9]+)' data-y1='([0-9]+)-([0-9]+)' data-y2='([0-9]+)-([0-9]+)'\s*data-islink='0' data-linkid='clue-' data-cluenum='[0-9]+\/[0-9]+'> <b>[0-9]+\/[0-9]+<\/b>([^(]*)\(""".r
  val _cluesTriple = """data-direction='(across|down)-(across|down)-(across|down)' data-x1='([0-9]+)-([0-9]+)-([0-9]+)' data-x2='([0-9]+)-([0-9]+)-([0-9]+)' data-y1='([0-9]+)-([0-9]+)-([0-9]+)' data-y2='([0-9]+)-([0-9]+)-([0-9]+)'\s*data-islink='0' data-linkid='clue-' data-cluenum='4\/20\/25'> <b>4\/20\/25<\/b> ([^\(]*)""".r

  // Regexps used for checking the validity of the parsing,
  // these two are for computing the validity of the solutions map.
  val _crosswordSize = """Crossword.setSize\(([0-9]+), ([0-9]+)\);""".r
  val _nbBlank = """class='cell blank'""".r


  /**
   * Parse crosswords from HTML text.
   * @param source source to parse
   * @param url the url from where the html comes from
   * @return a JSON representation of the crossword
   */
  def parse(source: Source, url: String): JsObject = {

    // Prepare interesting data
    val content = Text.bound(source.getLines().mkString, _begin, _end)
    var json = Json.obj(
      "source" -> "Mirror",
      "language" -> "eng"
    )

    //Adding URL to the json
    json += "url" -> Json.toJson(url)

    //Find and add category
    val category = _category.findFirstMatchIn(url).map(_.group(1))
    if (category.get == "2s_straight") json += "categories" -> Json.toJson("straight")
    else if (category.get == "2s_cryptic") json += "categories" -> Json.toJson("cryptic")
    else json += "categories" -> Json.toJson(Seq(category))

    //Find, format and add date
    val date = _date.findFirstMatchIn(url).map(_.group(1))
    json += "date" -> Json.toJson(_formatDateForJson.format(_formatForBrowsing.parse(date.get)))

    //Adding title
    val titlePuzId = _titlePuzId.findFirstMatchIn(content).map(_.group(1))
    if (titlePuzId.isDefined) {
      json += "title" -> Json.toJson(category.get + " " + titlePuzId.get)
    }

    //Adding source
    json += "source" -> Json.toJson("Wired Puzzles - Crossword")

    //solutions is A Map which gives letter for a given position (x,y).
    val solutions = _solution.findAllMatchIn(content).map(m => (
      Vec(m.group(1).toInt, m.group(2).toInt),
      m.group(4).charAt(0)
      )).toMap

    /**
     * Building the solution thanks to the solutions map described above.
     *
     * @param position The current position
     * @param dir The direction of the word (giving out the next position)
     * @param length The remaining length of the word
     * @param acc The current state of the result
     * @return The complete word
     */
    def buildSolution(position: Vec, dir: Direction, length: Int, acc: String): String = {
      if (length == 0) return acc
      buildSolution(position + Vec(dir), dir, length - 1, acc + solutions(position))
    }

    /** Building the words from single clue (the most common)
      * In order to produce vector with :
      * -The location where the word start
      * -The direction
      * -The word itself (solution)
      * -The clue
      *
      * There is an if-statement to determine what to give as "length" argument at the buildSolution function
      */
    val wordsFromSingleClue = _cluesSingle.findAllMatchIn(content).map(m =>
      if (m.group(1) == "across") {
        (Vec(m.group(2).toInt, m.group(4).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(2).toInt, m.group(4).toInt), Direction(m.group(1)), (m.group(3).toInt - m.group(2).toInt) + 1, ""),
          m.group(6).trim())
      }
      else {
        (Vec(m.group(2).toInt, m.group(4).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(2).toInt, m.group(4).toInt), Direction(m.group(1)), (m.group(5).toInt - m.group(4).toInt) + 1, ""),
          m.group(6).trim())
      }).toVector

    // Building the words from double clue
    // 1 : Only the first word concerned of each double clue
    val wordsFromDoubleClue1 = _cluesDouble.findAllMatchIn(content).map(m =>
      if (m.group(1) == "across") {
        (Vec(m.group(3).toInt, m.group(7).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(3).toInt, m.group(7).toInt), Direction(m.group(1)), (m.group(5).toInt - m.group(3).toInt) + 1, ""),
          m.group(11).trim())
      }
      else {
        (Vec(m.group(3).toInt, m.group(7).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(3).toInt, m.group(7).toInt), Direction(m.group(1)), (m.group(9).toInt - m.group(7).toInt) + 1, ""),
          m.group(11).trim())
      }
    ).toVector

    // 2 : Only the second word concerned of each double clue
    val wordsFromDoubleClue2 = _cluesDouble.findAllMatchIn(content).map(m =>
      if (m.group(2) == "across") {
        (Vec(m.group(4).toInt, m.group(8).toInt), Direction(m.group(2)),
          buildSolution(Vec(m.group(4).toInt, m.group(8).toInt), Direction(m.group(2)), (m.group(6).toInt - m.group(4).toInt) + 1, ""),
          m.group(11).trim())
      }
      else {
        (Vec(m.group(4).toInt, m.group(8).toInt), Direction(m.group(2)),
          buildSolution(Vec(m.group(4).toInt, m.group(8).toInt), Direction(m.group(2)),
            (m.group(10).toInt - m.group(8).toInt) + 1, ""), m.group(11).trim())
      }
    ).toVector

    // Building the words from triple clue
    // 1 : Only the first word concerned of each triple clue
    val wordsFromTripleClue1 = _cluesTriple.findAllMatchIn(content).map(m =>
      if (m.group(1) == "across") {
        (Vec(m.group(4).toInt, m.group(10).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(4).toInt, m.group(10).toInt), Direction(m.group(1)), (m.group(7).toInt - m.group(4).toInt) + 1, ""),
          m.group(16).trim())
      }
      else {
        (Vec(m.group(4).toInt, m.group(10).toInt), Direction(m.group(1)),
          buildSolution(Vec(m.group(4).toInt, m.group(10).toInt), Direction(m.group(1)), (m.group(13).toInt - m.group(10).toInt) + 1, ""),
          m.group(16).trim())
      }
    ).toVector

    // 2 : Only the second word concerned of each triple clue
    val wordsFromTripleClue2 = _cluesTriple.findAllMatchIn(content).map(m =>
      if (m.group(2) == "across") {
        (Vec(m.group(5).toInt, m.group(11).toInt), Direction(m.group(2)),
          buildSolution(Vec(m.group(5).toInt, m.group(11).toInt), Direction(m.group(2)), (m.group(8).toInt - m.group(5).toInt) + 1, ""),
          m.group(16).trim())
      }
      else {
        (Vec(m.group(5).toInt, m.group(11).toInt), Direction(m.group(2)),
          buildSolution(Vec(m.group(5).toInt, m.group(11).toInt), Direction(m.group(2)), (m.group(14).toInt - m.group(11).toInt) + 1, ""),
          m.group(16).trim())
      }
    ).toVector

    // 3 : Only the third word concerned of each triple clue
    val wordsFromTripleClue3 = _cluesTriple.findAllMatchIn(content).map(m =>
      if (m.group(3) == "across") {
        (Vec(m.group(6).toInt, m.group(12).toInt), Direction(m.group(3)),
          buildSolution(Vec(m.group(6).toInt, m.group(12).toInt), Direction(m.group(3)), (m.group(9).toInt - m.group(6).toInt) + 1, ""),
          m.group(16).trim())
      }
      else {
        (Vec(m.group(6).toInt, m.group(12).toInt), Direction(m.group(3)),
          buildSolution(Vec(m.group(6).toInt, m.group(12).toInt), Direction(m.group(3)),
            (m.group(15).toInt - m.group(12).toInt) + 1, ""), m.group(16).trim())
      }
    ).toVector

    // Joining everything together to obtain the final Vector "words" already described above.
    val words = {
    wordsFromSingleClue ++
      wordsFromDoubleClue1 ++ wordsFromDoubleClue2 ++
      wordsFromTripleClue1 ++ wordsFromTripleClue2 ++ wordsFromTripleClue3
    }

    // Putting the words into the json
    val wordsForJson = words.map { case (Vec(x,y),dir,solution,clue) => Json.obj(
      "word" -> solution,
      "clue" -> clue,
      "x" -> x,
      "y" -> y,
      "dir" -> dir.toString
    )}
    if (wordsForJson.isEmpty)
      throw new NoSuchElementException("empty crossword")
    json += "words" -> Json.toJson(wordsForJson)

    //Check 1 : Solutions
    //Check if the map "solutions" has the appropriate number of elements
    val numberOfCases1 = _crosswordSize.findFirstMatchIn(content).map(_.group(1).toInt)
    val numberOfCases2 = _crosswordSize.findFirstMatchIn(content).map(_.group(2).toInt)
    val blankCases = _nbBlank.findAllMatchIn(content).size

    val nbExpected = solutions.size
    val nbFound = numberOfCases1.get * numberOfCases2.get - blankCases

    if(nbFound != nbExpected)
      throw new Exception("Not enough solutions " + nbFound + " found instead of " + nbExpected)

    //Check 2 : Nb words
    //Check if the number of elements in words are the same as the number of clues.
    //Not working well for quizword (double or triple clues used with a non-homogenoues style)
    /*
    val numberOfWordsExpected = (("<b>".r).findAllMatchIn(content)).size
    val numberOfWordsFound = words.size

    if(numberOfWordsFound != numberOfWordsExpected)
      throw new Exception("Not enough words " + numberOfWordsFound + " found instead of " + numberOfWordsExpected)
    */

    // returning the final json
    json
  }

  /**
   * 1)Verify if the data are gathered already
   * 2)Creating files for the html file and the json file
   * 3)Downloading the html file from the website
   * 4)Writing down the html file and json file (call parse to obtain the json to write)
   *
   * @param category The category of the crossword (mandatory to build the url)
   * @param date The date of the crossword (mandatory to build the url)
   */
  private def ensure(category: String, date: String) {

    // Do nothing if there already is the JSON file
    val jsonFile = new File(_folder + category + "/" + date + ".json")
    if (jsonFile.exists())
      return

    // Check if the HTML file exists
    val htmlFile = new File(_folder + category + "/" + date + ".html")
    if (!htmlFile.exists()) {

      // Guarantee that folder is here
      if (!htmlFile.getParentFile.isDirectory && !htmlFile.getParentFile.mkdirs())
        throw new IOException("failed to create directory for Mirror")

      // Download HTML file
      println("downloading " + category + "/" + date + "...")
      Thread.sleep(1000) // let the server breath a bit ;)
      val html = try Source.fromURL(_root + category + date + ".html").mkString catch { case e: Exception =>
        System.err.println("failed to get " + category + "/" + date + " (" + e.getMessage + ")")
          ""
      }

      // Save content
      val out = new FileWriter(htmlFile)
      out.write(html)
      out.close()
    }


    // Convert to JSON
    val json = try Json.prettyPrint(parse(Source.fromFile(htmlFile), _root + category + date + ".html")) catch { case e: Exception =>
      System.err.println("failed to parse " + category + "/" + date + " (" + e.getMessage + ")")
      ""
    }

    // Save content
    val out = new FileWriter(jsonFile)
    out.write(json)
    out.close()

    println(category + "/" + date + " successfully exported!")
  }

  /**
   * Call "ensure" for every category and date inside the given range.
   *
   * @param category The category of the crossword (Mandatory for future url)
   * @param last The last date to retrieve
   * @param first The first date to retrieve
   */
  private def query(category: String, last: Date, first: Date) {
    _calendar.setTime(first)
    var currentDate = first

    while (currentDate.after(last)) {
      ensure(category, _formatForBrowsing.format(currentDate))
      _calendar.add(Calendar.DATE, -1)
      currentDate = _calendar.getTime()
    }

  }

  /**
   * Calling query for each category
   */
  def main(args: Array[String]) {

    val today =  new Date()
    val last = _formatForBrowsing.parse("20140402") // 1st of April

    query("straight_", last, today)
    query("2s_straight_", last, today)
    query("2s_cryptic_", last, today)
    query("quizword_", last, today)
  }

}
